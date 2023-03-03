package csw.services.icd.db

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd._
import csw.services.icd.codegen.{JavaCodeGenerator, PythonCodeGenerator, ScalaCodeGenerator, TypescriptCodeGenerator}
import csw.services.icd.db.parser.{BaseModelParser, IcdModelParser, ServiceModelParser, SubsystemModelParser}
import csw.services.icd.db.ComponentDataReporter._
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import csw.services.icd.fits.IcdFits
import diffson.playJson.DiffsonProtocol
import icd.web.shared.IcdModels.{EventModel, ImageModel, ReceiveCommandModel}
import icd.web.shared.{BuildInfo, PdfOptions, SubsystemWithVersion}
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.util.DeserializationUtils
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.FailoverStrategy.FactorFun
import reactivemongo.api.{AsyncDriver, DB, FailoverStrategy, MongoConnection, MongoConnectionOptions}

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

// Static defs
object IcdDbDefaults {
  val conf: Config          = ConfigFactory.load
  val defaultPort: Int      = conf.getInt("icd.db.port")
  val defaultHost: String   = conf.getString("icd.db.host")
  val defaultDbName: String = conf.getString("icd.db.name")

  // Suffix used for temp collections while ingesting model files into the DB
  val tmpCollSuffix = ".tmp"

  def connectToDatabase(host: String, port: Int, dbName: String): DB = {
    // Taken from https://stackoverflow.com/questions/54706942/mongoerror-no-primary-node-is-available
    // as workaround for error after upgrading to reactivemongo-1.1.0-RC6: "No primary node is available"
    val mdbConnectionFS = FailoverStrategy.apply(
      Duration(15000, TimeUnit.MILLISECONDS),
      5,
      FactorFun(1.0)
    )
    val connOptions: MongoConnectionOptions = MongoConnectionOptions.default.copy(
      keepAlive = true,
      failoverStrategy = mdbConnectionFS
//      nbChannelsPerNode = 20
    )

    val mongoUri = s"mongodb://$host:$port/$dbName"
    val driver   = AsyncDriver()
    val database = for {
      uri <- MongoConnection.fromString(mongoUri)
      con <- driver.connect(Seq("localhost"), connOptions, dbName)
      dn  <- Future(uri.db.get)
      db  <- con.database(dn)
    } yield db
    database.await
  }

  // Deletes the given mongo db, if it exists
  def deleteDatabase(host: String, port: Int, dbName: String): Unit = {
    try {
      connectToDatabase(host, port, dbName).drop().await
    }
    catch {
      case e: Exception => e.printStackTrace()
    }
  }

}

// This is the command line app icd-db
//noinspection DuplicatedCode
object IcdDb extends App {
  import IcdDbDefaults._

  // Cache of PDF files for published API and ICD versions
  val maybeCache: Option[PdfCache] =
    if (IcdDbDefaults.conf.getBoolean("icd.pdf.cache.enabled"))
      Some(new PdfCache(new File(IcdDbDefaults.conf.getString("icd.pdf.cache.dir"))))
    else None

