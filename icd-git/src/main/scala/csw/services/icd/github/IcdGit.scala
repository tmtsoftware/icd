package csw.services.icd.github

import java.io.File
import java.nio.file.{ Files, Paths }

import com.typesafe.config.ConfigFactory
import csw.services.icd.db.IcdVersionManager
import org.eclipse.jgit.api.Git
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
    list: Boolean = false,
    subsystem: Option[String] = None,
    target: Option[String] = None,
    icdVersion: Option[String] = None,
    versions: Boolean = false,
    interactive: Boolean = false,
    publish: Boolean = false,
    unpublish: Boolean = false,
    majorVersion: Boolean = false,
    user: String = System.getProperty("user.name"),
    password: String = "",
    comment: String = "")

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
      c.copy(user = x)
    } text "Use with --publish to set the GitHub user name (default: $USER)"

    opt[String]('p', "password") valueName "<password>" action { (x, c) =>
      c.copy(password = x)
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
  private def run(options: Options): Unit = {
    if (options.versions) listVersions(options)
    if (options.list) list(options)
    if (options.unpublish) unpublish(options)
    if (options.publish) publish(options)
  }

  private def error(msg: String): Unit = {
    println(s"Error: $msg")
    System.exit(1)
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
    val v = if (versionOpt.isDefined) versionOpt else getRepoVersions(getSubsystemGitHubUrl(subsys)).headOption
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

  // --versions option
  private def listVersions(options: Options): Unit = {
    List(options.subsystem, options.target).foreach { subsysOpt =>
      subsysOpt.map(getSubsystemGitHubUrl).foreach { url =>
        val tags = getRepoVersions(url)
        println(s"${subsysOpt.get} versions: ${tags.mkString(", ")}")
      }
    }
  }

  // --list option
  private def list(options: Options): Unit = {
    import IcdVersions._
    import spray.json._
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
      subsystemVersion <- versionOpt
      targetVersion <- targetVersionOpt
    } yield {
      // Checkout the icds repo in a temp dir
      val gitWorkDir = Files.createTempDirectory("icds").toFile
      try {
        val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
        val fileName = s"$gitIcdsDir/icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        val path = Paths.get(file.getPath)

        // Get the list of published ICDs for the subsystem and target from GitHub
        if (!file.exists()) error(s"No ICDs defined between $subsystem and $target")
        val icdVersions = IcdVersions.fromJson(new String(Files.readAllBytes(path)))
        val icds = icdVersions.icds
        println(s"ICDs defined between $subsystem and $target:")
        icds.foreach { icd =>
          val a = s"${icdVersions.subsystems.head}-${icd.versions.head}"
          val b = s"${icdVersions.subsystems(1)}-${icd.versions(1)}"
          println(s"- v${icd.icdVersion} between $a and $b: published by ${icd.user} on ${icd.date}: ${icd.comment}")
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
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
      icdVersion <- options.icdVersion
      subsystemVersion <- versionOpt
      targetVersion <- targetVersionOpt
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
          .setCredentialsProvider(new UsernamePasswordCredentialsProvider(options.user, options.password))
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
    for {
      (subsystem, versionOpt) <- options.subsystem.map(getSubsystemAndVersion)
      (target, targetVersionOpt) <- options.target.map(getSubsystemAndVersion)
      subsystemVersion <- versionOpt
      targetVersion <- targetVersionOpt
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

        // get the new ICD version info (increment the latest version number)
        val newIcdVersion = IcdVersionManager.incrVersion(icds.headOption.map(_.icdVersion), options.majorVersion)
        val date = DateTime.now().toString()
        val icdEntry = IcdVersions.IcdEntry(newIcdVersion, List(subsystemVersion, targetVersion), options.user, options.comment, date)

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
          .setCredentialsProvider(new UsernamePasswordCredentialsProvider(options.user, options.password))
          .call()
        println(s"Created ICD version $newIcdVersion based on $subsystem-$subsystemVersion and $target-$targetVersion")
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

