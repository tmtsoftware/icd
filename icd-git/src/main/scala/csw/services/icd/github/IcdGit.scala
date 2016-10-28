package csw.services.icd.github

import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import csw.services.icd.db.{IcdDb, IcdDbDefaults, IcdVersionManager}

/**
  * Implements the icd-git command line application, which is used to manage ICD versions in Git and
  * support importing from Git into the ICD database.
  */
object IcdGit extends App {

  // Default user name if none given
  private val defaultUser = System.getProperty("user.name")

  /**
    * Command line options ("icd-git --help" prints a usage message with descriptions of all the options)
    */
  private case class Options(
                              list: Boolean = false,
                              subsystems: List[SubsystemAndVersion] = Nil,
                              icdVersion: Option[String] = None,
                              interactive: Boolean = false,
                              publish: Boolean = false,
                              unpublish: Boolean = false,
                              majorVersion: Boolean = false,
                              user: Option[String] = None,
                              password: Option[String] = None,
                              comment: Option[String] = None,
                              dbName: String = IcdDbDefaults.defaultDbName,
                              host: String = IcdDbDefaults.defaultHost,
                              port: Int = IcdDbDefaults.defaultPort,
                              ingest: Boolean = false
                            )

  private def parseSubsystemsArg(s: String): List[SubsystemAndVersion] = {
    s.split(',').toList.map(SubsystemAndVersion(_)).sorted
  }

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-git") {
    head("icd-git", System.getProperty("CSW_VERSION"))

    opt[Unit]('l', "list") action { (x, c) =>
      c.copy(list = true)
    } text "Prints the list of API or ICD versions defined on GitHub for the given subsystem options"

