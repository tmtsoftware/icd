package csw.services.icd

import java.io._
import java.net.URI

import com.typesafe.config.{
  Config,
  ConfigFactory,
  ConfigRenderOptions,
  ConfigResolveOptions
}
import org.everit.json.schema.loader.SchemaClient

import scala.io.Source
import scala.util.{Failure, Success, Try}
import org.everit.json.schema.{Schema, ValidationException}
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import scala.collection.JavaConverters._

/**
  * An ICD API validator
  */
object IcdValidator {

  val schemaVersionKey = "modelVersion"
  val currentSchemaVersion = "2.0"

  /**
    * Returns a string with the contents of the given file, converted to JSON, if it was not already.
    * JSON files are recognized by the file suffix .json.
    *
    * @param file a file in HOCON or JSON format
    * @return the file contents in JSON format
    */
  def toJson(file: File): String = {
    if (!file.exists()) throw new FileNotFoundException(file.getName)
    if (file.getName.endsWith(".json")) {
      val source = Source.fromFile(file)
      val json = source.mkString
      source.close()
      json
    } else {
      val config =
        ConfigFactory.parseFile(file).resolve(ConfigResolveOptions.noSystem())
      toJson(config)
    }
  }

  val jsonOptions: ConfigRenderOptions =
    ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)

  /**
    * Returns a string with the contents of the given config, converted to JSON.
    *
    * @param config the config to convert
    * @return the config contents in JSON format
    */
  def toJson(config: Config): String = {
    config.root.render(jsonOptions)
  }

  // Adds a custom URI scheme, so that config:/... loads the config file as a resource
  // and converts it to JSON. In this way you can use "$ref": "config:/myfile.conf"
  // to refer to external JSON schemas in HOCON format.
  object HoconSchemaClient extends SchemaClient {
    override def get(url: String): InputStream = {
      val uri = URI.create(url)
      val config = ConfigFactory.parseResources(uri.getPath.substring(1))
      if (config == null)
        throw new IOException(s"Resource not found: ${uri.getPath}")
      new ByteArrayInputStream(toJson(config).getBytes)
    }
  }

  /**
    * Validates all the files with the standard names (stdNames) in the given directory and recursively
    * in its subdirectories.
    *
    * @param dir the top level directory containing one or more of the the standard set of ICD files
    *            and any number of subdirectories containing ICD files
    */
  def validateRecursive(dir: File = new File(".")): List[Problem] = {
    (dir :: subDirs(dir)).flatMap(validate)
  }

  /**
    * Validates all files with the standard names (stdNames) in the given directory.
    *
    * @param dir the directory containing the standard set of ICD files (default: current dir)
    */
  def validate(dir: File = new File(".")): List[Problem] = {
    import csw.services.icd.StdName._
    if (!dir.isDirectory) {
      List(Problem("error", s"$dir does not exist or is not a directory"))
    } else {
      // Note: first file read contains the schema version (subsystem or component model)
      var schemaVersion = currentSchemaVersion
      val result = for (stdName <- stdNames) yield {
        val inputFile = new File(dir, stdName.name)
        if (!inputFile.exists()) {
          Nil
        } else {
          Try(ConfigFactory.parseFile(inputFile)) match {
            case Success(parsedConfigFile) =>
              val inputConfig =
                parsedConfigFile.resolve(ConfigResolveOptions.noSystem())
              if (inputConfig.hasPath(schemaVersionKey))
                schemaVersion = inputConfig.getString(schemaVersionKey)
              val schemaPath = s"$schemaVersion/${stdName.schema}"
              val schemaConfig = ConfigFactory.parseResources(schemaPath)
              if (schemaConfig == null) {
                List(Problem("error", s"Missing schema resource: $schemaPath"))
              } else {
                validate(inputConfig, schemaConfig, inputFile.toString)
              }
            case Failure(ex) =>
              ex.printStackTrace()
              List(
                Problem("error",
                        s"Fatal config parsing error in $inputFile: $ex"))
          }
        }
      }
      result.flatten
    }
  }

  /**
    * Validates the given input file using the given JSON schema file
    * JSON files are recognized by the file suffix .json.
    *
    * @param inputFile  a file in HOCON or JSON format
    * @param schemaFile a JSON schema file in HOCON or JSON format
    * @return a list of problems, if any were found
    */
  def validate(inputFile: File, schemaFile: File): List[Problem] = {
    val jsonSchema = new JSONObject(toJson(schemaFile))
    val schemaLoader = SchemaLoader
      .builder()
      .schemaClient(HoconSchemaClient)
      .schemaJson(jsonSchema)
      .resolutionScope("classpath:/")
      .build()
    val schema = schemaLoader.load().build()
    val jsonInput = new JSONObject(toJson(inputFile))
    validate(schema, jsonInput, inputFile.getName)
  }

  /**
    * Validates the given input config using the standard schema for it based on the file name.
    *
    * @param inputConfig   the config to be validated against the schema
    * @param fileName      the name of the original file that inputConfig was made from
    * @param schemaVersion the schema version (default: latest version)
    * @return a list of problems, if any were found
    */
  def validate(inputConfig: Config,
               fileName: String,
               schemaVersion: String): List[Problem] = {
    val name = new File(fileName).getName
    StdName.stdNames.find(_.name == name) match {
      case Some(stdName) =>
        val schemaPath = s"$currentSchemaVersion/${stdName.schema}"
        val schemaConfig = ConfigFactory.parseResources(schemaPath)
        if (schemaConfig == null) {
          List(Problem("error", s"Missing schema resource: $schemaPath"))
        } else {
          validate(inputConfig, schemaConfig, fileName)
        }
      case None =>
        List(Problem("error", s"Invalid ICD file name: $fileName"))
    }
  }

  /**
    * Validates the given input config using the standard schema.
    *
    * @param inputConfig the config to be validated against the schema
    * @param stdName     holds the file and schema name
    * @return a list of problems, if any were found
    */
  def validate(inputConfig: Config,
               stdName: StdName,
               schemaVersion: String): List[Problem] = {
    val schemaPath = s"$schemaVersion/${stdName.schema}"
    val schemaConfig = ConfigFactory.parseResources(schemaPath)
    if (schemaConfig == null) {
      List(Problem("error", s"Missing schema resource: $schemaPath"))
    } else {
      validate(inputConfig, schemaConfig, stdName.name)
    }
  }

  /**
    * Validates the given input config using the given schema config.
    *
    * @param inputConfig   the config to be validated against the schema
    * @param schemaConfig  a config using the JSON schema syntax (but may be simplified to HOCON format)
    * @param inputFileName the name of the original input file (for error messages)
    * @return a list of problems, if any were found
    */
  def validate(inputConfig: Config,
               schemaConfig: Config,
               inputFileName: String): List[Problem] = {
    val jsonSchema = new JSONObject(toJson(schemaConfig))
    val schemaLoader = SchemaLoader
      .builder()
      .schemaClient(HoconSchemaClient)
      .schemaJson(jsonSchema)
      .resolutionScope("classpath:/")
      .build()
    val schema = schemaLoader.load().build()
    val jsonInput = new JSONObject(toJson(inputConfig))
    validate(schema, jsonInput, inputFileName)
  }

  // Runs the validation and handles any internal exceptions
  // 'source' is the name of the input file for use in error messages.
  private def validate(schema: Schema,
                       jsonInput: JSONObject,
                       source: String): List[Problem] = {
    try {
      schema.validate(jsonInput)
      Nil
    } catch {
      case e: ValidationException =>
        e.getAllMessages.asScala.toList.map(msg => Problem("error", msg))
      case e: Exception =>
        e.printStackTrace()
        List(Problem("fatal", e.toString))
    }
  }
}
