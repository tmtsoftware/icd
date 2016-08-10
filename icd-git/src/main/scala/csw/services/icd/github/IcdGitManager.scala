package csw.services.icd.github

import java.io.File
import java.nio.file.{Files, Paths}

import com.typesafe.config.ConfigFactory
import csw.services.icd.db.{IcdDb, IcdVersionManager}
import icd.web.shared.{IcdVersion, IcdVersionInfo}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.joda.time.DateTime

import scala.collection.JavaConverters._

/**
 * Provides methods for managing ICD versions in Git and
 * supports importing from Git into the ICD database.
 */
object IcdGitManager {
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
   * Exception thrown for normal errors
   *
   * @param msg the error message
   */
  class IcdGitException(msg: String) extends Exception(msg)

  // Report an error
  private[github] def error(msg: String): Unit = {
    throw new IcdGitException(msg)
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
  def getSubsystemAndVersion(s: String): (String, Option[String]) = {
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
  def getSubsystemGitHubUrl(s: String): String = {
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
  def getRepoVersions(url: String): List[String] = {
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
  case class SubsystemVersionInfo(subsystem: String, version: String, user: String, comment: String, date: String)

  /**
   * Gets a list of the subsystem version numbers, based on tags like v1.0.
   */
  private def getSubsystemVersionNumbers(subsystem: String): List[String] = {
    val url = getSubsystemGitHubUrl(subsystem)
    getRepoVersions(url)
  }

  /**
   * Gets a list of information about the tagged versions of the given subsystem
   */
  def getSubsystemVersionInfo(subsystem: String): List[SubsystemVersionInfo] = {
    def refFilter(ref: Ref): Boolean = {
      ref.getName.matches("refs/tags/v[0-9]+\\.[0-9]+")
    }
    val url = getSubsystemGitHubUrl(subsystem)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).setNoCheckout(true).call()
      git.tagList().call().asScala.toList.filter(refFilter).map { ref =>
        val peeledRef = git.getRepository.peel(ref)
        val version = ref.getName.substring(ref.getName.lastIndexOf('/') + 2)
        val walk = new RevWalk(git.getRepository)
        val commit = walk.parseCommit(peeledRef.getObjectId)
        //        moreInfo(git, commit)
        val comment = commit.getFullMessage
        val user = commit.getCommitterIdent.getName
        val date = new DateTime(commit.getCommitTime * 1000L).toString()
        walk.dispose()
        SubsystemVersionInfo(subsystem, version, user, comment, date)
      }
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Returns information about the ICD versions between the given subsystems (The order of
   * subsystems is not important)
   *
   * @param subsystem the first subsystem
   * @param target    the second subsystem
   * @return the ICD version info, if found
   */
  def list(subsystem: String, target: String): Option[IcdVersions] = {
    // sort by convention
    val (s, t) = if (subsystem > target) (target, subsystem) else (subsystem, target)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
      val fileName = s"$gitIcdsDir/icd-$s-$t.conf"
      val file = new File(gitWorkDir, fileName)
      val path = Paths.get(file.getPath)

      // Get the list of published ICDs for the subsystem and target from GitHub
      if (!file.exists()) None else Some(IcdVersions.fromJson(new String(Files.readAllBytes(path))))
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Deletes the entry for a published ICD (in case one was made by error).
   *
   * @param icdVersion the version number of the ICD (for example: 1.0)
   * @param subsystem  the first subsystem
   * @param target     the second subsystem
   * @param user       the GitHub user
   * @param password   the GitHub password
   * @param comment    the commit comment
   * @return the ICD entry for the removed ICD, if found
   */
  def unpublish(
    icdVersion: String,
    subsystem:  String, target: String,
    user: String, password: String, comment: String
  ): Option[IcdVersions.IcdEntry] = {
    import IcdVersions._
    import spray.json._
    // sort by convention to avoid duplicates
    val (s, t) = if (subsystem > target) (target, subsystem) else (subsystem, target)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
      val fileName = s"$gitIcdsDir/icd-$s-$t.conf"
      val file = new File(gitWorkDir, fileName)
      val path = Paths.get(file.getPath)

      // Get the list of published ICDs for the subsystem and target from GitHub
      val exists = file.exists()
      val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
      val icds = icdVersions.map(_.icds).getOrElse(Nil)

      val icdOpt = icds.find(_.icdVersion == icdVersion)
      if (icdOpt.isDefined) {
        // Write the file without the given ICD version
        val json = IcdVersions(List(s, t), icds.filter(_.icdVersion != icdVersion)).toJson.prettyPrint
        Files.write(path, json.getBytes)
        if (!exists) git.add.addFilepattern(fileName).call()
        git.commit().setOnly(fileName).setMessage(comment).call
        git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
      }
      icdOpt
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Publishes an ICD between the two subsystems by adding an entry to the relevant file in the Git repository.
   *
   * @param subsystem        the first subsystem
   * @param subsystemVersion the first subsystem version
   * @param target           the second subsystem
   * @param targetVersion    the second subsystem version
   * @param majorVersion     if true, increment the major version of the ICD
   * @param user             the GitHub user
   * @param password         the GitHub password
   * @param comment          the commit comment
   */
  def publish(subsystem: String, subsystemVersion: String,
              target: String, targetVersion: String,
              majorVersion: Boolean,
              user:         String, password: String, comment: String): IcdVersionInfo = {
    import IcdVersions._
    import spray.json._
    // sort by convention to avoid duplicates
    val (s, sv, t, tv) = if (subsystem > target) (target, targetVersion, subsystem, subsystemVersion) else (subsystem, subsystemVersion, target, targetVersion)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call
      val fileName = s"$gitIcdsDir/icd-$s-$t.conf"
      val file = new File(gitWorkDir, fileName)
      val path = Paths.get(file.getPath)

      // Get the list of published ICDs for the subsystem and target from GitHub
      val exists = file.exists()
      val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
      val icds = icdVersions.map(_.icds).getOrElse(Nil)

      // get the new ICD version info (increment the latest version number)
      val newIcdVersion = IcdVersionManager.incrVersion(icds.headOption.map(_.icdVersion), majorVersion)
      val date = DateTime.now().toString()
      val icdEntry = IcdVersions.IcdEntry(newIcdVersion, List(sv, tv),
        user, comment, date)

      // Prepend the new icd version info to the JSON file and commit/push back to GitHub
      icds.find(e => e.versions.toSet == icdEntry.versions.toSet).foreach { icd =>
        error(s"ICD version ${icd.icdVersion} is already defined for $s-$sv and $t-$tv")
      }
      val json = IcdVersions(List(s, t), icdEntry :: icds).toJson.prettyPrint

      val dir = file.getParentFile
      if (!dir.exists()) dir.mkdir()
      Files.write(path, json.getBytes)
      if (!exists) git.add.addFilepattern(fileName).call()
      git.commit().setOnly(fileName).setMessage(comment).call
      git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
      IcdVersionInfo(IcdVersion(newIcdVersion, s, sv, t, tv), user, comment, date)
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
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

  /**
   * Ingests the given subsystems and ICD (or all subsystems and ICDs) into the ICD database.
   *
   * @param dbName            the name of the database
   * @param host              the host for the db
   * @param port              the port for the db
   * @param subsystem         optional subsystem to ingest (optionally append :version)
   * @param target            optional second subsystem for ICD (optionally append :version)
   * @param feedback          function to display messages while working
   */
  def ingest(dbName: String, host: String, port: Int,
             subsystem: Option[String], target: Option[String],
             feedback: String => Unit = println): Unit = {
    // Get the MongoDB handle
    val db = IcdDb(dbName, host, port)
    try {
      db.dropDatabase()
    } catch {
      case ex: Exception => error("Unable to drop the existing ICD database: $ex")
    }

    // Get the list of subsystems to ingest
    val subsystems = {
      val l = subsystem.toList ++ target.toList
      if (l.nonEmpty) {
        feedback(s"Ingesting ${l.mkString(" and ")}")
        // sort by convention to avoid duplicate ICDs,
        l.sortWith { (a, b) =>
          // Ignore optional ":version" part
          IcdVersionManager.getSubsystemAndVersion(a)._1 < IcdVersionManager.getSubsystemAndVersion(b)._1
        }
      } else {
        feedback(s"Ingesting all known subsystems")
        allSubsystems.toList
      }
    }

    subsystems.foreach { subsystem =>
      // only import subsystems with release tags like v1.0
      if (getSubsystemVersionNumbers(subsystem).nonEmpty)
        ingest(subsystem, db, feedback)
    }
    importIcdFiles(db, subsystems, feedback)
  }

  // Ingests the given subsystem into the icd db
  private def ingest(subsys: String, db: IcdDb, feedback: String => Unit): Unit = {
    val (subsystem, versionOpt) = IcdVersionManager.getSubsystemAndVersion(subsys)
    val versionsFound = getSubsystemVersionInfo(subsystem)
    versionOpt.foreach { v =>
      if (!versionsFound.exists(_.version == v)) error(s"Version tag v$v of $subsystem not found")
    }
    def versionFilter(v: SubsystemVersionInfo) = if (versionOpt.isEmpty) true else versionOpt.get == v.version
    val versions = versionsFound.filter(versionFilter)
    val url = getSubsystemGitHubUrl(subsystem)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).call()
      versions.foreach { sv =>
        val name = s"tags/v${sv.version}"
        feedback(s"Checking out $subsystem tag v${sv.version}")
        git.checkout().setName(name).call
        feedback(s"Ingesting $subsystem ${sv.version}")
        db.ingest(gitWorkDir)
        db.versionManager.publishApi(sv.subsystem, Some(sv.version), majorVersion = false, sv.comment, sv.user)
      }
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  // Imports the ICD release information for the two subsystems, or all subsystems
  private def importIcdFiles(db: IcdDb, subsystems: List[String], feedback: String => Unit): Unit = {
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUrl).call

      if (subsystems.size == 2) {
        // Import one ICD if subsystem and target options were given
        val (subsystem, _) = IcdVersionManager.getSubsystemAndVersion(subsystems.head)
        val (target, _) = IcdVersionManager.getSubsystemAndVersion(subsystems(1))
        val fileName = s"$gitIcdsDir/icd-$subsystem-$target.conf"
        val file = new File(gitWorkDir, fileName)
        if (file.exists()) importIcdFile(db, file, feedback)
      } else {
        // Import all ICD files
        val dir = new File(gitWorkDir, gitIcdsDir)
        if (dir.exists && dir.isDirectory) {
          val files = dir.listFiles.filter(f => f.isFile && f.getName.endsWith(".conf")).toList
          files.foreach(file => importIcdFile(db, file, feedback))
        }
      }
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  // Imports ICD version information from the given file (in JSON format in the ICD-Model-Files/icds dir)
  private def importIcdFile(db: IcdDb, file: File, feedback: String => Unit): Unit = {
    val problems = db.importIcds(file)
    if (problems.nonEmpty) {
      problems.foreach(p => feedback(p.toString))
    }
  }
}
