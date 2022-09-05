package csw.services.icd.fits

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import csw.services.icd.IcdValidator
import csw.services.icd.db.{IcdDb, IcdDbDefaults}
import icd.web.shared.{BuildInfo, FitsKeyInfo, FitsKeyInfoList, FitsSource, PdfOptions}
import reactivemongo.api.bson.collection.BSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import csw.services.icd._
import reactivemongo.play.json.compat._
import bson2json._
import csw.services.icd.db.parser.FitsKeyInfoListBsonParser
import lax._
import json2bson._

import java.io.{File, FileOutputStream}
import play.api.libs.json._
import reactivemongo.api.bson.BSONDocument

object IcdFitsDefs {
  val collectionName = "fits.keys"

  type FitsKeyMap = Map[FitsSource, List[String]]
}

object IcdFits extends App {

  /**
   * Command line options ("icd-git --help" prints a usage message with descriptions of all the options)
   */
  private case class Options(
      dbName: String = IcdDbDefaults.defaultDbName,
      host: String = IcdDbDefaults.defaultHost,
      port: Int = IcdDbDefaults.defaultPort,
      subsystem: Option[String] = None,
      component: Option[String] = None,
      list: Boolean = false,
      outputFile: Option[File] = None,
      ingest: Option[File] = None,
      orientation: Option[String] = None,
      fontSize: Option[Int] = None,
      lineHeight: Option[String] = None,
      paperSize: Option[String] = None
  )

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-fits") {
    import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}
    head("icd-git", BuildInfo.version)

    opt[String]('d', "db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = x)
    } text s"The name of the database to use (for the --ingest option, default: $defaultDbName)"

    opt[String]("host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text s"The host name where the database is running (for the --ingest option, default: $defaultHost)"

    opt[Int]("port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text s"The port number to use for the database (for the --ingest option, default: $defaultPort)"

    opt[String]('c', "component") valueName "<name>" action { (x, c) =>
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options (subsystem must also be specified)"

    opt[String]('s', "subsystem") valueName "<subsystem>" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem to be used by any following options"

    opt[Unit]('l', "list") action { (_, c) =>
      c.copy(list = true)
    } text "Prints the list of known FITS keywords"

    opt[File]('i', "ingest") valueName "<file>" action { (x, c) =>
      c.copy(ingest = Some(x))
    } text "Ingest a JSON or HOCON formatted file containing FITS Keyword information into the icd database"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text "Generates a document containing a table of FITS keyword information in a format based on the file's suffix (html, pdf, json, csv, conf (HOCON))"

    opt[String]("orientation") valueName "[portrait|landscape]" action { (x, c) =>
      c.copy(orientation = Some(x))
    } text "For PDF output: The page orientation (default: landscape)"

    opt[Int]("fontSize") valueName "<size>" action { (x, c) =>
      c.copy(fontSize = Some(x))
    } text "For PDF or HTML file output: The base font size in px for body text (default: 10)"

    opt[String]("lineHeight") valueName "<height>" action { (x, c) =>
      c.copy(lineHeight = Some(x))
    } text "For PDF or HTML file output: The line height (default: 1.6)"

    opt[String]("paperSize") valueName "[Letter|Legal|A4|A3]" action { (x, c) =>
      c.copy(paperSize = Some(x))
    } text "For PDF output: The paper size (default: Letter)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      }
      catch {
        case e: IllegalArgumentException =>
          println(s"Error: ${e.getMessage}")
          System.exit(1)
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Run the application
  private def run(options: Options): Unit = {
    val db      = IcdDb(options.dbName, options.host, options.port)
    val icdFits = IcdFits(db)

    def error(msg: String): Unit = {
      println(msg)
      System.exit(1)
    }

    try {
      options.lineHeight.map(_.toFloat)
    }
    catch {
      case _: Exception =>
        error("Expected a floating point value for line height")
    }
    val pdfOptions = PdfOptions(
      options.orientation,
      options.fontSize,
      options.lineHeight,
      options.paperSize,
      Some(true),
      Nil
    )

    if (options.list) list()

    options.ingest.foreach(icdFits.ingest)

    if (options.outputFile.nonEmpty) {
      def filter(fitsKeyInfo: FitsKeyInfo): Boolean = {
        val subsystemMatches = options.subsystem.isEmpty ||
          fitsKeyInfo.source.map(_.subsystem).contains(options.subsystem.get)
        val componentMatches = options.component.isEmpty ||
          fitsKeyInfo.source.map(_.componentName).contains(options.component.get)
        subsystemMatches && componentMatches
      }
      icdFits.output(options.outputFile.get, filter, pdfOptions)
    }

    db.close()
    System.exit(0)

    def list(): Unit = {}

  }

}

//noinspection SpellCheckingInspection
case class IcdFits(db: IcdDb) {
  import IcdFitsDefs._
  private val collection = db.db.collection[BSONCollection](collectionName)

  def ingest(file: File): Unit = {
    // XXX TODO: validate first
    val config = ConfigFactory.parseFile(file)
    val jsObj  = Json.parse(IcdValidator.toJson(config)).as[JsObject]
    collection.drop().await
    collection.create().await
    collection.insert.one(jsObj).await
  }

  def getFitsKeyInfo(maybePdfOptions: Option[PdfOptions] = None): List[FitsKeyInfo] = {
    val maybeDoc         = collection.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
    val maybeFitsKeyList = maybeDoc.map(FitsKeyInfoListBsonParser(_, maybePdfOptions))
    maybeFitsKeyList.getOrElse(Nil)
  }

  def getRelatedFitsKeyInfo(
      maybeSubsystem: Option[String],
      maybeComponent: Option[String] = None,
      maybePdfOptions: Option[PdfOptions] = None
  ): List[FitsKeyInfo] = {
    val fitsKeyList = getFitsKeyInfo(maybePdfOptions)
    if (maybeSubsystem.isEmpty) fitsKeyList.sorted
    else {
      // XXX TODO: Do the query in MongoDB?
      val subsystem = maybeSubsystem.get
      fitsKeyList.filter(i =>
        i.source
          .exists(_.subsystem == subsystem) && (maybeComponent.isEmpty || i.source.exists(_.componentName == maybeComponent.get))
      ).sorted
    }
  }

  /**
   * Returns a map from FitsKey (which describes an event parameter) to a list of FITS keywords that
   * are based on the event parameter's value(s).
   */
  def getFitsKeyMap(fitKeyList: List[FitsKeyInfo]): FitsKeyMap = {
    val pairs = fitKeyList.flatMap { i =>
      i.source
        .map(s => FitsSource(s.subsystem, s.componentName, s.eventName, s.parameterName, s.index, s.rowIndex))
        .map(_ -> i.name)
    }
    pairs.toMap.groupMap(_._1)(_._2).view.mapValues(_.toList).toMap
  }

  /**
   * Returns a map from FitsKey (which describes an event parameter) to a list of FITS keywords that
   * are based on the event parameter's value(s).
   */
  def getFitsKeyMap(maybePdfOptions: Option[PdfOptions] = None): FitsKeyMap = {
    val fitKeyList = getFitsKeyInfo(maybePdfOptions)
    getFitsKeyMap(fitKeyList)
  }

  // --output option
  def output(
      file: File,
      filter: FitsKeyInfo => Boolean,
      pdfOptions: PdfOptions
  ): Unit = {
    import icd.web.shared.JsonSupport._
    val fitsKeyList = getFitsKeyInfo(Some(pdfOptions)).filter(filter)
    if (fitsKeyList.isEmpty) {
      println("No FITS keywords found")
    }
    else {
      val fitsKeyInfoList = FitsKeyInfoList(fitsKeyList)
      val fname           = file.getName.toLowerCase()
      if (fname.endsWith(".html") || fname.endsWith(".pdf")) {
        IcdFitsPrinter(fitsKeyList).saveToFile(pdfOptions, file)
      }
      else if (fname.endsWith(".json")) {
        val out  = new FileOutputStream(file)
        val json = Json.toJson(fitsKeyInfoList)
        out.write(Json.prettyPrint(json).getBytes)
        out.close()
      }
      else if (fname.endsWith(".conf")) {
        val out     = new FileOutputStream(file)
        val jsonStr = Json.prettyPrint(Json.toJson(fitsKeyInfoList))
        val config  = ConfigFactory.parseString(jsonStr)
        val opts    = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
        out.write(config.root().render(opts).getBytes)
        out.close()
      }
      else if (fname.endsWith(".csv")) {
        import com.github.tototoshi.csv._
        implicit object MyFormat extends DefaultCSVFormat {
          override val lineTerminator = "\n"
          override val delimiter      = '|'
        }
        def clean(s: String) = s.replace("<p>", "").replace("</p>", "").replace(MyFormat.delimiter, '/')
        val writer           = CSVWriter.open(file)
        writer.writeRow(List("Name", "Title", "Description", "Type", "DefaultValue", "Units", "Source", "Note"))
        fitsKeyList.foreach { k =>
          writer.writeRow(
            List(
              k.name,
              k.title,
              clean(k.description),
              k.typ,
              k.defaultValue.getOrElse(""),
              k.units.getOrElse(""),
              k.source.map(_.toShortString).mkString(", "),
              k.note.getOrElse("")
            )
          )
        }
        writer.close()
        println(s"Wrote $file")
      }
    }
  }

}
