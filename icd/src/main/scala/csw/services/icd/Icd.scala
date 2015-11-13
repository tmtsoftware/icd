package csw.services.icd

import java.io.{ File, FileOutputStream }

import com.typesafe.config.{ ConfigResolveOptions, ConfigFactory }

/**
 * An ICD API validator application
 */
object Icd extends App {
  import csw.services.icd.IcdValidator._

  /**
   * Command line options: [--validate <dir> --in <inputFile> --out <outputFile>]
   * (Some options may be abbreviated to a single letter: -i, -s, -o)
   */
  case class Options(validateDir: Option[File] = None,
                     inputFile: Option[File] = None, schemaFile: Option[File] = None, outputFile: Option[File] = None)

  private val parser = new scopt.OptionParser[Options]("icd") {
    head("icd", System.getProperty("CSW_VERSION"))

    opt[File]("validate") valueName "<dir>" action { (x, c) ⇒ // Note: -v is already taken by the shell script!
      c.copy(validateDir = Some(x))
    } text "Validates set of files in dir (default: current dir): subsystem-model.conf, component-model.conf, command-model.conf, publish-model.conf, subscribe-model.conf"

    opt[File]('i', "in") valueName "<inputFile>" action { (x, c) ⇒
      c.copy(inputFile = Some(x))
    } text "Single input file to be verified, assumed to be in HOCON (*.conf) or JSON (*.json) format"

    opt[File]('s', "schema") valueName "<jsonSchemaFile>" action { (x, c) ⇒
      c.copy(schemaFile = Some(x))
    } text s"""JSON schema file to use to validate the input file, assumed to be in HOCON (*.conf) or JSON (*.json) format
         |    (Default uses schema based on standard input file name: One of:
         |    ${StdName.stdSet.mkString(", ")}""".stripMargin
    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Saves the ICD (or single input or schema file) to the given file in a format based on the file's suffix (md, html, pdf, json)"
  }

  parser.parse(args, Options()) match {
    case Some(options) ⇒
      try {
        run(options)
      } catch {
        case e: Throwable ⇒
          println(e)
          System.exit(1)
      }
    case None ⇒ System.exit(1)
  }

  private def run(options: Options): Unit = {
    // Save single input or schema file as JSON to output file, if specified
    for (outputFile ← options.outputFile) {
      if (outputFile.getName.endsWith(".json")) {
        options.inputFile match {
          case Some(inputFile) ⇒ saveAsJson(inputFile, outputFile)
          case None ⇒ options.schemaFile foreach {
            schemaFile ⇒ saveAsJson(schemaFile, outputFile)
          }
        }
      }
    }

    // Validate single input file, if given
    for (inputFile ← options.inputFile) {
      val problems = if (options.schemaFile.isDefined) {
        validate(inputFile, options.schemaFile.get)
      } else {
        val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
        validate(inputConfig, inputFile.getName)
      }
      printProblems(problems)
    }

    // Validate the standard set of ICD files in the given (or current) dir
    if (options.inputFile.isEmpty && options.schemaFile.isEmpty) {
      val dir = options.validateDir.getOrElse(new File("."))
      val problems = validateRecursive(dir)
      printProblems(problems)
      for (outputFile ← options.outputFile) {
        if (errorCount(problems) == 0 && !outputFile.getName.endsWith(".json")) {
          IcdPrinter.saveToFile(dir, outputFile)
        }
      }
    }
  }

  private def errorCount(problems: List[Problem]): Int = {
    problems.count(p ⇒ p.severity == "error" || p.severity == "fatal")
  }

  private def printProblems(problems: List[Problem]): Unit = {
    for (problem ← problems) {
      println(s"${problem.severity}: ${problem.message}")
    }
  }

  private def saveAsJson(inputFile: File, outputFile: File): Unit = {
    val s = toJson(inputFile)
    val f = new FileOutputStream(outputFile)
    f.write(s.getBytes)
    f.close()
  }

}

