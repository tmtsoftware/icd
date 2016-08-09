package csw.services.icd.github

import java.io.File
import java.nio.file.{Files, Paths}

import com.typesafe.config.ConfigFactory
import csw.services.icd.db.IcdVersionManager
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.joda.time.DateTime

import scala.collection.JavaConverters._

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
    comment:      String         = ""
  )

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-git") {
    head("icd-git", System.getProperty("CSW_VERSION"))

    opt[Unit]('l', "list") action { (x, c) =>
      c.copy(list = true)
    } text "Prints the list of ICDs defined for the given subsystem and target subsystem options"

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
    } text "Prints a list of available versions for the subsystems given by the subsystem and/or target options"

    opt[Unit]('i', "interactive") action { (_, c) =>
      c.copy(interactive = true)
    } text "Interactive mode: Asks to choose missing options"

    opt[Unit]("publish") action { (_, c) =>
      c.copy(publish = true)
    } text "Publish an ICD based on the selected subsystem and target (Use together with --subsystem, --target and --comment)"

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
      c.copy(comment = x)
    } text "Use with --publish to add a comment describing the changes made"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          //          println(e)
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  // Run the application
  private def run(opts: Options): Unit = {
    val options = if (opts.interactive) interact(opts) else opts
    if (options.versions) listVersions(options)
    if (options.list) list(options)
    if (options.unpublish) unpublish(options)
    if (options.publish) publish(options)
  }

  private def error(msg: String): Unit = {
    println(s"Error: $msg")
    System.exit(1)
  }

  // If the --interactive option was given, ask for any missing options
  private def interact(options: Options): Options = {
    import scala.io.StdIn._

    def readSubsystemAndVersion(opt: Option[String], prompt: String, needsVersion: Boolean): Option[String] = {
      if (opt.isEmpty) {
        println(s"Please enter the $prompt subsystem: (one of ${allSubsystems.mkString(", ")})")
        Option(readLine()).map { subsys =>
          if (needsVersion) {
            val versions = getRepoVersions(getSubsystemGitHubUrl(subsys))
            println(s"Please enter the version for $subsys: (one of $versions)")
            val version = Option(readLine()).map(v => s":$v").getOrElse("")
            s"$subsys$version"
          } else subsys
        }
      } else opt
    }

    def readIcdVersion(opts: Options): Option[String] = {
      if (opts.icdVersion.isEmpty) {

        println(s"Please enter the ICD version number to unpublish: (Choose version number from the list below):")
        list(opts)
        Option(readLine())
      } else opts.icdVersion
    }

    // Get the user name and password and return the pair
    def readCredentials(opts: Options): (Option[String], Option[String]) = {
      val user = if (options.user.isDefined) options.user else {
        println(s"Enter the user name for Git: [$defaultUser]")
        val u = Option(readLine)
        if (u.isDefined) u else Some(defaultUser)
      }
      val password = if (options.password.isDefined) options.password else {
        println(s"Enter the password for Git:")
        Option(System.console().readPassword()).map(new String(_))
      }
      (user, password)
    }

    val subsysOpt = readSubsystemAndVersion(options.subsystem, "first", options.publish)
    val needsTarget = options.publish || options.unpublish || options.list
    val targetOpt = if (needsTarget) readSubsystemAndVersion(options.target, "second", options.publish) else options.target
    val icdVersion = if (options.unpublish) readIcdVersion(options.copy(subsystem = subsysOpt, target = targetOpt)) else options.icdVersion
    val needsPassword = options.publish || options.unpublish
    val (user, password) = if (needsPassword) readCredentials(options) else (options.user, options.password)
    options.copy(subsystem = subsysOpt, target = targetOpt, icdVersion = icdVersion, user = user, password = password)
  }

  /**
   * Returns the subsystem from a string in the format "subsystem:version", where the ":version" part is optional.
   *
   * @param s the subsystem or subsystem:version string (the version is ignored here)
   * @return the subsystem
   */
  private def getSubsystem(s: String): String = {
    val subsys = IcdVersionManager.getSubsystemAndVersion(s)._1
    if (!allSubsystems.contains(subsys)) {
      error(s"unknown subsystem: $subsys")
    }
    subsys
  }

  /**
   * Returns the subsystem and version from a string in the format "subsystem:version", where the ":version" part is optional.
   * The version defaults to the latest tagged release, or None if there are no releases
   *
   * @param s the subsystem or subsystem:version string
   * @return a pair of (subsystem, Option(version))
   */
  private def getSubsystemAndVersion(s: String): (String, Option[String]) = {
    // XXX add interactive...
    val (subsys, versionOpt) = IcdVersionManager.getSubsystemAndVersion(s)
    if (!allSubsystems.contains(subsys)) {
      error(s"unknown subsystem: $subsys")
    }
    val v = if (versionOpt.isDefined) versionOpt else getRepoVersions(getSubsystemGitHubUrl(subsys)).reverse.headOption
    (subsys, v)
  }

  /**
   * Returns the GitHub URL for a subsystem string in the format "subsystem:version", where the ":version" part is optional.
   *
   * @param s the subsystem or subsystem:version string (the version is ignored here)
   * @return the subsystem
   */
  private def getSubsystemGitHubUrl(s: String): String = {
    val subsystem = getSubsystem(s)
    s"https://github.com/tmtsoftware/$subsystem-Model-Files.git"
  }

  // Sort version strings like 1.2, 1.12
  private def sortVersion(a: String, b: String): Boolean = {
    val Array(aMaj, aMin) = a.split("\\.")
    val Array(bMaj, bMin) = b.split("\\.")
    aMaj < bMaj || aMin < bMin
  }

  /**
   * Gets the versions from the git tags for the given remote repo url
   * (The tags should be like: refs/tags/v1.0, refs/tags/v1.2, ...)
   */
  private def getRepoVersions(url: String): List[String] = {
    Git.lsRemoteRepository()
      .setTags(true)
      .setRemote(url)
      .call().asScala.toList.map { ref =>
        val name = ref.getName
        name
      }.filter(_.startsWith("refs/tags/v")).map { name =>
        name.substring(name.lastIndexOf('/') + 2)
      }.filter(_.matches("[0-9]+\\.[0-9]+")).sortWith(sortVersion)
  }

  // A version of a subsystem
  private case class SubsystemVersion(subsystem: String, version: String, user: String, comment: String, date: String)

  /**
   * Gets a list of information about the tagged versions of the given subsystem
   */
  private def getSubsystemVersions(subsystem: String): List[SubsystemVersion] = {
    val url = getSubsystemGitHubUrl(subsystem)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).setNoCheckout(true).call
      git.tagList().call().asScala.toList.filter(_.getName.startsWith("refs/tags/v")).map { ref =>
        val version = ref.getName.substring(ref.getName.lastIndexOf('/') + 2)
        val walk = new RevWalk(git.getRepository)
        val commit = walk.parseCommit(ref.getObjectId)
        val comment = commit.getFullMessage
        val user = commit.getCommitterIdent.getName
        val date = new DateTime(commit.getCommitTime * 1000L).toString()
        walk.dispose()
        SubsystemVersion(subsystem, version, user, comment, date)
      }
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  // --versions option (print versions of subsystem and target subsystem)
  private def listVersions(options: Options): Unit = {
    List(options.subsystem, options.target).foreach { subsysOpt =>
      //      subsysOpt.map(getSubsystemGitHubUrl).foreach { url =>
      //        val tags = getRepoVersions(url)
      //        println(s"${subsysOpt.get} versions: ${tags.mkString(", ")}")
      //      }
      subsysOpt.map(getSubsystemVersions).foreach {
        _.foreach { sv =>
          println(s"\nSubsystem ${sv.subsystem}-${sv.version}: created by ${sv.user} on ${sv.date}:\n${sv.comment}\n----")
        }
      }
    }
  }

  // --list option (print ICD versions for subsystem and target)
  private def list(options: Options): Unit = {
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.target.isEmpty) error("Missing required --target subsystem option")
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
    } yield {
      // Checkout the icds repo in a temp dir
      val gitWorkDir = Files.createTempDirectory("icds").toFile
      try {
        Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
        val fileName = s"$gitIcdsDir/icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        val path = Paths.get(file.getPath)

        // Get the list of published ICDs for the subsystem and target from GitHub
        if (!file.exists()) error(s"No ICDs defined between $subsystem and $target")
        val icdVersions = IcdVersions.fromJson(new String(Files.readAllBytes(path)))
        val icds = icdVersions.icds
        icds.foreach { icd =>
          val a = s"${icdVersions.subsystems.head}-${icd.versions.head}"
          val b = s"${icdVersions.subsystems(1)}-${icd.versions(1)}"
          println(s"- ICD Version ${icd.icdVersion} between $a and $b: published by ${icd.user} on ${icd.date}: ${icd.comment}")
        }
      } finally {
        deleteDirectoryRecursively(gitWorkDir)
      }
    }
  }

  // --unpublish option
  private def unpublish(options: Options): Unit = {
    import IcdVersions._
    import spray.json._
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.target.isEmpty) error("Missing required --target subsystem option")
    if (options.icdVersion.isEmpty) error("Missing required --icdVersion option")
    if (options.password.isEmpty) error("Missing required --password option")
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
      icdVersion <- options.icdVersion
    } yield {
      // Checkout the icds repo in a temp dir
      val gitWorkDir = Files.createTempDirectory("icds").toFile
      try {
        val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
        val fileName = s"$gitIcdsDir/icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        val path = Paths.get(file.getPath)

        // Get the list of published ICDs for the subsystem and target from GitHub
        val exists = file.exists()
        val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
        val icds = icdVersions.map(_.icds).getOrElse(Nil)

        if (!icds.exists(_.icdVersion == icdVersion))
          error(s"ICD version $icdVersion for $subsystem and $target does not exist")

        // Write the file without the given ICD version
        val json = IcdVersions(List(subsystem, target), icds.filter(_.icdVersion != icdVersion)).toJson.prettyPrint

        Files.write(path, json.getBytes)
        if (!exists) git.add.addFilepattern(fileName).call()
        git.commit().setOnly(fileName).setMessage(options.comment).call
        git.push
          .setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(options.user.getOrElse(defaultUser), options.password.get)
          )
          .call()
        println(s"Removed ICD version $icdVersion from the list of ICDs for $subsystem and $target")
      } finally {
        deleteDirectoryRecursively(gitWorkDir)
      }
    }
  }

  // --publish option
  private def publish(options: Options): Unit = {
    import IcdVersions._
    import spray.json._
    if (options.subsystem.isEmpty) error("Missing required --subsystem option")
    if (options.target.isEmpty) error("Missing required --target subsystem option")
    if (options.password.isEmpty) error("Missing required --password option")
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
      subsystemVersion <- versionOpt
      targetVersion <- targetVersionOpt
    } {
      // Checkout the icds repo in a temp dir
      val gitWorkDir = Files.createTempDirectory("icds").toFile
      try {
        val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
        val fileName = s"$gitIcdsDir/icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        val path = Paths.get(file.getPath)

        // Get the list of published ICDs for the subsystem and target from GitHub
        val exists = file.exists()
        val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
        val icds = icdVersions.map(_.icds).getOrElse(Nil)

        // get the new ICD version info (increment the latest version number)
        val newIcdVersion = IcdVersionManager.incrVersion(icds.headOption.map(_.icdVersion), options.majorVersion)
        val date = DateTime.now().toString()
        val icdEntry = IcdVersions.IcdEntry(newIcdVersion, List(subsystemVersion, targetVersion),
          options.user.getOrElse(defaultUser), options.comment, date)

        // Prepend the new icd version info to the JSON file and commit/push back to GitHub
        icds.find(e => e.versions.toSet == icdEntry.versions.toSet).foreach { icd =>
          error(s"ICD version ${icd.icdVersion} is already defined for $subsystem-$subsystemVersion and $target-$targetVersion")
        }
        val json = IcdVersions(List(subsystem, target), icdEntry :: icds).toJson.prettyPrint

        val dir = file.getParentFile
        if (!dir.exists()) dir.mkdir()
        Files.write(path, json.getBytes)
        if (!exists) git.add.addFilepattern(fileName).call()
        git.commit().setOnly(fileName).setMessage(options.comment).call
        git.push
          .setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(options.user.getOrElse(defaultUser), options.password.get)
          )
          .call()
        println(s"Created ICD version $newIcdVersion based on $subsystem-$subsystemVersion and $target-$targetVersion")
        (subsystem, target)
      } finally {
        deleteDirectoryRecursively(gitWorkDir)
      }
    }
  }

  /**
   * Deletes the contents of the given temporary directory (recursively).
   */
  private def deleteDirectoryRecursively(dir: File): Unit = {
    // just to be safe, don't delete anything that is not in /tmp/
    val p = dir.getPath
    if (!p.startsWith("/tmp/") && !p.startsWith(tmpDir))
      throw new RuntimeException(s"Refusing to delete $dir since not in /tmp/ or $tmpDir")

    if (dir.isDirectory) {
      dir.list.foreach {
        filePath =>
          val file = new File(dir, filePath)
          if (file.isDirectory) {
            deleteDirectoryRecursively(file)
          } else {
            file.delete()
          }
      }
      dir.delete()
    }
  }
}

