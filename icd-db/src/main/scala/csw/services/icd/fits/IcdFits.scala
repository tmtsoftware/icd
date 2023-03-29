package csw.services.icd.fits

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import csw.services.icd.db.{IcdDb, IcdDbDefaults}
import icd.web.shared.JsonSupport._
import icd.web.shared.{
  AvailableChannels,
  BuildInfo,
  FitsDictionary,
  FitsKeyInfo,
  FitsKeyInfoList,
  FitsSource,
  FitsTags,
  PdfOptions,
  SubsystemWithVersion
}
import reactivemongo.api.bson.collection.BSONCollection

import scala.concurrent.ExecutionContext.Implicits.global
import csw.services.icd.*
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import reactivemongo.play.json.compat.*
import csw.services.icd.db.parser.{
  AvailableChannelsListBsonParser,
  FitsKeyInfoListBsonParser,
  FitsTagsBsonParser
}
import lax.*
import json2bson.*

import java.io.{File, FileInputStream, FileOutputStream}
import play.api.libs.json.*
import reactivemongo.api.bson.BSONDocument

import scala.util.Try

object IcdFitsDefs {
  val fitsKeyCollectionName     = "fits.keys"
  val fitsTagCollectionName     = "fits.tags"
  val fitsChannelCollectionName = "fits.channels"

  // Map from FITS source parameter to list of FITS keywords whose values come from that parameter
  type FitsKeyMap = Map[FitsSource, List[String]]

  /**
   * Returns a map from FitsSource (which describes an event parameter) to a list of FITS keywords that
   * are based on the event parameter's value(s).
   */
  def getFitsKeyMap(fitKeyList: List[FitsKeyInfo]): FitsKeyMap = {
    val pairs = fitKeyList.flatMap { i =>
      i.channels
        .map(s =>
          FitsSource(
            s.source.subsystem,
            s.source.componentName,
            s.source.eventName,
            s.source.parameterName,
            s.source.index,
            s.source.rowIndex
          )
        )
        .map(_ -> i.name)
    }
    pairs.toMap.groupMap(_._1)(_._2).view.mapValues(_.toList).toMap
  }
}

object IcdFits extends App {

  /**
   * Command line options ("icd-fits --help" prints a usage message with descriptions of all the options)
   */
  private case class Options(
      dbName: String = IcdDbDefaults.defaultDbName,
      host: String = IcdDbDefaults.defaultHost,
      port: Int = IcdDbDefaults.defaultPort,
      subsystem: Option[String] = None,
      component: Option[String] = None,
      tag: Option[String] = None,
      list: Boolean = false,
      outputFile: Option[File] = None,
      ingest: Option[File] = None,
      ingestTags: Option[File] = None,
      ingestChannels: Option[File] = None,
      validate: Option[File] = None,
      generate: Option[File] = None,
      orientation: Option[String] = None,
      fontSize: Option[Int] = None,
      lineHeight: Option[String] = None,
      paperSize: Option[String] = None
  )

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-fits") {
    import csw.services.icd.db.IcdDbDefaults.{defaultDbName, defaultHost, defaultPort}
    head("icd-fits", BuildInfo.version)

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

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by any following options"

    opt[String]('t', "tag") valueName "<tag>" action { (x, c) =>
      c.copy(tag = Some(x))
    } text "Filters the list of FITS keywords to those with the given tag"

    opt[Unit]('l', "list") action { (_, c) =>
      c.copy(list = true)
    } text "Prints the list of known FITS keywords"

    opt[File]("validate") valueName "<file>" action { (x, c) =>
      c.copy(validate = Some(x))
    } text "Validates a JSON formatted file containing the FITS Keyword dictionary and prints out any errors"

    opt[File]('g', "generate") valueName "<file>" action { (x, c) =>
      c.copy(generate = Some(x))
    } text "Generates an updated FITS dictionary JSON file by merging the one currently in the icd database " +
      "with the FITS keyword information defined for event parameters in the publish model files. " +
      "If a subsystem (or subsystem and component) are specified, with optional version, the merging is limited " +
      "to that subsystem/component."

