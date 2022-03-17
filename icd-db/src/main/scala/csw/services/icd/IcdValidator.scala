package csw.services.icd

import java.io._
import java.net.URI
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigResolveOptions}
import csw.services.icd.db.StdConfig.FileResources
import csw.services.icd.db.StdConfig
import csw.services.icd.db.parser.ServiceModelParser
import io.swagger.v3.parser.OpenAPIV3Parser
import org.everit.json.schema.loader.SchemaClient

import scala.io.Source
import scala.util.{Failure, Success, Try}
import org.everit.json.schema.{Schema, ValidationException}
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject

import scala.jdk.CollectionConverters._

/**
 * An ICD API validator
 */
//noinspection DuplicatedCode
object IcdValidator {

  val schemaVersionKey     = "modelVersion"
  val currentSchemaVersion = "3.0"

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
      val json   = source.mkString
      source.close()
      json
    }
    else {
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
      val uri    = URI.create(url)
      val config = ConfigFactory.parseResources(uri.getPath.substring(1))
      if (config == null)
        throw new IOException(s"Resource not found: ${uri.getPath}")
      new ByteArrayInputStream(toJson(config).getBytes)
    }
  }

  private def checkSchemaVersion(v: String, fileName: String): Either[Problem, String] = {
    v match {
      // Fix for wrong modelVersion (0.1) in some existing published model files.
      // This means we tolerate 0.1 and 1.1, converting automatically to 1.0, for backward compatibility.
      case "1.0" | "1.1" | "0.1" => Right("1.0")
      case "2.0"                 => Right("2.0")
      case "3.0"                 => Right("3.0")
      case _                     => Left(Problem("error", s"Invalid modelVersion in $fileName: Expected 1.0 or 2.0"))
    }
  }

  /**
   * Validates all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories.
   *
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   */
  def validateDirRecursive(dir: File = new File(".")): List[Problem] = {
    if (dir.isDirectory) {
      (dir :: subDirs(dir)).flatMap(d => validateOneDir(d))
    }
    else List(Problem("error", s"Directory $dir does not exist"))
  }

  /**
   * Validates all files with the standard names (stdNames) in the given directory.
   *
   * @param dir the directory containing the standard set of ICD files (default: current dir)
   */
  def validateOneDir(dir: File = new File("."), deprecatedWarning: Boolean = true): List[Problem] = {
    import StdName._
    if (!dir.isDirectory) {
      List(Problem("error", s"$dir does not exist or is not a directory"))
    }
    else {
      // Note: first file read contains the schema version (subsystem or component model)
      var schemaVersion = currentSchemaVersion
      val result = for (stdName <- stdNames) yield {
        val inputFile = new File(dir, stdName.name)
        if (!inputFile.exists()) {
          Nil
        }
        else {
          Try(ConfigFactory.parseFile(inputFile)) match {
            case Success(parsedConfigFile) =>
              val inputConfig =
                StdConfig.addTargetSubsystem(parsedConfigFile.resolve(ConfigResolveOptions.noSystem()), stdName)
              if (inputConfig.hasPath(schemaVersionKey))
                schemaVersion = inputConfig.getString(schemaVersionKey)
              val resources = new FileResources(dir.getPath)
              val sc        = StdConfig(stdName, inputConfig, inputFile.getPath, resources)
              validateStdName(sc, schemaVersion)
            case Failure(ex) =>
              ex.printStackTrace()
              List(Problem("error", s"Fatal config parsing error in $inputFile: $ex"))
          }
        }
      }
      result.flatten
    }
  }

  /**
   * If the config is from a service-model.conf file, validate the OpenApi files referenced.
   * @param sc config, possibly from service-model.conf
   * @param dirName directory containing the model files
   * @return list of problems found
   */
  private def validateOpenApis(sc: StdConfig, dirName: String): List[Problem] = {
    // validate a single OpenApi file
    def validateOpenApi(fileName: String): List[Problem] = {
      val maybeContents = sc.resources.getResource(fileName)
      if (maybeContents.isEmpty) {
        List(Problem("error", s"Can't locate ${fileName}"))
      }
      else {
        val parseResult = new OpenAPIV3Parser().readContents(maybeContents.get, null, null)
        parseResult.getMessages.asScala.toList
          .map(msg => Problem("error", s"OpenApi file $fileName: $msg"))
      }
    }

    if (sc.stdName.isServiceModel) {
      val serviceModel = ServiceModelParser(sc.config)
      val dir = new File(sc.fileName).getParent
      serviceModel.provides
        .flatMap(p => validateOpenApi(new File(dir, p.openApi).getPath))
    }
    else Nil
  }

  /**
   * Validates the given input config using the standard schema.
   *
   * @param schemaVersion value of the component or subsystem model's modelVersion field: Should be 1.0 or 2.0
   * @return a list of problems, if any were found
   */
  def validateStdName(sc: StdConfig, schemaVersion: String): List[Problem] = {
    checkSchemaVersion(schemaVersion, sc.fileName) match {
      case Right(version) =>
        val schemaPath   = s"$version/${sc.stdName.schema}"
        val schemaConfig = ConfigFactory.parseResources(schemaPath)
        val problems     = validateConfig(sc.config, schemaConfig, sc.fileName)
        if (problems.nonEmpty) {
          problems
        }
        else {
          validateOpenApis(sc, new File(sc.fileName).getParent)
        }
      case Left(problem) =>
        List(problem)
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
  def validateConfig(inputConfig: Config, schemaConfig: Config, inputFileName: String): List[Problem] = {
    val jsonSchema = new JSONObject(toJson(schemaConfig))
    val schemaLoader = SchemaLoader
      .builder()
      .schemaClient(HoconSchemaClient)
      .schemaJson(jsonSchema)
      .resolutionScope("classpath:/")
      .build()
    val schema    = schemaLoader.load().build().asInstanceOf[Schema]
    val jsonInput = new JSONObject(toJson(inputConfig))
    validateJson(schema, jsonInput, inputFileName)
  }

  // Runs the validation and handles any internal exceptions
  // 'source' is the name of the input file for use in error messages.
  private def validateJson(schema: Schema, jsonInput: JSONObject, source: String): List[Problem] = {
    try {
      schema.validate(jsonInput)
      Nil
    }
    catch {
      case e: ValidationException =>
        e.getAllMessages.asScala.toList.map(msg => Problem("error", s"$source: $msg"))
      case e: Exception =>
        e.printStackTrace()
        List(Problem("fatal", e.toString))
    }
  }
}
