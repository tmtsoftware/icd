package csw.services.icd.github

import java.io.File
import java.nio.file.{Files, Paths}

import csw.services.icd.db.IcdVersionManager
import icd.web.shared.{IcdVersion, IcdVersionInfo}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object IcdGit extends App {

  // XXX FIXME
  private val gitHubBaseUrl = "https://github.com/abrighton/Subsystems"
  private val tmpDir = System.getProperty("java.io.tmpdir")

  /**
   * All known subsubsystems.
   * Note: This should match the icd subsystems.conf resource (or else read it to get the values...)
   */
  private val allSubsystems = Set(
    "AOESW", // AO Executive Software
    "APS", // Phasing System
    "CIS", // Communications and Information Systems
    "CLN", // Optical Cleaning System
    "COAT", // Optical Coating System
    "COOL", // Instrument Cooling System
    "CRYO", // Instrumentation Cryogenic Cooling System
    "CSW", // Common Software
    "DMS", // Data Management System
    "DPS", // Data Processing Subsystem
    "ENC", // Enclosure
    "ESEN", // Engineering Sensors
    "ESW", // Executive Software System
    "HNDL", // Optics Handling Equipment
    "HQ", // Observatory Headquarters
    "IRIS", // InfraRed Imaging Spectrometer
    "IRMS", // Infrared Multi-Slit Spectrometer
    "LGSF", // Laser Guide Star Facility
    "M1CS", // M1 Control System
    "M1S", // M1 Optics System
    "M2S", // M2 Control System
    "M3S", // M3 Control System
    "MCS", // Mount Control System
    "NFIRAOS", // Narrow Field Infrared AO System
    "NSCU", // NFIRAOS Science Calibration Unit
    "OSS", // Observatory Safety System
    "REFR", // Instrumentation Refrigerant Cooling System
    "ROAD", // Road
    "SCMS", // Site Conditions Monitoring System
    "SOSS", // Science Operations Support System
    "STR", // Structure
    "SUM", // Summit Facility
    "TCS", // Telescope Control System
    "TEST", // Dummy test subsystem
    "TEST2", // Second dummy test subsystem
    "TINC", // Test instrument control
    "TINS", // Test Instruments
    "WFOS" // Wide Field Optical Spectrometer
    )

  /**
   * Command line options ("icd-git --help" prints a usage message with descriptions of all the options)
   */
  case class Options(
    list: Boolean = false,
    subsystem: Option[String] = None,
    target: Option[String] = None,
    //                      icdVersion:   Option[String] = None,
    //                      outputFile:   Option[File]   = None,
    //                      versions:     Option[String] = None,
    //                      diff:         Option[String] = None,
    interactive: Boolean = false,
    publish: Boolean = false,
    majorVersion: Boolean = false,
    user: String = System.getProperty("user.name"),
    password: String = "",
    comment: String = "")

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("icd-git") {
    head("icd-git", System.getProperty("CSW_VERSION"))

    opt[Unit]('l', "list") action { (x, c) =>
      c.copy(list = true)
    } text "Prints a list of available versions (based on release tags) for the subsystem given by the subsystem option"

    opt[String]('s', "subsystem") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(subsystem = Some(x))
    } text "Specifies the subsystem (and optional version) to be used by the other options"

    opt[String]('t', "target") valueName "<subsystem>[:version]" action { (x, c) =>
      c.copy(target = Some(x))
    } text "Specifies the target or second subsystem (and optional version) to be used by the other options"

    //    opt[String]("icdversion") valueName "<icd-version>" action { (x, c) =>
    //      c.copy(icdVersion = Some(x))
    //    } text "Specifies the version to be used by any following options (overrides subsystem and target versions)"
    //
    //    opt[String]("versions") valueName "<subsystem>" action { (x, c) =>
    //      c.copy(versions = Some(x))
    //    } text "List the version history of the given subsystem"
    //
    //    opt[String]("diff") valueName "<subsystem>:<version1>[,version2]" action { (x, c) =>
    //      c.copy(diff = Some(x))
    //    } text "For the given subsystem, list the differences between <version1> and <version2> (or the current version)"

    opt[Unit]('i', "interactive") action { (_, c) =>
      c.copy(interactive = true)
    } text "Interactive mode: Asks to choose missing options"

    opt[Unit]("publish") action { (_, c) =>
      c.copy(publish = true)
    } text "Publish an ICD based on the selected subsystem and target (Use together with --subsystem, --target and --comment)"

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
    if (options.list) list(options)
    if (options.publish) publish(options)
  }

  def error(msg: String): Unit = {
    println(msg)
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

  // --list option
  private def list(options: Options): Unit = {
    options.subsystem.map(getSubsystemGitHubUrl).foreach { url =>
      val tags = getRepoVersions(url)
      tags.foreach(println)
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
        val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitHubBaseUrl).call
        val fileName = s"icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        val path = Paths.get(file.getPath)

        // Get the list of published ICDs for the subsystem and target from GitHub
        val exists = file.exists()
        val icds = if (exists) IcdVersions.fromJson(new String(Files.readAllBytes(path))).icds else Nil

        // get the new ICD version info (increment the latest version number)
        val newIcdVersion = IcdVersionManager.incrVersion(icds.headOption.map(_.icdVersion.icdVersion), options.majorVersion)
        val icdVersion = IcdVersion(newIcdVersion, subsystem, subsystemVersion, target, targetVersion)
        val date = DateTime.now().toString()
        val icdVersionInfo = IcdVersionInfo(icdVersion, options.user, options.comment, date)

        // Prepend the new icd version info to the JSON file and commit/push back to GitHub
        val json = IcdVersions(icdVersionInfo :: icds).toJson.prettyPrint
        Files.write(path, json.getBytes)
        if (!exists) git.add.addFilepattern(fileName).call()
        git.commit().setOnly(fileName).setMessage(options.comment).call
        git.push
          .setCredentialsProvider(new UsernamePasswordCredentialsProvider(options.user, options.password))
          .call()
      } finally {
        deleteDirectoryRecursively(gitWorkDir)
      }
    }
  }

  /**
   * Deletes the contents of the given temporary directory (recursively).
   */
  def deleteDirectoryRecursively(dir: File): Unit = {
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

