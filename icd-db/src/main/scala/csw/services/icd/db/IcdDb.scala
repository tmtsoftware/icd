package csw.services.icd.db

import java.io.File

import com.mongodb.casbah.Imports._
import com.typesafe.config.Config
import csw.services.icd._
import csw.services.icd.model.{ BaseModel, SubsystemModel }
import org.joda.time.DateTimeZone

import scala.io.StdIn

object IcdDbDefaults {
  val defaultPort = 27017
  val defaultHost = "localhost"
  val defaultDbName = "icds"
}

object IcdDb extends App {

  import IcdDbDefaults._

  /**
   * Command line options: [--db <name> --host <host> --port <port>
   * --ingest <dir> --major --component <name> --list [subsystems|hcds|assemblies|all]  --out <outputFile>
   * --drop [db|component] --versions <icdName> --diff <icdName>:<version1>[,version2]
   * --publishes <path> --subscribes <path>
   * ]
   *
   * (Options may be abbreviated to a single letter: For example: -i, -l, -c, -o)
   */
  case class Options(dbName: String = defaultDbName,
                     host: String = defaultHost,
                     port: Int = defaultPort,
                     ingest: Option[File] = None,
                     majorVersion: Boolean = false,
                     comment: String = "",
                     list: Option[String] = None,
                     component: Option[String] = None,
                     outputFile: Option[File] = None,
                     drop: Option[String] = None,
                     versions: Option[String] = None,
                     diff: Option[String] = None,
                     publishes: Option[String] = None,
                     subscribes: Option[String] = None)

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-db") {
    head("icd-db", System.getProperty("CSW_VERSION"))

    opt[String]('d', "db") valueName "<name>" action { (x, c) ⇒
      c.copy(dbName = x)
    } text "The name of the database to use (default: icds)"

    opt[String]('h', "host") valueName "<hostname>" action { (x, c) ⇒
      c.copy(host = x)
    } text "The host name where the database is running (default: localhost)"

    opt[Int]('p', "port") valueName "<number>" action { (x, c) ⇒
      c.copy(port = x)
    } text "The port number to use for the database (default: 27017)"

    opt[File]('i', "ingest") valueName "<dir>" action { (x, c) ⇒
      c.copy(ingest = Some(x))
    } text "Directory containing ICD files to ingest into the database"

    opt[Unit]("major") action { (_, c) ⇒
      c.copy(majorVersion = true)
    } text "Increment the ICD's major version"

    opt[String]('m', "comment") valueName "<text>" action { (x, c) ⇒
      c.copy(comment = x)
    } text "A comment describing the changes made (default: empty string)"

    opt[String]('l', "list") valueName "[subsystems|assemblies|hcds|all]" action { (x, c) ⇒
      c.copy(list = Some(x))
    } text "Prints a list of ICD subsystems, assemblies, HCDs or all components"

    opt[String]('c', "component") valueName "<name>" action { (x, c) ⇒
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Saves the component's ICD to the given file in a format based on the file's suffix (md, html, pdf)"

    opt[String]("drop") valueName "[db|component]" action { (x, c) ⇒
      c.copy(drop = Some(x))
    } text "Drops the specified component or database (use with caution!)"

    opt[String]("versions") valueName "<icdName>" action { (x, c) ⇒
      c.copy(versions = Some(x))
    } text "List the version history of the given ICD"

    opt[String]("diff") valueName "<icdName>:<version1>[,version2]" action { (x, c) ⇒
      c.copy(diff = Some(x))
    } text "For the given ICD, list the differences between <version1> and <version2> (or the current version)"

    opt[String]("publishes") valueName "<path>" action { (x, c) ⇒
      c.copy(publishes = Some(x))
    } text "Prints a list of ICD components that publish the given value (name with optional component prefix)"

    opt[String]("subscribes") valueName "<path>" action { (x, c) ⇒
      c.copy(subscribes = Some(x))
    } text "Prints a list of ICD components that subscribe to the given value (name with optional component prefix)"

    help("help")
    version("version")
  }

  // Parse the command line options
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