  // Parser for the command line options
  private val parser = new scopt.OptionParser[IcdDbOptions]("icd-db") {
    head("icd-db", BuildInfo.version)

    opt[String]("db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = x)
    } text s"The name of the database to use (default: $defaultDbName)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text s"The host name where the database is running (default: $defaultHost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text s"The port number to use for the database (default: $defaultPort)"

    opt[File]('i', "ingest") valueName "<dir>" action { (x, c) =>
      c.copy(ingest = Some(x))
    } text "Top level directory containing files to ingest into the database"

    opt[String]('l', "list") valueName "[subsystems|assemblies|hcds|all]" action { (x, c) =>
      c.copy(list = Some(x))
    } text "Prints a list of ICD subsystems, assemblies, HCDs or all components"

    opt[String]("listData") valueName "<subsystem>" action { (x, c) =>
      c.copy(listData = Some(x))
    } text "Prints a list of event sizes and yearly accumulation of archived data for components of the specified subsystem."

    opt[Unit]('u', "allUnits") action { (_, c) =>
      c.copy(allUnits = Some(()))
    } text "Prints the set of unique units used in all received commands and published events for all components in DB."

    opt[String]('c', "component") valueName "<name>" action { (x, c) =>
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options (subsystem must also be specified)"

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by any following options"

    opt[String]('t', "subsystem2") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(target = Some(x))
    } text "Specifies the second subsystem (and optional version) in an ICD to be used by any following options"

    opt[String]("component2") valueName "<name>" action { (x, c) =>
      c.copy(targetComponent = Some(x))
    } text "Specifies the subsytem2 component to be used by any following options (subsystem2 must also be specified)"

    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) =>
      c.copy(icdVersion = Some(x))
    } text "Specifies the version to be used by any following options (overrides subsystem and subsystem2 versions)"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text "Saves the selected API (or ICD) to the given file in a format based on the file's suffix (html, pdf) or generates code for the given API in a language based on the suffix ('scala', 'java', 'ts' (typescript), py (python))"

    // Note: Dropping the db while the web app is running causes issues.
    opt[String]("drop") valueName "[db|subsystem|component]" action { (x, c) =>
      c.copy(drop = Some(x))
    } text "Drops the specified component, subsystem, or the entire icd database (requires restart of icd web app)"

    opt[String]("versions") valueName "<subsystem>" action { (x, c) =>
      c.copy(versions = Some(x))
    } text "List the version history of the given subsystem"

    opt[String]("diff") valueName "<subsystem>:<version1>[,version2]" action { (x, c) =>
      c.copy(diff = Some(x))
    } text "For the given subsystem, list the differences between <version1> and <version2> (or the current version)"

    opt[File]('m', "missing") valueName "<outputFile>" action { (x, c) =>
      c.copy(missing = Some(x))
    } text "Generates a 'Missing Items' report to the given file (dir for csv) in a format based on the file's suffix (html, pdf, otherwise text/csv formatted files are generated in given dir)"

    opt[File]('a', "archived") valueName "<outputFile>" action { (x, c) =>
      c.copy(archived = Some(x))
    } text "Generates an 'Archived Items' report for all subsystems (or the given one) to the given file in a format based on the file's suffix (html, pdf, csv)"

    opt[Unit]("allSubsystems") action { (_, c) =>
      c.copy(allSubsystems = Some(()))
    } text "Include all subsystems in searches for publishers, subscribers, etc. while generating API doc (Default: only consider the one subsystem)"

    opt[Unit]("clientApi") action { (_, c) =>
      c.copy(clientApi = Some(()))
    } text "Include subscribed events and sent commands in the API dic (Default: only include published events and received commands)"

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

    opt[String]("documentNumber") valueName "text" action { (x, c) =>
      c.copy(documentNumber = Some(x))
    } text "For PDF output: An optional document number to display after the title/subtitle"

