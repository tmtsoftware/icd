package csw.services.icd.github

import com.typesafe.config.ConfigFactory
import csw.services.icd.db.{IcdDbDefaults, IcdVersionManager}

import scala.collection.JavaConverters._

/**
 * Implements the icd-git command line application, which is used to manage ICD versions in Git and
 * support importing from Git into the ICD database.
 */
object IcdGit extends App {

  // You can override the GitHub URI used to store the ICD version files for testing
  private val gitBaseUrl = {
    val uri = System.getProperty("csw.services.icd.github.uri")
    if (uri != null) uri else "https://github.com/tmtsoftware/ICD-Model-Files"
  }

  // Directory in the Git repository used to store the ICD version info files
  private val gitIcdsDir = "icds"

  // A temp dir is used to clone the GitHub repo in order to edit the ICD version file
  private val tmpDir = System.getProperty("java.io.tmpdir")

  // Default user name if none given
  private val defaultUser = System.getProperty("user.name")

  /**
   * A list of all known TMT subsystems (read from the same resources file used in validating the ICDs)
   */
  private val allSubsystems: Set[String] = {
    val config = ConfigFactory.parseResources("subsystem.conf")
    config.getStringList("enum").asScala.toSet
  }

  /**
   * Command line options ("icd-git --help" prints a usage message with descriptions of all the options)
   */
  private case class Options(
    list:         Boolean        = false,
    subsystem:    Option[String] = None,
    target:       Option[String] = None,
    icdVersion:   Option[String] = None,
    versions:     Boolean        = false,
    interactive:  Boolean        = false,
    publish:      Boolean        = false,
    unpublish:    Boolean        = false,
    majorVersion: Boolean        = false,
    user:         Option[String] = None,
    password:     Option[String] = None,
    comment:      Option[String] = None,
    dbName:       String         = IcdDbDefaults.defaultDbName,
    host:         String         = IcdDbDefaults.defaultHost,
    port:         Int            = IcdDbDefaults.defaultPort,
    ingest:       Boolean        = false
  )

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-git") {
    head("icd-git", System.getProperty("CSW_VERSION"))

    opt[Unit]('l', "list") action { (x, c) =>
      c.copy(list = true)
    } text "Prints the list of ICD versions defined on GitHub for the given subsystem and target subsystem options"

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by the other options"

