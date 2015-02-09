package csw.services.icd

import java.io.{ File, FileOutputStream }

/**
 * An ICD validator application
 */
object Icd extends App {
  import csw.services.icd.IcdValidator._

  /**
   * Command line options: [--in <inputFile> --schema <jsonSchema> --out <jsonOutputFile>]
   * (Options may be abbreviated to -i, -s, -o)
   */
  case class Options(inputFile: Option[File] = None, schemaFile: Option[File] = None, outputFile: Option[File] = None)

  private val parser = new scopt.OptionParser[Options]("icd") {
    head("icd", System.getProperty("CSW_VERSION"))

    opt[File]('i', "in") valueName "<inputFile>" action { (x, c) ⇒
      c.copy(inputFile = Some(x))
    } text "Input file to be verified, assumed to be in HOCON (*.conf) or JSON (*.json) format"

    opt[File]('s', "schema") valueName "<jsonSchemaFile>" action { (x, c) ⇒
      c.copy(schemaFile = Some(x))
    } text "JSON schema file to use to validate the input, assumed to be in HOCON (*.conf) or JSON (*.json) format"

    opt[File]('o', "out") valueName "<jsonOutputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Save the input file (or the schema file, if no input file was given) in JSON format (for testing)"

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
    // Save input or schema file as JSON to output file
    options.outputFile map {
      outputFile ⇒
        val x = options.inputFile match {
          case Some(inputFile) ⇒ saveAsJson(inputFile, outputFile)
          case None ⇒ options.schemaFile map {
            schemaFile ⇒ saveAsJson(schemaFile, outputFile)
          }
        }
    }

    // Validate input file, if given
    for (inputFile ← options.inputFile; schemaFile ← options.schemaFile) {
      val problems = validate(inputFile, schemaFile)
      for (problem ← problems) {
        println(s"${problem.severity}: ${problem.message}")
      }
    }
  }

  private def saveAsJson(inputFile: File, outputFile: File): Unit = {
    val s = toJson(inputFile)
    val f = new FileOutputStream(outputFile)
    f.write(s.getBytes)
    f.close()
  }
}