    opt[String]("package") valueName "package.name" action { (x, c) =>
      c.copy(packageName = Some(x))
    } text "Package name for generated Scala files (default: no package)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, IcdDbOptions()) match {
    case Some(options) =>
      try {
        run(options)
      }
      catch {
        case _: IcdDbException =>
          println("Error: Failed to connect to mongodb. Make sure mongod server is running.")
          System.exit(1)
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Run the application
  private def run(options: IcdDbOptions): Unit = {
    val db = IcdDb(options.dbName, options.host, options.port)

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
      Nil,
      documentNumber = options.documentNumber.getOrElse("")
    )

    options.ingest.map { dir =>
      db.ingestAndCleanup(dir)
    } match {
      case Some(problems) if problems.nonEmpty =>
        problems.foreach(println(_))
        System.exit(1)
      case _ =>
    }

    options.list.foreach(list)
    options.outputFile.foreach(output)
    options.drop.foreach(drop)
    options.versions.foreach(listVersions)
    options.diff.foreach(diffVersions)
    options.missing.foreach(missingItemsReport)
    options.archived.foreach(file => archivedItemsReport(file, Some(pdfOptions)))
    options.listData.foreach(s => listData(db, s))
    options.allUnits.foreach(_ => printAllUsedUnits(db))

    db.close()
    System.exit(0)

    // --list option
    def list(componentType: String): Unit = {
      val opt = componentType.toLowerCase
      val list =
        if (opt.startsWith("s"))
          db.query.getSubsystemNames
        else if (opt.startsWith("as"))
          db.query.getAssemblyNames(options.subsystem)
        else if (opt.startsWith("h"))
          db.query.getHcdNames(options.subsystem)
        else db.query.getComponentNames(options.subsystem)
      for (name <- list) println(name)
    }

    // --output option
    def output(file: File): Unit = {
      if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
      val fname = file.getName.toLowerCase()
      if (fname.endsWith(".html") || fname.endsWith(".pdf")) {
        val clientApi           = options.target.isDefined || options.clientApi.isDefined
        val searchAllSubsystems = clientApi && options.allSubsystems.isDefined && options.target.isEmpty
        IcdDbPrinter(db, searchAllSubsystems, clientApi, maybeCache, Some(pdfOptions)).saveToFile(
          options.subsystem.get,
          options.component,
          options.target,
          options.targetComponent,
          options.icdVersion,
          pdfOptions,
          file
        )
      }
      else generate(file)
    }

    // --drop option
    def drop(opt: String): Unit = {
      opt match {
        case "db" =>
          println(s"Dropping ${options.dbName}")
          db.dropDatabase()
        case "subsystem" =>
          if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
          db.query.dropSubsystem(options.subsystem.get)
        case "component" =>
          // Note: Dropping a component could cause problems, needs testing
          if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
          options.component match {
            case Some(component) =>
              println(s"Dropping $component from ${options.dbName}")
              db.query.dropComponent(options.subsystem.get, component)
            case None =>
              error("Missing required component name: Please specify --component <name>")
          }
        case x =>
          error(s"Invalid drop argument $x. Expected 'db' or 'component' (together with --component option)")
      }
    }

    // --versions option
    def listVersions(subsystem: String): Unit = {
      for (v <- db.versionManager.getVersions(subsystem)) {
        println(s"${v.maybeVersion.getOrElse("*")}\t${v.date.withZone(DateTimeZone.getDefault)}\t${v.comment}")
      }
    }

    // Check that the version is in the correct format
    def checkVersion(maybeVersion: Option[String]): Unit = {
      maybeVersion match {
        case Some("master") =>
        case Some(version) =>
          val versionRegex = """\d+\.\d+""".r
          version match {
            case versionRegex(_*) =>
            case _                => error(s"Bad version format: $version, expected something like 1.0, 2.1")
          }
        case None =>
      }
    }

    // --diff option: Compare versions option. Argument format: <subsystem>:v1[,v2]
    def diffVersions(arg: String): Unit = {
      import DiffsonProtocol._

      val msg = "Expected argument format: <subsystemName>:v1[,v2]"
      if (!arg.contains(":") || arg.endsWith(":") || arg.endsWith(",")) error(msg)
      val Array(name, vStr) = arg.split(":")
      if (vStr.isEmpty) error(msg)
      val Array(v1, v2) =
        if (vStr.contains(",")) {
          vStr.split(",").map(Option(_))
        }
        else {
          Array(Option(vStr), Option.empty)
        }
      checkVersion(v1)
      checkVersion(v2)
      for (diff <- db.versionManager.diff(name, v1, v2)) {
        val jsValue = Json.toJson(diff.patch)
        val s       = Json.prettyPrint(jsValue)
        println(s"\n${diff.path}:\n$s") // XXX TODO FIXME: work on the format?
      }
    }

    // --missing option
    def missingItemsReport(file: File): Unit = {
      MissingItemsReport(db, options, pdfOptions).saveToFile(file, pdfOptions)
    }

    // --archive option
    def archivedItemsReport(file: File, maybePdfOptions: Option[PdfOptions]): Unit = {
      val maybeSv = options.subsystem
        .map(SubsystemAndVersion(_))
        .map(s => SubsystemWithVersion(s.subsystem, s.maybeVersion, options.component))
      ArchivedItemsReport(db, maybeSv, maybePdfOptions).saveToFile(file, pdfOptions)
    }

    // --generate code in the given file
    def generate(file: File): Unit = {
      file.getName.split("\\.").last match {
        case "scala" =>
          new ScalaCodeGenerator(db).generate(
            options.subsystem.get,
            options.component,
            file,
            None,
            options.packageName
          )
        case "java" =>
          new JavaCodeGenerator(db).generate(
            options.subsystem.get,
            options.component,
            file,
            None,
            options.packageName
          )
        case "ts" =>
          new TypescriptCodeGenerator(db).generate(
            options.subsystem.get,
            options.component,
            file,
            None,
            options.packageName
          )
        case "py" =>
          new PythonCodeGenerator(db).generate(
            options.subsystem.get,
            options.component,
            file,
            None,
            options.packageName
          )
        case x =>
          error(s"Unsupported file suffix: $x")
      }
    }
  }
}