  // Run the application
  private def run(options: Options): Unit = {
    val db = IcdDb(options.dbName, options.host, options.port)

    options.ingest.foreach(dir ⇒ db.ingest(dir, options.comment, options.majorVersion))
    options.list.foreach(list)
    options.outputFile.foreach(output)
    options.drop.foreach(drop)
    options.versions.foreach(listVersions)
    options.diff.foreach(diffVersions)
    options.publishes.foreach(listPublishes)
    options.subscribes.foreach(listSubscribes)

    // --list option
    def list(componentType: String): Unit = {
      val opt = componentType.toLowerCase
      val list = if (opt.startsWith("s"))
        db.query.getSubsystemNames
      else if (opt.startsWith("as"))
        db.query.getAssemblyNames
      else if (opt.startsWith("h"))
        db.query.getHcdNames
      else db.query.getComponentNames
      for (name ← list) println(name)
    }

    def error(msg: String): Unit = {
      println(msg)
      System.exit(1)
    }

    // --output option
    def output(file: File): Unit = {
      options.component match {
        case Some(component) ⇒ IcdDbPrinter(db.query).saveToFile(component, file)
        case None            ⇒ error("Missing required component name: Please specify --component <name>")
      }
    }

    // --drop option
    def drop(opt: String): Unit = {
      opt match {
        case "db" ⇒
          if (confirmDrop(s"Are you sure you want to drop the ${options.dbName} database?")) {
            println(s"Dropping ${options.dbName}")
            db.dropDatabase()
          }
        case "component" ⇒
          options.component match {
            case Some(component) ⇒
              if (confirmDrop(s"Are you sure you want to drop $component from ${options.dbName}?")) {
                println(s"Dropping $component from ${options.dbName}")
                db.query.dropComponent(component)
              }
            case None ⇒
              error("Missing required component name: Please specify --component <name>")
          }
        case x ⇒
          error(s"Invalid drop argument $x. Expected 'db' or 'component' (together with --component option)")
      }
      def confirmDrop(msg: String): Boolean = {
        print(s"$msg [y/n] ")
        StdIn.readLine().toLowerCase == "y"
      }
    }

    // --versions option
    def listVersions(name: String): Unit = {
      for (v ← db.manager.getIcdVersions(name)) {
        println(s"${v.version}\t${v.date.withZone(DateTimeZone.getDefault)}\t${v.comment}")
      }
    }

    // Check that the version is in the correct format
    def checkVersion(version: String): Unit = {
      val versionRegex = """\d+\.\d+""".r
      version match {
        case versionRegex(_*) ⇒
        case _                ⇒ error(s"Bad version format: $version, expected something like 1.0, 2.1")
      }
    }

    // --diff option: Compare versions option. Argument format: <icdName>:v1[,v2]
    def diffVersions(arg: String): Unit = {
      val msg = "Expected argument format: <icdName>:v1[,v2]"
      if (!arg.contains(":") || arg.endsWith(":") || arg.endsWith(",")) error(msg)
      val Array(name, vStr) = arg.split(":")
      if (vStr.isEmpty) error(msg)
      val Array(v1, v2) =
        if (vStr.contains(",")) vStr.split(",") else Array(vStr, db.manager.getCurrentIcdVersion(name))
      checkVersion(v1)
      checkVersion(v2)
      for (diff ← db.manager.diff(name, v1, v2))
        println(s"\n${diff.path}:\n${diff.patch.toString()}") // XXX TODO: work on the format?
    }

    // --publishes option
    def listPublishes(path: String): Unit = {
    }

    // --subscribes option
    def listSubscribes(path: String): Unit = {
    }
  }
}

/**
 * ICD Database (Mongodb) support
 */
