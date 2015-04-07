package csw.services.icd.db

import java.io.File

import com.mongodb.casbah.Imports._
import com.typesafe.config.{ Config, ConfigResolveOptions, ConfigFactory }
import csw.services.icd._

import scala.io.StdIn

object IcdDb extends App {

  /**
   * Command line options: [--db <name> --host <host> --port <port>
   * --ingest <dir> --major --component <name> --list [hcds|assemblies|all]  --out <outputFile>
   * --drop [db|component]]
   *
   * (Options may be abbreviated to a single letter: For example: -i, -l, -c, -o)
   */
  case class Options(dbName: String = "icds",
                     host: String = "localhost",
                     port: Int = 27017,
                     ingest: Option[File] = None,
                     majorVersion: Boolean = false,
                     comment: String = "",
                     list: Option[String] = None,
                     component: Option[String] = None,
                     outputFile: Option[File] = None,
                     drop: Option[String] = None)

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

    opt[Unit]("major") action { (_, c) =>
      c.copy(majorVersion = true) } text "Increment the ICD's major version"

    opt[String]('m', "comment") valueName "<text>" action { (x, c) ⇒
      c.copy(comment = x)
    } text "A comment describing the changes made (default: empty string)"

    opt[String]('l', "list") valueName "[hcds|assemblies|all]" action { (x, c) ⇒
      c.copy(list = Some(x))
    } text "Prints a list of hcds, assemblies, or all components"

    opt[String]('c', "component") valueName "<name>" action { (x, c) ⇒
      c.copy(component = Some(x))
    } text "Specifies the component to be used by any following options"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Saves the component's ICD to the given file in a format based on the file's suffix (md, html, pdf)"

    opt[String]("drop") valueName "[db|component]" action { (x, c) ⇒
      c.copy(drop = Some(x))
    } text "Drops the specified component or database (use with caution!)"

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

    options.ingest.foreach(dir ⇒ db.ingest(dir.getName, dir, options.comment, options.majorVersion))
    options.list.foreach(list)
    options.outputFile.foreach(output)
    options.drop.foreach(drop)

    // --list option
    def list(componentType: String): Unit = {
      val opt = componentType.toLowerCase
      val list = if (opt.startsWith("as"))
        db.query.getAssemblyNames
      else if (opt.startsWith("h"))
        db.query.getHcdNames
      else db.query.getComponentNames
      for (name ← list) println(name)
    }

    // --output option
    def output(file: File): Unit = {
      options.component match {
        case Some(component) ⇒ IcdDbPrinter(db.query).saveToFile(component, file)
        case None            ⇒ println("Missing required component name: Please specify --component <name>")
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
              println("Missing required component name: Please specify --component <name>")
          }
        case x ⇒
          println(s"Invalid drop argument $x. Expected 'db' or 'component' (together with --component option)")
      }
      def confirmDrop(msg: String): Boolean = {
        print(s"$msg [y/n] ")
        StdIn.readLine().toLowerCase == "y"
      }
    }
  }
}

/**
 * ICD Database (Mongodb) support
 */
case class IcdDb(dbName: String = "icds", host: String = "localhost", port: Int = 27017) {

  val mongoClient = MongoClient(host, port)
  val db = mongoClient(dbName)
  val query = IcdDbQuery(db)
  val manager = IcdDbManager(db, query)

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   * @param name the name to store the ICD under (the collection name, usually the last component of the directory name)
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   * @param comment optional change comment
   * @param majorVersion if true, increment the ICD's major version
   */
  def ingest(name: String, dir: File = new File("."), comment: String = "", majorVersion: Boolean = false): List[Problem] = {
    val problems = IcdValidator.validateRecursive(dir) // XXX TODO: enforce that dir name == component name?
    if (problems.isEmpty) {
      ingestOneDir(name, dir)
      for (subdir ← subDirs(dir)) {
        // build names for collections from name and subdir names, separated by "."
        val path = dir.toPath.relativize(subdir.toPath).toString.replaceAll("/", ".")
        ingestOneDir(s"$name.$path", subdir)
      }
      manager.newVersion(name, comment, majorVersion)
    }
    problems
  }

  /**
   * Ingests all files with the standard names (stdNames) in the given directory (only) into the database.
   * @param name the name of the collection in which to store this part of the ICD
   * @param dir the directory containing the standard set of ICD files
   */
  private [db] def ingestOneDir(name: String, dir: File): Unit = {
    import csw.services.icd.StdName._
    for (stdName ← stdNames) yield {
      val inputFile = new File(dir, stdName.name)
      if (inputFile.exists()) {
        val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
        ingestConfig(s"$name.${stdName.modelBaseName}", inputConfig)
      }
    }
  }

  /**
   * Ingests the given input config into the database.
   * @param name the name of the collection in which to store this part of the ICD
   * @param config the config to be ingested into the datasbase
   * @return a list of problems, if any were found
   */
  private [db] def ingestConfig(name: String, config: Config): Unit = {
    import collection.JavaConversions._
    val dbObj = config.root().unwrapped().toMap.asDBObject
    manager.ingest(name, dbObj)
  }

  /**
   * Drops this database. Use with caution!
   */
  def dropDatabase(): Unit = {
    db.dropDatabase()
  }


}