    opt[File]('i', "ingest") valueName "<file>" action { (x, c) =>
      c.copy(ingest = Some(x))
    } text "Ingest a JSON formatted file containing a FITS Keyword dictionary into the icd database"

    opt[File]("ingestTags") valueName "<file>" action { (x, c) =>
      c.copy(ingestTags = Some(x))
    } text "Ingest a JSON or HOCON formatted file defining tags for the FITS dictionary into the icd database"

    opt[File]("ingestChannels") valueName "<file>" action { (x, c) =>
      c.copy(ingestChannels = Some(x))
    } text "Ingest a JSON or HOCON formatted file defining the available FITS channels for each subsystem into the icd database"

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

    val maybeSv = options.subsystem
      .map(SubsystemAndVersion(_))
      .map(s => SubsystemWithVersion(s.subsystem, s.maybeVersion, options.component))

    if (options.list) icdFits.list(maybeSv.map(_.subsystem), maybeSv.flatMap(_.maybeComponent), options.tag, pdfOptions)

    options.validate.foreach(file => icdFits.validateFitsDictionary(file).foreach(println))
    options.generate.foreach(file => icdFits.generateFitsDictionary(file, maybeSv).foreach(println))
    options.ingestTags.foreach(file => icdFits.ingestTags(file).foreach(println))
    options.ingestChannels.foreach(file => icdFits.ingestChannels(file).foreach(println))
    options.ingest.foreach(file => icdFits.ingest(file).foreach(println))
    options.outputFile.foreach(file => icdFits.output(file, maybeSv.map(_.subsystem), maybeSv.flatMap(_.maybeComponent), options.tag, pdfOptions))
    db.close()
    System.exit(0)
  }

}