case class IcdDb(dbName: String = IcdDbDefaults.defaultDbName,
                 host: String = IcdDbDefaults.defaultHost,
                 port: Int = IcdDbDefaults.defaultPort) {

  val mongoClient = MongoClient(host, port)
  val db = mongoClient(dbName)
  val query = IcdDbQuery(db)
  val manager = IcdDbManager(db, query)

  def multipleSubsystemsError(subsystems: List[String]): List[Problem] =
    List(Problem("error", "Multiple subsystems found: " + subsystems.mkString(",")))

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   * @param comment optional change comment
   * @param majorVersion if true, increment the ICD's major version
   */
  def ingest(dir: File = new File("."), comment: String = "", majorVersion: Boolean = false): List[Problem] = {
    val results = (dir :: subDirs(dir)).map(ingestOneDir)
    val problems = results.flatMap {
      case Left(list) ⇒ list
      case Right(_)   ⇒ None
    }
    if (problems.nonEmpty) {
      problems
    } else {
      val subsystems = results.flatMap {
        case Left(_)     ⇒ None
        case Right(name) ⇒ Some(name)
      }.distinct
      if (subsystems.length != 1) {
        multipleSubsystemsError(subsystems)
      } else {
        manager.newVersion(subsystems.head, comment, majorVersion)
        Nil
      }
    }
  }

  /**
   * Ingests all files with the standard names (stdNames) in the given directory (only) into the database.
   * @param dir the directory containing the standard set of ICD files
   * @return on error, a list describing the problems, otherwise name of the subsystem for the ICD
   */
  private[db] def ingestOneDir(dir: File): Either[List[Problem], String] = {
    val list = StdConfig.get(dir)
    val subsystems = list.map(getSubsystemName).distinct
    if (subsystems.length != 1) {
      Left(multipleSubsystemsError(subsystems))
    } else {
      val problems = ingestConfigs(list)
      if (problems.nonEmpty) Left(problems) else Right(subsystems.head)
    }
  }

  /**
   * Ingests the given ICD config objects (based on the contents of one ICD directory)
   * @param list list of ICD model files packaged as StdConfig objects
   * @return a list describing the problems, if any
   */
  def ingestConfigs(list: List[StdConfig]): List[Problem] = {
    list.flatMap(ingestConfig)
  }

  /**
   * Ingests the given ICD config objects (based on the contents of one ICD directory)
   * @param stdConfig ICD model file packaged as a StdConfig object
   * @return a list describing the problems, if any
   */
  def ingestConfig(stdConfig: StdConfig): List[Problem] = {
    val problems = IcdValidator.validate(stdConfig.config, stdConfig.stdName.name)
    if (problems.nonEmpty) {
      problems
    } else try {
      ingestConfig(getCollectionName(stdConfig), stdConfig.config)
      Nil
    } catch {
      case t: Throwable ⇒ List(Problem("error", s"Internal error: $t"))
    }
  }

  /**
   * Ingests the given input config into the database.
   * @param name the name of the collection in which to store this part of the ICD
   * @param config the config to be ingested into the datasbase
   */
  private def ingestConfig(name: String, config: Config): Unit = {
    import collection.JavaConversions._
    val dbObj = config.root().unwrapped().toMap.asDBObject
    manager.ingest(name, dbObj)
  }

  /**
   * Returns the MongoDB collection name to use for the given ICD config.
   * @param stdConfig ICD model file packaged as StdConfig object
   * @return the collection name
   */
  private def getCollectionName(stdConfig: StdConfig): String = {
    val baseName = if (stdConfig.stdName.isSubsystemModel) {
      SubsystemModel(stdConfig.config).name
    } else {
      val model = BaseModel(stdConfig.config)
      s"${model.subsystem}.${model.component}"
    }
    s"$baseName.${stdConfig.stdName.modelBaseName}"
  }

  /**
   * Returns the subsystem name for the given ICD config.
   * @param stdConfig ICD model file packaged as StdConfig object
   * @return the collection name
   */
  def getSubsystemName(stdConfig: StdConfig): String = {
    if (stdConfig.stdName.isSubsystemModel)
      SubsystemModel(stdConfig.config).name
    else
      BaseModel(stdConfig.config).subsystem
  }

  /**
   * Closes all open connections.
   * NOTE: This connection can't be reused after closing.
   */
  def close(): Unit = {
    mongoClient.close()
  }

  /**
   * Drops this database. Use with caution!
   */
  def dropDatabase(): Unit = {
    db.dropDatabase()
  }

}