/**
 * Thrown when the connection to the icd database fails
 */
class IcdDbException extends Exception

/**
 * ICD Database (Mongodb) support
 */
case class IcdDb(
    dbName: String = IcdDbDefaults.defaultDbName,
    host: String = IcdDbDefaults.defaultHost,
    port: Int = IcdDbDefaults.defaultPort
) {
  // Cleanup databases from earlier releases
  IcdDbDefaults.deleteDatabase(host, port, "icds")
  IcdDbDefaults.deleteDatabase(host, port, "icds2")
  IcdDbDefaults.deleteDatabase(host, port, "icds3")

  val db: DB    = IcdDbDefaults.connectToDatabase(host, port, dbName)
  val admin: DB = IcdDbDefaults.connectToDatabase(host, port, "admin")

  // Clean up on exit
  sys.addShutdownHook(close())

  val query: IcdDbQuery                 = IcdDbQuery(db, admin, None)
  val versionManager: IcdVersionManager = IcdVersionManager(query)
  val manager: IcdDbManager             = IcdDbManager(db, versionManager)

  // Returns list of duplicates in given list
  private def getDuplicates(list: List[String]) = list.groupBy(identity).collect { case (x, List(_, _, _*)) => x }.toList

  // Check for duplicate component names
  private def checkForDuplicateComponentNames(list: List[StdConfig]): List[String] = {
    val components = list.flatMap {
      case x if x.stdName.isComponentModel =>
        val subsystem = x.config.getString("subsystem")
        val component = x.config.getString("component")
        Some(s"$subsystem.$component")
      case _ => None
    }
    getDuplicates(components)
  }

  // Check for misspelled subsystem or component names
  private def checkForWrongComponentNames(list: List[StdConfig]): List[String] = {
    // get pairs of (dir -> subsystem.component) and check if there are directories that contain
    // multiple different values (should all be the same)
    val pairs = list.flatMap {
      case x if x.stdName.hasComponent =>
        val dir       = new File(x.fileName).getParent
        val subsystem = x.config.getString("subsystem")
        val component = x.config.getString("component")
        Some(dir -> s"$subsystem.$component")
      case _ => None
    }
    val map = pairs.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
    map.values.toList.map(_.distinct).filter(_.size != 1).map(_.mkString(" != "))
  }

  // Check for obvious errors, such as duplicate event or parameter names after ingesting model files for subsystem
  private def checkPostIngest(subsystem: String): List[Problem] = {
    def getEventProblems(prefix: String, events: List[EventModel], name: String): List[Problem] = {
      val p1 = getDuplicates(events.map(_.name)).map(s => Problem("error", s"Duplicate $name name: '$s''"))
      val p2 = events.flatMap(event =>
        getDuplicates(event.parameterList.map(_.name))
          .map(s => Problem("error", s"Duplicate parameter name '$s' in $name '$prefix.${event.name}''"))
      )
      p1 ::: p2
    }

    def getImageProblems(prefix: String, images: List[ImageModel]): List[Problem] = {
      val p1 = getDuplicates(images.map(_.name)).map(s => Problem("error", s"Duplicate image name: '$s''"))
      val p2 = images.flatMap(image =>
        getDuplicates(image.metadataList.map(_.name))
          .map(s => Problem("error", s"Duplicate metadata name '$s' in image: '$prefix.${image.name}''"))
      )
      p1 ::: p2
    }

    def getCommandProblems(prefix: String, commands: List[ReceiveCommandModel]): List[Problem] = {
      val p1 = getDuplicates(commands.map(_.name)).map(s => Problem("error", s"Duplicate command name: '$s''"))
      val p2 = commands.flatMap(command =>
        getDuplicates(command.parameters.map(_.name))
          .map(s => Problem("error", s"Duplicate parameter name '$s' in command '$prefix.${command.name}''"))
      )
      p1 ::: p2
    }

    val sv = SubsystemWithVersion(subsystem, None, None)
    versionManager
      .getResolvedModels(sv, None, Map.empty)
      .flatMap { icdModels =>
        val publishProblems = icdModels.publishModel.toList.flatMap { publishModel =>
          val prefix = s"${publishModel.subsystem}.${publishModel.component}"
          getEventProblems(prefix, publishModel.eventList, "event") :::
          getEventProblems(prefix, publishModel.currentStateList, "current state") :::
          getImageProblems(prefix, publishModel.imageList)
        }
        val commandProblems = icdModels.commandModel.toList.flatMap { commandModel =>
        val prefix = s"${commandModel.subsystem}.${commandModel.component}"
          getCommandProblems(prefix, commandModel.receive)
        }
        publishProblems ::: commandProblems
      }

  }

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   * If a subsystem-model.conf was ingested, the previous data for that subsystem is removed first
   * (to make sure renamed or removed components actually are gone).
   *
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   * @return a pair of two lists: 1: A list of the configs in the directories, 2: A list describing any problems that occurred
   */
  def ingestAndCleanup(dir: File = new File(".")): List[Problem] = {
    val (configs, ingestProblems) = ingest(dir)
    val subsystemList             = configs.filter(_.stdName == StdName.subsystemFileNames).map(_.config.getString("subsystem"))

    // Check for duplicate subsystem-model.conf file (found one in TCS)
    val duplicateSubsystems = subsystemList.diff(subsystemList.toSet.toList)
    val duplicateComponents = checkForDuplicateComponentNames(configs)
    val wrongComponents     = checkForWrongComponentNames(configs)
    val duplicateSubsystemProblems =
      if (duplicateSubsystems.nonEmpty)
        List(Problem("error", s"Duplicate subsystem-model.conf found: ${duplicateSubsystems.mkString(", ")}"))
      else Nil

    val duplicateComponentProblems =
      if (duplicateComponents.nonEmpty)
        List(Problem("error", s"Duplicate component names found: ${duplicateComponents.mkString(", ")}"))
      else Nil

    val wrongComponentProblems =
      if (wrongComponents.nonEmpty)
        List(Problem("error", s"Conflicting component names found: ${wrongComponents.mkString(", ")}"))
      else Nil

    val allProblems = ingestProblems ::: duplicateSubsystemProblems ::: duplicateComponentProblems ::: wrongComponentProblems

    if (subsystemList.isEmpty)
      query.afterIngestFiles(allProblems, dbName)
    else subsystemList.foreach(query.afterIngestSubsystem(_, allProblems, dbName))

    val postIngestProblems = subsystemList.flatMap(checkPostIngest)

    allProblems ::: postIngestProblems
  }

  /**
   * If DMS-Model-Files/FITS-Dictionary exists, ingest it into the icd database
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   */
  private def ingestFitsDictionary(dir: File): List[Problem] = {
    // Uploads from web app have an extra temp directory at root
    val dmsDictDir1 = new File(s"$dir/FITS-Dictionary")
    val dmsDictDir2 = new File(s"$dir/DMS-Model-Files/FITS-Dictionary")
    val dmsDictDir  = if (dmsDictDir1.isDirectory) dmsDictDir1 else dmsDictDir2
    if (dmsDictDir.isDirectory) {
      val icdFits         = new IcdFits(this)
      val fitsKeywordFile = new File(dmsDictDir, "FITS-Dictionary.json")
      val fitsProblems = if (fitsKeywordFile.exists()) {
        icdFits.ingest(fitsKeywordFile)
      }
      else Nil
      val fitsTagFile = new File(dmsDictDir, "FITS-Tags.conf")
      val tagProblems = if (fitsTagFile.exists()) {
        icdFits.ingestTags(fitsTagFile)
      } else Nil
      fitsProblems ::: tagProblems
    }
    else Nil
  }

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   *
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   * @return a pair of two lists: 1: A list of the configs in the directories, 2: A list describing any problems that occurred
   */
  def ingest(dir: File = new File(".")): (List[StdConfig], List[Problem]) = {
    val validateProblems = IcdValidator.validateDirRecursive(dir)
    val result1 =
      if (validateProblems.nonEmpty)
        (Nil, validateProblems)
      else {
        val listOfPairs = (dir :: subDirs(dir)).map(ingestOneDir)
        (listOfPairs.flatMap(_._1), listOfPairs.flatMap(_._2))
      }

    if (result1._2.nonEmpty)
      result1
    else (result1._1, ingestFitsDictionary(dir))
  }

  /**
   * Ingests all files with the standard names (stdNames) in the given directory (only) into the database.
   *
   * @param dir the directory containing the standard set of ICD files
   * @return a pair of two lists: 1: A list of the configs in the directory, 2: A list describing any problems that occurred
   */
  private[db] def ingestOneDir(dir: File): (List[StdConfig], List[Problem]) = {
    val (stdConfigs, problems) = StdConfig.get(dir)
    if (problems.nonEmpty) (stdConfigs, problems)
    else (stdConfigs, ingestConfigs(stdConfigs))
  }

  /**
   * Ingests the given ICD model config objects (based on the contents of one subsystem directory)
   *
   * @param list list of ICD model files packaged as StdConfig objects
   * @return a list describing the problems, if any
   */
  private def ingestConfigs(list: List[StdConfig]): List[Problem] = {
    list.flatMap(stdConfig => ingestConfig(stdConfig))
  }

  /**
   * Ingests the given ICD model objects into the db (based on the contents of one subsystem API directory).
   * The collections created have the suffix IcdDbDefaults.tmpCollSuffix and must be renamed, if there are no errors.
   *
   * @param stdConfig ICD model file packaged as a StdConfig object
   * @return a list describing the problems, if any
   */
  def ingestConfig(stdConfig: StdConfig): List[Problem] = {
    // Ingest a single OpenApi file
    def ingestOpenApiFile(collectionName: String, fileName: String): List[Problem] = {
      import scala.jdk.CollectionConverters._
      val parseOptions = new ParseOptions()
      parseOptions.setResolve(true)
      parseOptions.setResolveFully(true)
      val tmpName     = s"$collectionName${IcdDbDefaults.tmpCollSuffix}"
      val parseResult = new OpenAPIV3Parser().readLocation(fileName, null, parseOptions)
      val problems    = parseResult.getMessages.asScala.toList.map(Problem("error", _))
      if (problems.nonEmpty) problems
      else {
        val openAPI = parseResult.getOpenAPI
        // Convert YAML to JSON if needed
        val jsonStr =
          if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            val yaml = io.swagger.util.Yaml.pretty().writeValueAsString(openAPI)
            DeserializationUtils.deserializeIntoTree(yaml, fileName).toPrettyString
          }
          else {
            io.swagger.util.Json.pretty().writeValueAsString(openAPI)
          }
        // Ingest into db
        val jsObj = Json.parse(jsonStr).as[JsObject]
        manager.ingest(collectionName, tmpName, jsObj)
        Nil
      }
    }

    // If this is for a service-model.conf file, also ingest the OpenApi files into the database
    def ingestOpenApiFiles(collectionName: String): List[Problem] = {
      if (stdConfig.stdName.isServiceModel) {
        val serviceModel = ServiceModelParser(stdConfig.config)
        val dirName      = new File(stdConfig.fileName).getParent
        serviceModel.provides
          .flatMap(p => ingestOpenApiFile(s"$collectionName.${p.name}", s"$dirName/${p.openApi}"))
      }
      else Nil
    }

    try {
      val coll    = getCollectionName(stdConfig)
      val tmpName = s"$coll${IcdDbDefaults.tmpCollSuffix}"
      ingestConfig(coll, tmpName, stdConfig.config)
      ingestOpenApiFiles(coll)
    }
    catch {
      case t: Throwable => List(Problem("error", s"Internal error: $t"))
    }
  }

  /**
   * Ingests the given input config into the database.
   *
   * @param name   the name of the collection in which to store this part of the API
   * @param tmpName temp name of the collection to use during ingest
   * @param config the config to be ingested into the database
   */
  //noinspection SameParameterValue
  private def ingestConfig(name: String, tmpName: String, config: Config): Unit = {
    import play.api.libs.json.Reads._

    val jsObj = Json.parse(IcdValidator.toJson(config)).as[JsObject]

    manager.ingest(name, tmpName, jsObj)
  }

  /**
   * Returns the DB collection name to use for the given ICD config.
   *
   * @param stdConfig API model file packaged as StdConfig object
   * @return the collection name
   */
  private def getCollectionName(stdConfig: StdConfig): String = {
    val baseName = if (stdConfig.stdName.isSubsystemModel) {
      SubsystemModelParser(stdConfig.config).subsystem
    }
    else if (stdConfig.stdName.isIcdModel) {
      IcdModelParser(stdConfig.config).subsystem
    }
    else {
      val model = BaseModelParser(stdConfig.config)
      s"${model.subsystem}.${model.component}"
    }
    s"$baseName.${stdConfig.stdName.modelBaseName}"
  }

  /**
   * Imports the given ICD release information.
   *
   * @param icdVersions describes the ICD version
   */
  def importIcds(icdVersions: IcdVersions, feedback: String => Unit): Unit = {
    versionManager.removeIcdVersions(icdVersions.subsystems.head, icdVersions.subsystems(1))
    icdVersions.icds.foreach { icd =>
      feedback(s"Ingesting ICD ${icdVersions.subsystems.head}-${icdVersions.subsystems(1)}-${icd.icdVersion}")
      versionManager.addIcdVersion(
        icd.icdVersion,
        icdVersions.subsystems.head,
        icd.versions.head,
        icdVersions.subsystems(1),
        icd.versions(1),
        icd.user,
        icd.comment,
        DateTime.parse(icd.date)
      )
    }
  }

  /**
   * Closes all open connections.
   * NOTE: This connection can't be reused after closing.
   */
  def close(): Unit = {
    db.endSession().await
  }

  /**
   * Drops this database. Use with caution!
   */

  def dropDatabase(): Unit = {
    db.drop().await
  }

}
