package csw.services.icd

import java.io._
import java.net.URI

import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration
import com.github.fge.jsonschema.core.load.download.URIDownloader
import com.github.fge.jsonschema.core.report.{ ProcessingMessage, ProcessingReport }
import com.github.fge.jsonschema.main.{ JsonSchema, JsonSchemaFactory }
import com.typesafe.config.{ Config, ConfigFactory, ConfigRenderOptions, ConfigResolveOptions }

import scala.io.Source

/**
 * An ICD validator
 */
object IcdValidator {

  /**
   * Returns a string with the contents of the given file, converted to JSON, if it was not already.
   * JSON files are recognized by the file suffix .json.
   * @param file a file in HOCON or JSON format
   * @return the file contents in JSON format
   */
  def toJson(file: File): String = {
    if (!file.exists()) throw new FileNotFoundException(file.getName)
    if (file.getName.endsWith(".json")) {
      Source.fromFile(file).mkString
    } else {
      val config = ConfigFactory.parseFile(file).resolve(ConfigResolveOptions.noSystem())
      toJson(config)
    }
  }

  val jsonOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)

  /**
   * Returns a string with the contents of the given config, converted to JSON.
   * @param config the config to convert
   * @return the config contents in JSON format
   */
  def toJson(config: Config): String = {
    config.root.render(jsonOptions)
  }

  // Adds a custom URI scheme, so that config:/... loads the config file as a resource
  // and converts it to JSON. In this way you can use "$ref": "config:/myfile.conf"
  // to refer to external JSON schemas in HOCON format.
  private case object ConfigDownloader extends URIDownloader {
    override def fetch(uri: URI): InputStream = {
      val config = ConfigFactory.parseResources(uri.getPath.substring(1))
      if (config == null) throw new IOException(s"Resource not found: ${uri.getPath}")
      new ByteArrayInputStream(toJson(config).getBytes)
    }
  }

  private val cfg = LoadingConfiguration.newBuilder.addScheme("config", ConfigDownloader).freeze
  private val factory = JsonSchemaFactory.newBuilder.setLoadingConfiguration(cfg).freeze

  /**
   * Validates all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories.
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   */
  def validateRecursive(dir: File = new File(".")): List[Problem] = {
    (dir :: subDirs(dir)).flatMap(validate)
  }

  /**
   * Validates all files with the standard names (stdNames) in the given directory.
   * @param dir the directory containing the standard set of ICD files (default: current dir)
   */
  def validate(dir: File = new File(".")): List[Problem] = {
    import csw.services.icd.StdName._
    if (!dir.isDirectory) {
      List(Problem("error", s"$dir does not exist"))
    } else {
      val result = for (stdName ← stdNames) yield {
        val inputFile = new File(dir, stdName.name)
        if (!inputFile.exists()) {
          // List(Problem("info", s"${stdName.name} is missing in $dir"))
          Nil
        } else {
          val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
          val schemaConfig = ConfigFactory.parseResources(stdName.schema)
          if (schemaConfig == null) {
            List(Problem("error", s"Missing schema resource: ${stdName.schema}"))
          } else {
            validate(inputConfig, schemaConfig, inputFile.toString)
          }
        }
      }
      result.flatten
    }
  }

  /**
   * Validates the given input file using the given JSON schema file
   * JSON files are recognized by the file suffix .json.
   * @param inputFile a file in HOCON or JSON format
   * @param schemaFile a JSON schema file in HOCON or JSON format
   * @return a list of problems, if any were found
   */
  def validate(inputFile: File, schemaFile: File): List[Problem] = {
    val jsonSchema = JsonLoader.fromString(toJson(schemaFile))
    val schema = factory.getJsonSchema(jsonSchema)
    val jsonInput = JsonLoader.fromString(toJson(inputFile))
    validate(schema, jsonInput, inputFile.getName)
  }

  /**
   * Validates the given input config using the given schema config.
   *
   * @param inputConfig the config to be validated against the schema
   * @param fileName the name of the original file that inputConfig was made from (for error messages)
   * @return a list of problems, if any were found
   */
  def validate(inputConfig: Config, fileName: String): List[Problem] = {
    val name = new File(fileName).getName
    StdName.stdNames.find(_.name == name) match {
      case Some(stdName) ⇒
        val schemaConfig = ConfigFactory.parseResources(stdName.schema)
        if (schemaConfig == null) {
          List(Problem("error", s"Missing schema resource: ${stdName.schema}"))
        } else {
          validate(inputConfig, schemaConfig, fileName)
        }
      case None ⇒
        List(Problem("error", s"Invalid ICD file name: $fileName"))
    }
  }

  /**
   * Validates the given input config using the given schema config.
   * @param inputConfig the config to be validated against the schema
   * @param schemaConfig a config using the JSON schema syntax (but may be simplified to HOCON format)
   * @param inputFileName the name of the original input file (for error messages)
   * @return a list of problems, if any were found
   */
  def validate(inputConfig: Config, schemaConfig: Config, inputFileName: String): List[Problem] = {
    val jsonSchema = JsonLoader.fromString(toJson(schemaConfig))
    val schema = factory.getJsonSchema(jsonSchema)
    val jsonInput = JsonLoader.fromString(toJson(inputConfig))
    validate(schema, jsonInput, inputFileName)
  }

  // Runs the validation and handles any internal exceptions
  // 'source' is the name of the input file for use in error messages.
  private def validate(schema: JsonSchema, jsonInput: JsonNode, source: String): List[Problem] = {
    try {
      validateResult(schema.validate(jsonInput, true), source)
    } catch {
      case e: Exception ⇒
        e.printStackTrace()
        List(Problem("fatal", e.toString))
    }
  }

  // Packages the validation results for return to caller.
  // 'source' is the name of the input file for use in error messages.
  private def validateResult(report: ProcessingReport, source: String): List[Problem] = {
    import scala.collection.JavaConverters._
    val result = for (msg ← report.asScala)
      yield Problem(msg.getLogLevel.toString, formatMsg(msg, source))
    result.toList
  }

  // Formats the error message for display to user.
  // 'source' is the name of the original input file.
  private def formatMsg(msg: ProcessingMessage, source: String): String = {
    import scala.collection.JavaConversions._
    val file = new File(source).getPath

    // val jsonStr = toJson(ConfigFactory.parseString(msg.asJson().toString))
    // s"$file: $jsonStr"

    // try to get a nicely formatted error message that includes the necessary info
    val json = msg.asJson()
    val pointer = json.get("instance").get("pointer").asText()
    val loc = if (pointer.isEmpty) s"$file" else s"$file, at path: $pointer"
    val schemaUri = json.get("schema").get("loadingURI").asText()
    val schemaPointer = json.get("schema").get("pointer").asText()
    val schemaStr = if (schemaUri == "#") "" else s" (schema: $schemaUri:$schemaPointer)"

    // try to get additional messages from the reports section
    val reports = json.get("reports")
    val messages = if (reports == null) ""
    else {
      val reportElems = for (r ← reports.elements().toList) yield r
      val msgElems = (for (r ← reports) yield r.elements().toList).flatten
      val msgTexts = for (e ← msgElems) yield e.get("message").asText()
      "\n" + msgTexts.mkString("\n")
    }

    s"$loc: ${msg.getLogLevel.toString}: ${msg.getMessage}$schemaStr$messages"
  }

}
