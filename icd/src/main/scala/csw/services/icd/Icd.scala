package csw.services.icd

import java.io.{File, FileOutputStream}

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory}

/**
 * An ICD API validator application
 */
//noinspection DuplicatedCode
object Icd extends App {

  import csw.services.icd.IcdValidator._

  /**
   * Command line options: [--validate <dir> --in <inputFile> --out <outputFile>]
   * (Some options may be abbreviated to a single letter: -i, -s, -o)
   */
  case class Options(
                      validateDir: Option[File] = None,
                      inputFile: Option[File] = None,
                      schemaFile: Option[File] = None,
                      outputFile: Option[File] = None
                    )

  private val parser = new scopt.OptionParser[Options]("icd") {
    head("icd", System.getProperty("ICD_VERSION"))

    opt[File]("validate") valueName "<dir>" action { (x, c) => // Note: -v is already taken by the shell script!
      c.copy(validateDir = Some(x))
    } text "Validates icd files in dir (recursively, default: current dir): subsystem-model.conf, component-model.conf, command-model.conf, publish-model.conf, subscribe-model.conf"

    opt[File]('i', "in") valueName "<inputFile>" action { (x, c) =>
      c.copy(inputFile = Some(x))
    } text "Single input file to be validated, assumed to be in HOCON (*.conf) format"

    opt[File]('s', "schema") valueName "<jsonSchemaFile>" action { (x, c) =>
      c.copy(schemaFile = Some(x))
    } text
      s"""JSON schema file to use to validate the input file, assumed to be in HOCON (*.conf) or JSON (*.json) format
         |        (Default uses schema based on input file name (${StdName.stdSet.mkString(", ")})""".stripMargin
    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text
      """Saves the API doc (or single input or schema file) to the given file in a format based on
        |        the file's suffix (html, pdf, json).
        |        Only single files can be saved to JSON, only the contents of the current directory as html, pdf""".stripMargin

    help("help")
    version("version")
  }

  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  private def run(options: Options): Unit = {
    options.inputFile.foreach(validateInputFile)
    options.outputFile.foreach(output)

    // Validate the standard set of ICD files in the given (or current) dir
    if (options.inputFile.isEmpty && options.schemaFile.isEmpty) {
      val dir = options.validateDir.getOrElse(new File("."))
      val problems = validateDir(dir)
      if (errorCount(problems) == 0) {
        for (outputFile <- options.outputFile if !outputFile.getName.endsWith(".json")) {
          IcdPrinter.saveToFile(dir, outputFile)
        }
      } else System.exit(1)
    }

    // --in option - Validate single input file
    def validateInputFile(inputFile: File): Unit = {
      val problems = if (options.schemaFile.isDefined) {
        validate(inputFile, options.schemaFile.get)
      } else {
        val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
        validate(inputConfig, inputFile.getName, currentSchemaVersion)
      }
      printProblems(problems)
      if (errorCount(problems) != 0) System.exit(1)
    }

    // --out option - Save single input or schema file as JSON to output file, if specified
    def output(outputFile: File): Unit = {
      if (outputFile.getName.endsWith(".json")) {
        options.inputFile match {
          // JSON output
          case Some(inputFile) => saveAsJson(inputFile, outputFile)
          case None => options.schemaFile foreach { schemaFile =>
            saveAsJson(schemaFile, outputFile)
          }
        }
      } else if (options.inputFile.isDefined)
        println("Only JSON output is supported for single input files")
    }

    // Validates the files with the standard names in the given dir
    def validateDir(dir: File): List[Problem] = {
      val problems = validateRecursive(dir)
      printProblems(problems)
    }

  }

  private def errorCount(problems: List[Problem]): Int = {
    problems.count(p => p.severity == "error" || p.severity == "fatal")
  }

  private def printProblems(problems: List[Problem]): List[Problem] = {
    for (problem <- problems) {
      println(s"${problem.severity}: ${problem.message}")
    }
    problems
  }

  private def saveAsJson(inputFile: File, outputFile: File): Unit = {
    val s = toJson(inputFile)
    val f = new FileOutputStream(outputFile)
    f.write(s.getBytes)
    f.close()
  }

}