    opt[String]('s', "subsystems") valueName "<subsystem1>[:version1],..." action { (x, c) =>
      c.copy(subsystems = parseSubsystemsArg(x))
    } text "Specifies the subsystems (and optional versions) of the APIs to be used by the other options"

    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) =>
      c.copy(icdVersion = Some(x))
    } text "Specifies the ICD version for the --unpublish option"

    opt[Unit]('i', "interactive") action { (_, c) =>
      c.copy(interactive = true)
    } text "Interactive mode: Asks to choose missing options"

    opt[Unit]("publish") action { (_, c) =>
      c.copy(publish = true)
    } text "Publish an API (one subsystem) or ICD (two subsystems) based on the options (--subsystems, --user, --password, --comment)"

    opt[Unit]("unpublish") action { (_, c) =>
      c.copy(unpublish = true)
    } text "Deletes the entry for the given API or ICD version (Use together with --subsystems, --icdversion)"

    opt[Unit]("major") action { (_, c) =>
      c.copy(majorVersion = true)
    } text "Use with --publish to increment the major version"

    opt[String]('u', "user") valueName "<user>" action { (x, c) =>
      c.copy(user = Some(x))
    } text "Use with --publish or --unpublish to set the GitHub user name (default: $USER)"

    opt[String]('p', "password") valueName "<password>" action { (x, c) =>
      c.copy(password = Some(x))
    } text "Use with --publish or --unpublish to set the user's GitHub password"

    opt[String]('m', "comment") valueName "<text>" action { (x, c) =>
      c.copy(comment = Some(x))
    } text "Use with --publish to add a comment describing the changes made"

    opt[String]('d', "db") valueName "<name>" action { (x, c) =>
      c.copy(dbName = x)
    } text "The name of the database to use (for the --ingest option, default: icds)"

    opt[String]("host") valueName "<hostname>" action { (x, c) =>
      c.copy(host = x)
    } text "The host name where the database is running (for the --ingest option, default: localhost)"

    opt[Int]("port") valueName "<number>" action { (x, c) =>
      c.copy(port = x)
    } text "The port number to use for the database (for the --ingest option, default: 27017)"

    opt[Unit]("ingest") action { (_, c) =>
      c.copy(ingest = true)
    } text "Ingests the selected subsystem and target subsystem model files and ICDs from GitHub into the ICD database (Ingests all subsystems, if neither option is given)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
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
  private def run(opts: Options): Unit = {
    val options = if (opts.interactive) interact(opts) else opts
    if (options.list) list(options)
    if (options.unpublish) unpublish(options)
    if (options.publish) publish(options)
    if (options.ingest) ingest(options)
  }

  private def error(msg: String): Unit = IcdGitManager.error(msg)

  // If the --interactive option was given, ask for any missing options
  private def interact(options: Options): Options = {
    import scala.io.StdIn._

    // If no subsystem was specified, ask for one, using the given prompt
    def readSubsystemAndVersion(sv: Option[SubsystemAndVersion], prompt: String, needsVersion: Boolean): Option[SubsystemAndVersion] = {
      if (sv.isEmpty) {
        println(s"Please enter the $prompt subsystem: (one of ${IcdVersionManager.allSubsystems.mkString(", ")})")
        val s = readLine()
        if (s == null || s.isEmpty) sv
        else Option(s).map { subsys =>
          if (needsVersion) {
            val versions = IcdGitManager.getSubsystemVersionNumbers(SubsystemAndVersion(subsys, None))
            if (versions.isEmpty) error(s"No published versions of $subsys were found. Please use --publish option.")
            val version = if (versions.size == 1) versions.head
            else {
              println(s"Please enter the version for $subsys: (one of $versions)")
              readLine()
            }
            SubsystemAndVersion(subsys, Some(version))
          } else SubsystemAndVersion(subsys, None)
        }
      } else sv
    }

    def readIcdVersion(opts: Options): Option[String] = {
      if (opts.icdVersion.isEmpty) {
        println(s"Please enter the ICD version number to unpublish: (Choose version number from the list below):")
        list(opts)
        // XXX TODO: check that version is correct and exists
        Option(readLine())
      } else opts.icdVersion
    }

    // Get the user name and password and return the pair
    def readCredentials(opts: Options): (Option[String], Option[String]) = {
      val user = if (options.user.isDefined) options.user
      else {
        println(s"Enter the user name for Git: [$defaultUser]")
        val u = Option(readLine)
        if (u.isDefined) u else Some(defaultUser)
      }
      val password = if (options.password.isDefined) options.password
      else {
        println(s"Enter the password for Git:")
        Option(System.console().readPassword()).map(new String(_))
      }
      (user, password)
    }

    def readComment(): Option[String] = {
      println("Enter a comment for the new ICD version:")
      Option(readLine)
    }

    // XXX TODO: How many subsystems to ask for?
    def askIngestAll(opts: Options): Boolean = {
      if (opts.subsystems.nonEmpty) false
      else {
        println("Do you want to ingest all subsystem model files into the ICD database? [no] (Answer no to select the two subsystems): ")
        val ans = Option(readLine)
        ans.isDefined && Set("y", "yes").contains(ans.get.toLowerCase())
      }
    }

    def askIfPublishingAnIcd(): Boolean = {
      println("Do you want to publish an API for one subsystem or an ICD between two subsystems? [api] (Answer api or icd): ")
      val ans = Option(readLine)
      ans.isDefined && Set("i", "icd").contains(ans.get.toLowerCase())

    }

    val ingestAll = if (options.ingest) askIngestAll(options) else false
    val isIcd = options.publish && (options.subsystems.size == 2 || askIfPublishingAnIcd())
    val subsysOpt = if (options.ingest && ingestAll) None else readSubsystemAndVersion(options.subsystems.headOption, "first", options.publish && isIcd)
    val needsTarget = options.publish || options.unpublish || options.list || (options.ingest && !ingestAll)
    val t = if (options.subsystems.size > 1) options.subsystems.tail.headOption else None
    val targetOpt = if (needsTarget && isIcd) readSubsystemAndVersion(t, "second", options.publish) else t
    val rest = if (options.subsystems.size > 2) options.subsystems.tail.tail else Nil
    val subsysList = (subsysOpt ++ targetOpt ++ rest).toList
    val icdVersion = if (options.unpublish) readIcdVersion(options.copy(subsystems = subsysList)) else options.icdVersion
    val needsPassword = options.publish || options.unpublish
    val (user, password) = if (needsPassword) readCredentials(options) else (options.user, options.password)
    val comment = if (options.publish) readComment() else options.comment
    options.copy(subsystems = subsysList, icdVersion = icdVersion, user = user, password = password, comment = comment)
  }

  // --list option (print API and ICD versions for subsystem and target)
  private def list(options: Options): Unit = {
    if (options.subsystems.size == 2) {
      // XXX TODO: Get all pairs of subsystems?
      for {
        icdVersions <- IcdGitManager.list(options.subsystems)
      } {
        icdVersions.icds.foreach { icd =>
          val a = s"${icdVersions.subsystems.head}-${icd.versions.head}"
          val b = s"${icdVersions.subsystems(1)}-${icd.versions(1)}"
          println(s"\n- ICD Version ${icd.icdVersion} between $a and $b: published by ${icd.user} on ${icd.date}: ${icd.comment}\n")
        }
      }
    } else if (options.subsystems.nonEmpty) {
      // list the publish history for each of the given subsystems
      options.subsystems.map(sv => IcdGitManager.getApiVersions(sv)).foreach {
        _.foreach { a =>
          a.apis.foreach { api =>
            println(s"\nSubsystem ${a.subsystem}-${api.version}: created by ${api.user} on ${api.date}:\n${api.comment}\n")
          }
        }
      }
    } else {
      // list the publish history for all subsystems and icds?
      //      XXX
    }
  }

  // --unpublish option
  private def unpublish(options: Options): Unit = {
    if (options.password.isEmpty) error("Missing required --password option")
    if (options.subsystems.size != 1 && options.subsystems.size != 2)
      error("Missing --subsystems option with one, or two, comma separated subsystem names")
    if (options.subsystems.size == 2 && options.icdVersion.isEmpty)
      error("Missing required --icdVersion option")
    val user = options.user.getOrElse(defaultUser)
    val password = options.password.get
    val subsystem = options.subsystems.head.subsystem
    val comment = options.comment.getOrElse("No comment")

    if (options.subsystems.size == 2) {
      // unpublish ICD
      val icdVersion = options.icdVersion.get
      val target = options.subsystems.tail.head.subsystem
      val icd = IcdGitManager.unpublish(icdVersion, subsystem, target, user, password, comment)
      if (icd.isEmpty)
        error(s"ICD version $icdVersion for $subsystem and $target does not exist")
      else
        println(s"Removed ICD version $icdVersion from the list of ICDs for $subsystem and $target")
    } else {
      // unpublish API
      val sv = SubsystemAndVersion(subsystem)
      val api = IcdGitManager.unpublish(sv, user, password, comment)
      if (api.isEmpty) {
        sv.versionOpt match {
          case Some(version) =>
            error(s"API version $version for $subsystem does not exist")
          case None =>
            error(s"No published API version for $subsystem was found")
        }
      }
      else {
        println(s"Removed API version ${api.get.version} from the list of published versions for $subsystem")
      }
    }
  }

  // --publish option
  private def publish(options: Options): Unit = {
    if (options.subsystems.isEmpty) error("Missing --subsystems option")
    if (options.password.isEmpty) error("Missing required --password option")
    val user = options.user.getOrElse(defaultUser)
    val password = options.password.get
    val comment = options.comment.getOrElse("No comment")

    if (options.subsystems.isEmpty) error("Missing --subsystems option: Please specify one subsystem to publish an API, or two subsystems with versions for an ICD")
    if (options.subsystems.size == 1) {
      // publish subsystem API
      val sv = options.subsystems.head
      if (sv.versionOpt.isDefined) error(s"Invalid use of :version in ${sv.subsystem}:${sv.versionOpt}")
      val info = IcdGitManager.publish(sv.subsystem, options.majorVersion, user, password, comment)
      println(s"Created API version ${info.version} of ${sv.subsystem}")
    } else if (options.subsystems.size == 2) {
      // publish ICD between two subsystems
      val sv = options.subsystems.head
      val tv = options.subsystems.tail.head
      val info = IcdGitManager.publish(options.subsystems, options.majorVersion, user, password, comment)
      println(s"Created ICD version ${info.icdVersion.icdVersion} based on $sv and $tv")
    }
  }

  // Handle the --ingest option
  private def ingest(options: Options): Unit = {
    // Get the MongoDB handle
    val db = IcdDb(options.dbName, options.host, options.port)
    try {
      db.dropDatabase()
    } catch {
      case ex: Exception => error(s"Unable to drop the existing ICD database: $ex")
    }

    IcdGitManager.ingest(db, options.subsystems, (s: String) => println(s))
  }
}