//noinspection SpellCheckingInspection
case class IcdFits(db: IcdDb) {
  import IcdFitsDefs._
  private val fitsKeyCollection     = db.db.collection[BSONCollection](fitsKeyCollectionName)
  private val fitsTagCollection     = db.db.collection[BSONCollection](fitsTagCollectionName)
  private val fitsChannelCollection = db.db.collection[BSONCollection](fitsChannelCollectionName)

  def ingestTags(file: File): List[Problem] = {
    // Check for duplicate keywords (duplicates must have different channels)
    // Return an error for each duplicate
    def checkDups(config: Config): List[Problem] = {
      import scala.jdk.CollectionConverters._
      val tags = config.root().entrySet().asScala.toList.map(_.getKey)
      val keywords = tags.flatMap { tag =>
        config.getStringList(s"$tag.keywords").asScala.toList
      }
      keywords
        .groupBy(identity)
        .collect {
          case (x, List(_, _, _*)) => x
        }
        .toList
        .sorted
        .map(k => Problem("error", s"Duplicate FITS keyword/channel combination: keyword: $k"))
    }

    val config   = ConfigFactory.parseFile(file)
    val problems = Try(checkDups(config)).getOrElse(List(Problem("error", s"Error checking <tag>.keywords in $file")))
    if (problems.nonEmpty) problems
    else {
      val jsObj = Json.parse(IcdValidator.toJson(config)).as[JsObject]
      fitsTagCollection.drop().await
      fitsTagCollection.create().await
      fitsTagCollection.insert.one(jsObj).await
      Nil
    }
  }

  def ingestChannels(file: File): List[Problem] = {
    val config   = ConfigFactory.parseFile(file)
    val jsObj    = Json.parse(IcdValidator.toJson(config)).as[JsObject]
    val problems = IcdValidator.validateFitsChannels(jsObj.toString())
    if (problems.isEmpty) {
      fitsChannelCollection.drop().await
      fitsChannelCollection.create().await
      fitsChannelCollection.insert.one(jsObj).await
    }
    problems
  }

  /**
   * Validate the FITS-Dictionary.json file and return a list of problems, if found
   */
  def validateFitsDictionary(file: File): List[Problem] = {
    val inputStream = new FileInputStream(file)
    val jsObj       = Json.parse(inputStream).asInstanceOf[JsObject]
    val problems    = IcdValidator.validateFitsDictionary(jsObj.toString())
    inputStream.close()
    problems
  }

  /**
   * Generates the FITS-Dictionary.json file by merging the dictionary in the icd database with the
   * keyword info from the events and return a list of problems, if found.
   * If a subsystem/component is specified, restrict the merging to that subsystem/component.
   */
  def generateFitsDictionary(file: File, maybeSv: Option[SubsystemWithVersion]): List[Problem] = {
    val fitsDictionary = getFitsDictionary(None)
    val fitsKeyList    = fitsDictionary.fitsKeys
    if (fitsKeyList.isEmpty) {
      List(Problem("error", "No FITS keywords found"))
    }
    else {
      val fname = file.getName.toLowerCase()
      if (!fname.endsWith(".json")) {
        List(Problem("error", "Output file should have the .json suffix (Usually FITS-Dictionary.json)"))
      }
      else  {
        val fitsGen = IcdFitsGenerate(db)
        val newFitsKeyList = fitsGen.generate(maybeSv, fitsKeyList)
        val fitsKeyInfoList = FitsKeyInfoList(newFitsKeyList)
        val out             = new FileOutputStream(file)
        val json            = Json.toJson(fitsKeyInfoList)
        out.write(Json.prettyPrint(json).getBytes)
        out.close()
      }
      Nil
    }
  }

  /**
   * Ingest the FITS-Dictionary.json file into the icd db
   */
  def ingest(file: File): List[Problem] = {
    val inputStream = new FileInputStream(file)
    val jsObj       = Json.parse(inputStream).asInstanceOf[JsObject]
    val problems = IcdValidator.validateFitsDictionary(jsObj.toString())
    inputStream.close()
    if (problems.isEmpty) {
      fitsKeyCollection.drop().await
      fitsKeyCollection.create().await
      fitsKeyCollection.insert.one(jsObj).await
    }
    problems
  }

  def getFitsTags: FitsTags = {
    val maybeFitsTagDoc = fitsTagCollection.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
    val maybeFitsTags   = maybeFitsTagDoc.map(FitsTagsBsonParser(_))
    maybeFitsTags.getOrElse(FitsTags(Map.empty))
  }

  // Contents of FITS-Channels.conf: List of available FITS keyword channel names for each subsystem referenced in
  // FITS-Dictionary.json.
  def getFitsChannels: List[AvailableChannels] = {
    val maybeFitsChannelsDoc = fitsChannelCollection.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
    val maybeFitsChannels    = maybeFitsChannelsDoc.map(AvailableChannelsListBsonParser(_))
    maybeFitsChannels.getOrElse(Nil)
  }

  // Map from subsystem name to FITS channel
  def getFitsChannelMap: Map[String, Set[String]] = {
    getFitsChannels.map(c => c.subsystem -> c.channels.toSet).toMap
  }

  def getFitsKeyInfo(maybePdfOptions: Option[PdfOptions] = None): List[FitsKeyInfo] = {
    val maybeFitsKeyDoc  = fitsKeyCollection.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
    val maybeFitsKeyList = maybeFitsKeyDoc.map(FitsKeyInfoListBsonParser(_, maybePdfOptions))
    maybeFitsKeyList.getOrElse(Nil)
  }

  def getFitsDictionary(
      maybeSubsystem: Option[String],
      maybeComponent: Option[String] = None,
      maybeTag: Option[String] = None,
      maybePdfOptions: Option[PdfOptions] = None
  ): FitsDictionary = {
    val fitsTags    = getFitsTags
    val fitsKeyList = getFitsKeyInfo(maybePdfOptions)
    // filter key list by subsystem/component
    val l1 = {
      if (maybeSubsystem.isEmpty) fitsKeyList
      else {
        val subsystem = maybeSubsystem.get
        fitsKeyList.filter(fitsKey =>
          fitsKey.channels.exists(_.source.subsystem == subsystem) && (maybeComponent.isEmpty ||
            fitsKey.channels.exists(_.source.componentName == maybeComponent.get))
        )
      }
    }
    val noTag = maybeTag.isEmpty || !fitsTags.tags.contains(maybeTag.get)

    // filter key list by tag
    val l2 =
      if (noTag) l1
      else {
        val tag = maybeTag.get
        l1.filter { fitsKey =>
          fitsKey.channels.map(_.name) match {
            case List("") =>
              fitsTags.tags(tag).exists(_.keyword == fitsKey.name)
            case channels =>
              channels.exists(c => fitsTags.tags(tag).exists(k => k.keyword == fitsKey.name && k.channel.contains(c)))
          }
        }
      }

    // remove any channels that do not match the selected tag's channel
    val l3 =
      if (noTag) l2
      else {
        val tag = maybeTag.get
        l2.map { fitsKey =>
          val channels = fitsKey.channels.filter(c =>
            c.name.isEmpty || fitsTags.tags(tag).exists(k => k.keyword == fitsKey.name && k.channel.contains(c.name))
          )
          fitsKey.copy(channels = channels)
        }
      }

    FitsDictionary(l3.sorted, fitsTags)
  }

  /**
   * Print the related FITS keywords to stdout
   */
  def list(
      maybeSubsystem: Option[String],
      maybeComponent: Option[String],
      maybeTag: Option[String],
      pdfOptions: PdfOptions
  ): Unit = {
    val fitsDictionary = getFitsDictionary(maybeSubsystem, maybeComponent, maybeTag, Some(pdfOptions))
    fitsDictionary.fitsKeys.foreach { k => println(k.name) }
  }

  /**
   * Returns a map from FitsKey (which describes an event parameter) to a list of FITS keywords that
   * are based on the event parameter's value(s).
   */
  def getFitsKeyMap(maybePdfOptions: Option[PdfOptions] = None): FitsKeyMap = {
    val fitKeyList = getFitsKeyInfo(maybePdfOptions)
    IcdFitsDefs.getFitsKeyMap(fitKeyList)
  }

  // --output option
  def output(
      file: File,
      maybeSubsystem: Option[String],
      maybeComponent: Option[String],
      maybeTag: Option[String],
      pdfOptions: PdfOptions
  ): Unit = {
    val fitsDictionary = getFitsDictionary(maybeSubsystem, maybeComponent, maybeTag, Some(pdfOptions))
    val fitsKeyList    = fitsDictionary.fitsKeys
    if (fitsKeyList.isEmpty) {
      println("No FITS keywords found")
    }
    else {
      val fname = file.getName.toLowerCase()
      if (fname.endsWith(".html") || fname.endsWith(".pdf")) {
        IcdFitsPrinter(fitsDictionary, maybeSubsystem, maybeComponent).saveToFile(maybeTag, pdfOptions, file)
      }
      else if (fname.endsWith(".json")) {
        val fitsKeyInfoList = FitsKeyInfoList(fitsKeyList)
        val out             = new FileOutputStream(file)
        val json            = Json.toJson(fitsKeyInfoList)
        out.write(Json.prettyPrint(json).getBytes)
        out.close()
      }
      else if (fname.endsWith(".conf")) {
        val fitsKeyInfoList = FitsKeyInfoList(fitsKeyList)
        val out             = new FileOutputStream(file)
        val jsonStr         = Json.prettyPrint(Json.toJson(fitsKeyInfoList))
        val config          = ConfigFactory.parseString(jsonStr)
        val opts            = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
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
        writer.writeRow(List("Name", "Description", "Type", "Units", "Source"))
        fitsKeyList.foreach { k =>
          writer.writeRow(
            List(
              k.name,
              clean(k.description),
              k.`type`,
              k.units.getOrElse(""),
              k.channels.map(_.source.toShortString).mkString(", ")
            )
          )
        }
        writer.close()
        println(s"Wrote $file")
      }
    }
  }

}