    opt[String]('t', "target") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(target = Some(x))
    } text "Specifies the target or second subsystem (and optional version) to be used by the other options"

    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) =>
      c.copy(icdVersion = Some(x))
    } text "Specifies the ICD version for the --unpublish option"

    opt[Unit]("versions") action { (x, c) =>
      c.copy(versions = true)
    } text "Prints a list of available versions on GitHub for the subsystems given by the subsystem and/or target options"

    opt[Unit]('i', "interactive") action { (_, c) =>
      c.copy(interactive = true)
    } text "Interactive mode: Asks to choose missing options"

    opt[Unit]("publish") action { (_, c) =>
      c.copy(publish = true)
    } text "Publish an ICD based on the selected subsystem and target (Use together with --subsystem:version, --target:version and --comment)"

    opt[Unit]("unpublish") action { (_, c) =>
      c.copy(unpublish = true)
    } text "Deletes the entry for the given ICD version (Use together with --subsystem, --target and --icdversion)"

    opt[Unit]("major") action { (_, c) =>
      c.copy(majorVersion = true)
    } text "Use with --publish to increment the major version"

    opt[String]('u', "user") valueName "<user>" action { (x, c) =>
      c.copy(user = Some(x))
    } text "Use with --publish to set the GitHub user name (default: $USER)"

    opt[String]('p', "password") valueName "<password>" action { (x, c) =>
      c.copy(password = Some(x))
    } text "Use with --publish to set the user's GitHub password"

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
    } text "Ingests the selected --subsystem and --target subsystem model files from GitHub into the ICD database (Ingests all subsystems, if neither option is given)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: IcdGitManager.IcdGitException =>
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
    if (options.versions) listVersions(options)
    if (options.list) list(sortSubsystems(options))
    if (options.unpublish) unpublish(sortSubsystems(options))
    if (options.publish) publish(sortSubsystems(options))
    if (options.ingest) ingest(sortSubsystems(options, subsystemRequired = false, targetRequired = false))
  }

  private def error(msg: String): Unit = IcdGitManager.error(msg)

  // Make sure subsystem and target subsystem are alphabetically sorted (convention, since A->B == B->A)
  private def sortSubsystems(opts: Options, subsystemRequired: Boolean = true, targetRequired: Boolean = true): Options = {
    if (subsystemRequired && opts.subsystem.isEmpty) error("Missing required --subsystem option")
    if (targetRequired && opts.target.isEmpty) error("Missing required --target subsystem option")
    if (opts.subsystem.isEmpty || opts.target.isEmpty) opts
    else {
      val list = List(opts.subsystem.get, opts.target.get).sortWith { (a, b) =>
        // Ignore optional ":version" part
        IcdVersionManager.getSubsystemAndVersion(a)._1 < IcdVersionManager.getSubsystemAndVersion(b)._1
      }
      opts.copy(subsystem = Some(list.head), target = Some(list(1)))
    }
  }

  // If the --interactive option was given, ask for any missing options
  private def interact(options: Options): Options = {
    import scala.io.StdIn._

    def readSubsystemAndVersion(opt: Option[String], prompt: String, needsVersion: Boolean): Option[String] = {
      if (opt.isEmpty) {
        println(s"Please enter the $prompt subsystem: (one of ${allSubsystems.mkString(", ")})")
        Option(readLine()).map { subsys =>
          if (needsVersion) {
            val versions = IcdGitManager.getRepoVersions(IcdGitManager.getSubsystemGitHubUrl(subsys))
            if (versions.isEmpty) error(s"No tagged versions of $subsys were found. Please add a v1.0 git release tag.")
            val version = if (versions.size == 1) s":${versions.head}"
            else {
              println(s"Please enter the version for $subsys: (one of $versions)")
              Option(readLine()).map(v => s":$v").getOrElse("")
            }
            s"$subsys$version"
          } else subsys
        }
      } else opt
    }

    def readIcdVersion(opts: Options): Option[String] = {
      if (opts.icdVersion.isEmpty) {
        println(s"Please enter the ICD version number to unpublish: (Choose version number from the list below):")
        list(sortSubsystems(opts))
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

    def askIngestAll(opts: Options): Boolean = {
      if (opts.subsystem.isDefined) false
      else {
        println("Do you want to ingest all subsystem model files into the ICD database? [no] (Answer no to select the two subsystems): ")
        val ans = Option(readLine)
        ans.isDefined && Set("y", "yes").contains(ans.get.toLowerCase())
      }
    }

    val ingestAll = if (options.ingest) askIngestAll(options) else false
    val subsysOpt = if (options.ingest && ingestAll) None else readSubsystemAndVersion(options.subsystem, "first", options.publish)
    val needsTarget = options.publish || options.unpublish || options.list || (options.ingest && !ingestAll)
    val targetOpt = if (needsTarget) readSubsystemAndVersion(options.target, "second", options.publish) else options.target
    val icdVersion = if (options.unpublish) readIcdVersion(options.copy(subsystem = subsysOpt, target = targetOpt)) else options.icdVersion
    val needsPassword = options.publish || options.unpublish
    val (user, password) = if (needsPassword) readCredentials(options) else (options.user, options.password)
    val comment = if (options.publish) readComment() else options.comment
    options.copy(subsystem = subsysOpt, target = targetOpt, icdVersion = icdVersion, user = user, password = password, comment = comment)
  }

  // --versions option (print versions of subsystem and target subsystem)
  private def listVersions(options: Options): Unit = {
    List(options.subsystem, options.target).foreach { subsysOpt =>
      // This code lists the tag names and associated information
      subsysOpt.map(IcdGitManager.getSubsystemVersionInfo).foreach {
        _.foreach { sv =>
          println(s"\nSubsystem ${sv.subsystem}-${sv.version}: created by ${sv.user} on ${sv.date}:\n${sv.comment}\n----")
        }
      }
    }
  }

  // --list option (print ICD versions for subsystem and target)
  private def list(options: Options): Unit = {
    for {
      (subsystem, _) <- options.subsystem.map(IcdGitManager.getSubsystemAndVersion)
      (target, _) <- options.target.map(IcdGitManager.getSubsystemAndVersion)
      icdVersions <- IcdGitManager.list(subsystem, target)
    } {
      icdVersions.icds.foreach { icd =>
        val a = s"${icdVersions.subsystems.head}-${icd.versions.head}"
        val b = s"${icdVersions.subsystems(1)}-${icd.versions(1)}"
        println(s"- ICD Version ${icd.icdVersion} between $a and $b: published by ${icd.user} on ${icd.date}: ${icd.comment}")
      }
    }
  }

  // --unpublish option
  private def unpublish(options: Options): Unit = {
    if (options.icdVersion.isEmpty) error("Missing required --icdVersion option")
    if (options.password.isEmpty) error("Missing required --password option")
    val user = options.user.getOrElse(defaultUser)
    for {
      (subsystem, versionOpt) <- options.subsystem.map(IcdGitManager.getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(IcdGitManager.getSubsystemAndVersion)
      icdVersion <- options.icdVersion
    } {
      val icd = IcdGitManager.unpublish(icdVersion, subsystem, target, user, options.password.get, options.comment.getOrElse("No comment"))
      if (icd.isEmpty)
        error(s"ICD version $icdVersion for $subsystem and $target does not exist")
      else
        println(s"Removed ICD version $icdVersion from the list of ICDs for $subsystem and $target")
    }
  }

  // --publish option
  private def publish(options: Options): Unit = {
    if (options.password.isEmpty) error("Missing required --password option")
    val user = options.user.getOrElse(defaultUser)
    for {
      (subsystem, versionOpt) <- options.subsystem.map(IcdGitManager.getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(IcdGitManager.getSubsystemAndVersion)
      subsystemVersion <- versionOpt
      targetVersion <- targetVersionOpt
    } {
      val info = IcdGitManager.publish(
        subsystem, subsystemVersion,
        target, targetVersion,
        options.majorVersion,
        user, options.password.get, options.comment.getOrElse("No comment")
      )
      println(s"Created ICD version ${info.icdVersion} based on $subsystem-$subsystemVersion and $target-$targetVersion")
    }
  }

  // Handle the --ingest option
  private def ingest(options: Options): Unit = {
    IcdGitManager.ingest(options.dbName, options.host, options.port, options.subsystem, options.target)
  }
}

