package csw.services.icd.db

import java.io.File

import com.mongodb.casbah.Imports._
import com.typesafe.config.{ConfigFactory, Config}
import csw.services.icd._
import csw.services.icd.model.{BaseModelParser, SubsystemModelParser}
import org.joda.time.DateTimeZone

import scala.io.StdIn

object IcdDbDefaults {
  private val conf = ConfigFactory.load
  val defaultPort = conf.getInt("icd.db.port")
  val defaultHost = conf.getString("icd.db.host")
  val defaultDbName = conf.getString("icd.db.name")
}

object IcdDb extends App {

  import IcdDbDefaults._

  /**
   * Command line options ("icd-db --help" prints a usage message with descriptions of all the options)
   */
  case class Options(
    dbName:       String         = defaultDbName,
    host:         String         = defaultHost,
    port:         Int            = defaultPort,
    ingest:       Option[File]   = None,
    list:         Option[String] = None,
    subsystem:    Option[String] = None,
    target:       Option[String] = None,
    icdVersion:   Option[String] = None,
    component:    Option[String] = None,
    outputFile:   Option[File]   = None,
    drop:         Option[String] = None,
    versions:     Option[String] = None,
    diff:         Option[String] = None,
    publish:      Boolean        = false,
    majorVersion: Boolean        = false,
    comment:      String         = "",
    publishes:    Option[String] = None,
    subscribes:   Option[String] = None
  )

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
    } text "Top level directory containing files to ingest into the database"

    opt[String]('l', "list") valueName "[subsystems|assemblies|hcds|all]" action { (x, c) ⇒
      c.copy(list = Some(x))
    } text "Prints a list of ICD subsystems, assemblies, HCDs or all components"

    opt[String]('c', "component") valueName "<name>" action { (x, c) ⇒
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options (subsystem must also be specified)"

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) ⇒
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by any following options"

    opt[String]('t', "target") valueName "<subsystem>[:version]" action { (x, c) ⇒
      c.copy(target = Some(x))
    } text "Specifies the target subsystem (and optional version) to be used by any following options"

    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) ⇒
      c.copy(icdVersion = Some(x))
    } text "Specifies the version to be used by any following options (overrides subsystem and target versions)"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Saves the selected API or ICD to the given file in a format based on the file's suffix (html, pdf)"

    opt[String]("drop") valueName "[db|component]" action { (x, c) ⇒
      c.copy(drop = Some(x))
    } text "Drops the specified component or database (use with caution!)"

    opt[String]("versions") valueName "<subsystem>" action { (x, c) ⇒
      c.copy(versions = Some(x))
    } text "List the version history of the given subsystem"

    opt[String]("diff") valueName "<subsystem>:<version1>[,version2]" action { (x, c) ⇒
      c.copy(diff = Some(x))
    } text "For the given subsystem, list the differences between <version1> and <version2> (or the current version)"

    opt[Unit]("publish") action { (_, c) ⇒
      c.copy(publish = true)
    } text "Publish the selected subsystem (Use together with --subsystem, --major and --comment)"

    opt[Unit]("major") action { (_, c) ⇒
      c.copy(majorVersion = true)
    } text "Use with --publish to increment the major version"

    opt[String]('m', "comment") valueName "<text>" action { (x, c) ⇒
      c.copy(comment = x)
    } text "Use with --publish to add a comment describing the changes made (default: empty string)"

    opt[String]("publishes") valueName "<path>" action { (x, c) ⇒
      c.copy(publishes = Some(x))
    } text "Prints a list of components that publish the given value (name with optional component prefix)"

    opt[String]("subscribes") valueName "<path>" action { (x, c) ⇒
      c.copy(subscribes = Some(x))
    } text "Prints a list of components that subscribe to the given value (name with optional component prefix)"

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
          //          println(e)
          e.printStackTrace()
          System.exit(1)
      }
    case None ⇒ System.exit(1)
  }

  // Run the application
  private def run(options: Options): Unit = {
    val db = IcdDb(options.dbName, options.host, options.port)

    options.ingest.map(dir ⇒ db.ingest(dir)) match {
      case Some(problems) if problems.nonEmpty ⇒
        problems.foreach(println(_))
        System.exit(1)
      case _ ⇒
    }

    options.list.foreach(list)
    options.outputFile.foreach(output)
    options.drop.foreach(drop)
    options.versions.foreach(listVersions)
    options.diff.foreach(diffVersions)
    options.publishes.foreach(listPublishes)
    options.subscribes.foreach(listSubscribes)

    if (options.publish)
      options.subsystem.foreach(publish(options.majorVersion, options.comment))

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
      if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
      IcdDbPrinter(db).saveToFile(options.subsystem.get, options.component, options.target, options.icdVersion, file)
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
          if (options.subsystem.isEmpty) error("Missing required subsystem name: Please specify --subsystem <name>")
          options.component match {
            case Some(component) ⇒
              if (confirmDrop(s"Are you sure you want to drop $component from ${options.dbName}?")) {
                println(s"Dropping $component from ${options.dbName}")
                db.query.dropComponent(options.subsystem.get, component)
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
    def listVersions(subsystem: String): Unit = {
      for (v ← db.versionManager.getVersions(subsystem)) {
        println(s"${v.versionOpt.getOrElse("*")}\t${v.date.withZone(DateTimeZone.getDefault)}\t${v.comment}")
      }
    }

    // Check that the version is in the correct format
    def checkVersion(versionOpt: Option[String]): Unit = {
      versionOpt match {
        case Some(version) ⇒
          val versionRegex = """\d+\.\d+""".r
          version match {
            case versionRegex(_*) ⇒
            case _                ⇒ error(s"Bad version format: $version, expected something like 1.0, 2.1")
          }
        case None ⇒
      }
    }

    // --diff option: Compare versions option. Argument format: <subsystem>:v1[,v2]
    def diffVersions(arg: String): Unit = {
      val msg = "Expected argument format: <subsystemName>:v1[,v2]"
      if (!arg.contains(":") || arg.endsWith(":") || arg.endsWith(",")) error(msg)
      val Array(name, vStr) = arg.split(":")
      if (vStr.isEmpty) error(msg)
      val Array(v1, v2) =
        if (vStr.contains(",")) vStr.split(",").map(Some(_)) else Array(Some(vStr), None)
      checkVersion(v1)
      checkVersion(v2)
      for (diff ← db.versionManager.diff(name, v1, v2))
        println(s"\n${diff.path}:\n${diff.patch.toString()}") // XXX TODO: work on the format?
    }

    // --publishes option
    def listPublishes(path: String): Unit = {
      // XXX TODO
    }

    // --subscribes option
    def listSubscribes(path: String): Unit = {
      // XXX TODO
    }

    // --publish option
    def publish(majorVersion: Boolean, comment: String)(subsystemStr: String): Unit = {
      val (subsystem, versionOpt) = IcdVersionManager.getSubsystemAndVersion(subsystemStr)
      checkVersion(versionOpt)
      db.versionManager.publishApi(subsystem, versionOpt, majorVersion, comment, System.getProperty("user.name"))
    }
  }
}

/**
 * ICD Database (Mongodb) support
 */
case class IcdDb(
    dbName: String = IcdDbDefaults.defaultDbName,
    host:   String = IcdDbDefaults.defaultHost,
    port:   Int    = IcdDbDefaults.defaultPort
) {

  val mongoClient = MongoClient(host, port)

  // Clean up on exit
  sys.addShutdownHook(mongoClient.close())

  val db = mongoClient(dbName)
  val query = IcdDbQuery(db)
  val versionManager = IcdVersionManager(db, query)
  val manager = IcdDbManager(db, versionManager)

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
   * Ingests the given ICD config objects (based on the contents of one ICD directory)
   *
   * @param list list of ICD model files packaged as StdConfig objects
   * @return a list describing the problems, if any
   */
  private def ingestConfigs(list: List[StdConfig]): List[Problem] = {
    list.flatMap(ingestConfig)
  }

  /**
   * Ingests the given ICD config objects (based on the contents of one ICD directory)
   *
   * @param stdConfig ICD model file packaged as a StdConfig object
   * @return a list describing the problems, if any
   */
  def ingestConfig(stdConfig: StdConfig): List[Problem] = {
    try {
      ingestConfig(getCollectionName(stdConfig), stdConfig.config)
      Nil
    } catch {
      case t: Throwable ⇒ List(Problem("error", s"Internal error: $t"))
    }
  }

  /**
   * Ingests the given input config into the database.
   *
   * @param name   the name of the collection in which to store this part of the ICD
   * @param config the config to be ingested into the datasbase
   */
  private def ingestConfig(name: String, config: Config): Unit = {
    import collection.JavaConversions._
    val dbObj = config.root().unwrapped().toMap.asDBObject
    manager.ingest(name, dbObj)
  }

  /**
   * Returns the MongoDB collection name to use for the given ICD config.
   *
   * @param stdConfig ICD model file packaged as StdConfig object
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
   * Returns the subsystem name for the given ICD config.
   *
   * @param stdConfig ICD model file packaged as StdConfig object
   * @return the collection name
   */
  def getSubsystemName(stdConfig: StdConfig): String = {
    if (stdConfig.stdName.isSubsystemModel)
      SubsystemModelParser(stdConfig.config).subsystem
    else
      BaseModelParser(stdConfig.config).subsystem
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
