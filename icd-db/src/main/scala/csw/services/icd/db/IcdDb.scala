package csw.services.icd.db

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd._
import csw.services.icd.db.parser.{BaseModelParser, SubsystemModelParser}
import csw.services.icd.db.ComponentDataReporter._
import diffson.playJson.DiffsonProtocol
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import reactivemongo.api.{DefaultDB, MongoConnection, MongoDriver}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

object IcdDbDefaults {
  private val conf          = ConfigFactory.load
  val defaultPort: Int      = conf.getInt("icd.db.port")
  val defaultHost: String   = conf.getString("icd.db.host")
  val defaultDbName: String = conf.getString("icd.db.name")
}

//noinspection DuplicatedCode
object IcdDb extends App {

  // Parser for the command line options
  private val parser = new scopt.OptionParser[IcdDbOptions]("icd-db") {
    head("icd-db", System.getProperty("ICD_VERSION"))

    opt[String]('d', "db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = x)
    } text "The name of the database to use (default: icds)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text "The host name where the database is running (default: localhost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The port number to use for the database (default: 27017)"

    opt[File]('i', "ingest") valueName "<dir>" action { (x, c) =>
      c.copy(ingest = Some(x))
    } text "Top level directory containing files to ingest into the database"

    opt[String]('l', "list") valueName "[subsystems|assemblies|hcds|all]" action { (x, c) =>
      c.copy(list = Some(x))
    } text "Prints a list of ICD subsystems, assemblies, HCDs or all components"

    opt[String]("listData") valueName "<subsystem>" action { (x, c) =>
      c.copy(listData = Some(x))
    } text "Prints a list of total data rates for components of specified subsystem."

    opt[Unit]('u', "allUnits") action { (_, c) =>
      c.copy(allUnits = Some(()))
    } text "Prints the set of unique units used in all received commands and published events for all components in DB."

    opt[String]('c', "component") valueName "<name>" action { (x, c) =>
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options (subsystem must also be specified)"

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by any following options"

    opt[String]('t', "target") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(target = Some(x))
    } text "Specifies the target subsystem (and optional version) to be used by any following options"

    opt[String]("target-component") valueName "<name>" action { (x, c) =>
      c.copy(targetComponent = Some(x))
    } text "Specifies the target subsytem component to be used by any following options (target must also be specified)"

    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) =>
      c.copy(icdVersion = Some(x))
    } text "Specifies the version to be used by any following options (overrides subsystem and target versions)"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) =>
      c.copy(outputFile = Some(x))
    } text "Saves the selected API or ICD to the given file in a format based on the file's suffix (html, pdf)"

    opt[String]("drop") valueName "[db|component]" action { (x, c) =>
      c.copy(drop = Some(x))
    } text "Drops the specified component or database (use with caution!)"

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
    } text "Generates an 'Archived Items' report to the given file in a format based on the file's suffix (html, pdf)"

    opt[Unit]( "allSubsystems") action { (_, c) =>
      c.copy(allSubsystems = Some(()))
    } text "Include all subsystems in searches for publishers, subscribers, etc. while generating API doc (Default: only consider the one subsystem)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, IcdDbOptions()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
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

    options.ingest.map(dir => db.ingest(dir)) match {
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
    options.archived.foreach(archivedItemsReport)
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
          db.query.getAssemblyNames
        else if (opt.startsWith("h"))
          db.query.getHcdNames
        else db.query.getComponentNames
      for (name <- list) println(name)
    }

    def error(msg: String): Unit = {
      println(msg)
      System.exit(1)
    }

    // --output option
    def output(file: File): Unit = {
      if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
      val searchAllSubsystems = options.allSubsystems.isDefined && options.target.isEmpty
      IcdDbPrinter(db, searchAllSubsystems).saveToFile(
        options.subsystem.get,
        options.component,
        options.target,
        options.targetComponent,
        options.icdVersion,
        file
      )
    }

    // --drop option
    def drop(opt: String): Unit = {
      opt match {
        case "db" =>
//          if (confirmDrop(s"Are you sure you want to drop the ${options.dbName} database?")) {
            println(s"Dropping ${options.dbName}")
            db.dropDatabase()
//          }
        case "component" =>
          if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
          options.component match {
            case Some(component) =>
//              if (confirmDrop(s"Are you sure you want to drop $component from ${options.dbName}?")) {
                println(s"Dropping $component from ${options.dbName}")
                db.query.dropComponent(options.subsystem.get, component)
//              }
            case None =>
              error("Missing required component name: Please specify --component <name>")
          }
        case x =>
          error(s"Invalid drop argument $x. Expected 'db' or 'component' (together with --component option)")
      }

//      def confirmDrop(msg: String): Boolean = {
//        print(s"$msg [y/n] ")
//        StdIn.readLine().toLowerCase == "y"
//      }
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
        } else {
          Array(Option(vStr), Option.empty)
        }
      checkVersion(v1)
      checkVersion(v2)
      for (diff <- db.versionManager.diff(name, v1, v2)) {
        val jsValue = Json.toJson(diff.patch)
        val s = Json.prettyPrint(jsValue)
        println(s"\n${diff.path}:\n$s") // XXX TODO FIXME: work on the format?
      }
    }

    // --missing option
    def missingItemsReport(file: File): Unit = {
      MissingItemsReport(db, options).saveToFile(file)
    }

    // --archive option
    def archivedItemsReport(file: File): Unit = {
      ArchivedItemsReport(db, options.subsystem).saveToFile(file)
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

  implicit val timeout: FiniteDuration = 5.seconds
  private val mongoUri                 = s"mongodb://$host:$port/$dbName"
  private val driver = new MongoDriver
  private val futureDb = for {
    uri <- Future.fromTry(MongoConnection.parseURI(mongoUri))
    dn  <- Future(uri.db.get)
    db  <- driver.connection(uri, None, strictUri = false).get.database(dn)
  } yield db
  val db: DefaultDB = Try(Await.result(futureDb, timeout)).getOrElse {
    throw new IcdDbException()
  }

  // Clean up on exit
  sys.addShutdownHook(close())

  val query: IcdDbQuery                 = IcdDbQuery(db, None)
  val versionManager: IcdVersionManager = IcdVersionManager(query)
  val manager: IcdDbManager             = IcdDbManager(db, versionManager)

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   *
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   */
  def ingest(dir: File = new File(".")): List[Problem] = {
    val validateProblems = IcdValidator.validateRecursive(dir)
    if (validateProblems.nonEmpty)
      validateProblems
    else
      (dir :: subDirs(dir)).flatMap(ingestOneDir)
  }

  /**
   * Ingests all files with the standard names (stdNames) in the given directory (only) into the database.
   *
   * @param dir the directory containing the standard set of ICD files
   * @return a list describing any problems that occured
   */
  private[db] def ingestOneDir(dir: File): List[Problem] = {
    val list = StdConfig.get(dir)
    ingestConfigs(list)
  }

  /**
   * Ingests the given ICD model config objects (based on the contents of one subsystem directory)
   *
   * @param list list of ICD model files packaged as StdConfig objects
   * @return a list describing the problems, if any
   */
  private def ingestConfigs(list: List[StdConfig]): List[Problem] = {
    list.flatMap(ingestConfig)
  }

  /**
   * Ingests the given ICD model objects (based on the contents of one subsystem API directory)
   *
   * @param stdConfig ICD model file packaged as a StdConfig object
   * @return a list describing the problems, if any
   */
  def ingestConfig(stdConfig: StdConfig): List[Problem] = {
    try {
      ingestConfig(getCollectionName(stdConfig), stdConfig.config)
      Nil
    } catch {
      case t: Throwable => List(Problem("error", s"Internal error: $t"))
    }
  }

  /**
   * Ingests the given input config into the database.
   *
   * @param name   the name of the collection in which to store this part of the API
   * @param config the config to be ingested into the datasbase
   */
  private def ingestConfig(name: String, config: Config): Unit = {
    import play.api.libs.json._
    import play.api.libs.json.Reads._

    val jsObj = Json.parse(IcdValidator.toJson(config)).as[JsObject]

    manager.ingest(name, jsObj)
  }

  /**
   * Returns the DefaultDB collection name to use for the given ICD config.
   *
   * @param stdConfig API model file packaged as StdConfig object
   * @return the collection name
   */
  private def getCollectionName(stdConfig: StdConfig): String = {
    val baseName = if (stdConfig.stdName.isSubsystemModel) {
      SubsystemModelParser(stdConfig.config).subsystem
    } else {
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
    Await.ready(db.endSession(), timeout)
  }

  /**
   * Drops this database. Use with caution!
   */
  def dropDatabase(): Unit = {
    Await.ready(db.drop(), timeout)
  }

}
