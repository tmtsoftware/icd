package csw.services.icd.github

import java.io.{File, PrintWriter}
import java.nio.file.{FileSystems, Files, Paths}

import com.typesafe.config.ConfigFactory
import csw.services.icd.db.ApiVersions.ApiEntry
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import csw.services.icd.db.{ApiVersions, IcdDb, IcdVersionManager, IcdVersions}
import icd.web.shared.{ApiVersionInfo, IcdVersion, IcdVersionInfo}
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json

import scala.jdk.CollectionConverters._

/**
 * Provides methods for managing ICD versions in Git and
 * supports importing from Git into the ICD database.
 */
//noinspection DuplicatedCode
object IcdGitManager {
  //  For testing you can override the parent GitHub URI of the subsystem model file repos
  //  (The model files are then in $gitParentUri/$subsystem-Model-Files)
  private val gitParentUri = {
    val uri = System.getProperty("csw.services.icd.github.parent.uri")
    if (uri != null) uri else "https://github.com/tmt-icd"
  }

  //  For testing you can override the GitHub URI used to access the ICD and API version files
  // (These are in the $gitBaseUri/icds and $gitBaseUri/apis subdirs.)
  private val gitBaseUri = {
    val uri = System.getProperty("csw.services.icd.github.uri")
    if (uri != null) uri else s"$gitParentUri/ICD-Model-Files"
  }

  // Directory in the Git repository used to store the ICD version info files
  private val gitIcdsDir = "icds"

  // Directory in the Git repository used to store the subsystem API version info files
  private val gitApisDir = "apis"

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
   * Gets a list of information about all of the published API and ICD versions by reading any
   * apis/api-*.json and icds/icd-*.json files from the GitHub repo.
   */
  def getAllVersions: (List[ApiVersions], List[IcdVersions]) = {
    // Checkout the main git repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      // Clone the repository containing the API version info files
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      git.close()
      getAllVersions(gitWorkDir)
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Gets a list of information about all of the published API and ICD versions by reading any
   * apis/api-*.json and icds/icd-*.json files from the GitHub repo.
   *
   * @param gitWorkDir the directory containing the cloned Git repo
   */
  private def getAllVersions(gitWorkDir: File): (List[ApiVersions], List[IcdVersions]) = {
    val apisDir    = new File(gitWorkDir, gitApisDir)
    val icdsDir    = new File(gitWorkDir, gitIcdsDir)
    val apiMatcher = FileSystems.getDefault.getPathMatcher(s"glob:$apisDir/api-*.json")
    val icdMatcher = FileSystems.getDefault.getPathMatcher(s"glob:$icdsDir/icd-*.json")

    val apiVersions = Option(apisDir.listFiles)
      .getOrElse(Array())
      .toList
      .map(_.toPath)
      .filter(apiMatcher.matches)
      .map(path => ApiVersions.fromJson(new String(Files.readAllBytes(path))))

    val icdVersions = Option(icdsDir.listFiles)
      .getOrElse(Array())
      .toList
      .map(_.toPath)
      .filter(icdMatcher.matches)
      .map(path => IcdVersions.fromJson(new String(Files.readAllBytes(path))))

    (apiVersions, icdVersions)
  }

  // Report an error
  private[github] def error(msg: String): Unit = {
    throw new IllegalArgumentException(msg)
  }

  // Report a warning
  private[github] def warning(msg: String): Unit = {
    println(s"Warning: $msg")
  }

  /**
   * Returns the GitHub URL for a subsystem string in the format "subsystem:version", where the ":version" part is optional.
   *
   * @param s the subsystem or subsystem:version string (the version is ignored here)
   * @return the subsystem
   */
  def getSubsystemGitHubUrl(s: String): String = {
    val subsystem = IcdVersionManager.SubsystemAndVersion(s).subsystem
    s"$gitParentUri/$subsystem-Model-Files.git"
  }

  // Gets the commit id of the given repo
  private def getRepoCommitId(url: String): String = {
    val list = Git
      .lsRemoteRepository()
      .setHeads(true)
      .setRemote(url)
      .call()
      .asScala
      .toList
      .filter(_.getName == "refs/heads/master")
      .map { ref =>
        ref.getObjectId.getName
      }
    list.head
  }

  /**
   * Gets a list of version numbers for the given subsystem
   */
  def getSubsystemVersionNumbers(sv: SubsystemAndVersion, allApiVersions: List[ApiVersions]): List[String] = {
    getApiVersions(sv, allApiVersions).toList.flatMap(_.apis.map(_.version))
  }

  /**
   * Gets a list of version numbers for the given subsystem from an already checked out working directory
   */
  private def getSubsystemVersionNumbers(sv: SubsystemAndVersion, gitWorkDir: File): List[String] = {
    getApiVersions(sv, gitWorkDir).toList.flatMap(_.apis.map(_.version))
  }

  /**
   * Gets a list of information about the published versions of the given subsystem
   *
   * @param sv             indicates the subsystem
   * @param allApiVersions cached list of all API versions (see getAllVersions)
   */
  def getApiVersions(sv: SubsystemAndVersion, allApiVersions: List[ApiVersions]): Option[ApiVersions] = {
    allApiVersions.find(_.subsystem == sv.subsystem)
  }

  /**
   * Gets the file to use to store API versions for the given subsystem, and the relative pathname
   */
  private def getApiFile(subsystem: String, gitWorkDir: File): (File, String) = {
    val fileName = s"$gitApisDir/api-$subsystem.json"
    (new File(gitWorkDir, fileName), fileName)
  }

  /**
   * Gets the file to use to store ICD versions for the given subsystems, and the relative pathname
   */
  private def getIcdFile(subsystem: String, target: String, gitWorkDir: File): (File, String) = {
    val fileName = s"$gitIcdsDir/icd-$subsystem-$target.json"
    (new File(gitWorkDir, fileName), fileName)
  }

  /**
   * Gets the History file and name (Holds a readable list of API and ICD versions)
   */
  private def getHistoryFile(gitWorkDir: File): (File, String) = {
    val fileName = s"History.md"
    (new File(gitWorkDir, fileName), fileName)
  }

  /**
   * Gets a list of information about the published versions of the given subsystem
   * from an already checked out working directory
   */
  private def getApiVersions(sv: SubsystemAndVersion, gitWorkDir: File): Option[ApiVersions] = {
    val (file, _) = getApiFile(sv.subsystem, gitWorkDir)
    val path      = Paths.get(file.getPath)

    // Get the list of published APIs for the subsystem from GitHub
    val exists = file.exists()
    if (exists) Some(ApiVersions.fromJson(new String(Files.readAllBytes(path)))) else None
  }

  /**
   * Returns information about the ICD versions between the given subsystems (The order of
   * subsystems is not important)
   *
   * @param subsystems     the subsystems that make up the ICD
   * @param allIcdVersions cached list of ICD versions (from getAllVersions)
   * @return the ICD version info, if found
   */
  def list(subsystems: List[SubsystemAndVersion], allIcdVersions: List[IcdVersions]): Option[IcdVersions] = {
    if (subsystems.size != 2) error("Expected two subsystems that make up an ICD")
    allIcdVersions.find(i => i.subsystems == subsystems.sorted.map(_.subsystem))
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
      subsystem: String,
      target: String,
      user: String,
      password: String,
      comment: String
  ): Option[IcdVersions.IcdEntry] = {
    import IcdVersions._
    // sort by convention to avoid duplicates
    val (s, t) = if (subsystem > target) (target, subsystem) else (subsystem, target)
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git              = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      val (file, fileName) = getIcdFile(s, t, gitWorkDir)
      val path             = Paths.get(file.getPath)

      // Get the list of published ICDs for the subsystem and target from GitHub
      val exists      = file.exists()
      val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
      val icds        = icdVersions.map(_.icds).getOrElse(Nil)

      val maybeIcd = icds.find(_.icdVersion == icdVersion)
      if (maybeIcd.isDefined) {
        // Write the file without the given ICD version
        val icdVersions = IcdVersions(List(s, t), icds.filter(_.icdVersion != icdVersion))
        val jsValue     = Json.toJson(icdVersions)
        val json        = Json.prettyPrint(jsValue)
        Files.write(path, json.getBytes)
        if (!exists) git.add.addFilepattern(fileName).call()
        git.commit().setOnly(fileName).setMessage(comment).call
        updateHistoryFile(git, gitWorkDir)
        git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
      }
      git.close()
      maybeIcd
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Deletes the entry for a published API (in case one was made by error).
   *
   * @param sv       the subsystem and version
   * @param user     the GitHub user
   * @param password the GitHub password
   * @param comment  the commit comment
   * @return the entry for the removed API, if found
   */
  def unpublish(sv: SubsystemAndVersion, user: String, password: String, comment: String): Option[ApiVersions.ApiEntry] = {
    import ApiVersions._
    // Checkout the apis repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("apis").toFile
    try {
      val git              = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      val (file, fileName) = getApiFile(sv.subsystem, gitWorkDir)
      val path             = Paths.get(file.getPath)

      // Get the list of published APIs for the subsystem from GitHub
      val result =
        if (!file.exists()) None
        else {
          val apiVersions = Some(ApiVersions.fromJson(new String(Files.readAllBytes(path))))
          val apis        = apiVersions.map(_.apis).getOrElse(Nil)
          val maybeApi = sv.maybeVersion match {
            case Some(version) => apis.find(_.version == version)
            case None          => apis.headOption
          }
          maybeApi.foreach { api =>
            // Write the file without the given API version
            val apiVersions = ApiVersions(sv.subsystem, apis.filter(_.version != api.version))
            val jsValue     = Json.toJson(apiVersions)
            val json        = Json.prettyPrint(jsValue)
            Files.write(path, json.getBytes)
            git.commit().setOnly(fileName).setMessage(comment).call
            updateHistoryFile(git, gitWorkDir)
            git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
          }
          maybeApi
        }
      git.close()
      result
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Publishes an API version for the given subsystem by adding an entry to the relevant file in the Git repository.
   *
   * @param subsystem    the subsystem
   * @param majorVersion if true, increment the major version of the subsystem API
   * @param user         the GitHub user
   * @param password     the GitHub password
   * @param comment      the commit comment
   */
  def publish(subsystem: String, majorVersion: Boolean, user: String, password: String, comment: String): ApiVersionInfo = {
    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val url    = getSubsystemGitHubUrl(subsystem)
      val commit = getRepoCommitId(url)

      // Clone the repository containing the API version info files
      val git              = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      val (file, fileName) = getApiFile(subsystem, gitWorkDir)
      val path             = Paths.get(file.getPath)

      // Get the list of published APIs for the subsystem from GitHub
      val exists      = file.exists()
      val apiVersions = if (exists) Some(ApiVersions.fromJson(new String(Files.readAllBytes(path)))) else None
      val apis        = apiVersions.map(_.apis).getOrElse(Nil)

      // get the new ICD version info (increment the latest version number)
      val newApiVersion = IcdVersionManager.incrVersion(apis.headOption.map(_.version), majorVersion)
      val date          = DateTime.now().withZone(DateTimeZone.UTC).toString()
      val apiEntry      = ApiVersions.ApiEntry(newApiVersion, commit, user, comment, date)

      // Prepend the new icd version info to the JSON file and commit/push back to GitHub
      apis.find(e => e.version == apiEntry.version || e.commit == apiEntry.commit).foreach { api =>
        error(s"API version ${api.version} is already defined for $subsystem")
      }
      val json = Json.prettyPrint(Json.toJson(ApiVersions(subsystem, apiEntry :: apis)))

      val dir = file.getParentFile
      if (!dir.exists()) dir.mkdir()
      Files.write(path, json.getBytes)
      if (!exists) git.add.addFilepattern(fileName).call()
      git.commit().setOnly(fileName).setMessage(comment).call
      updateHistoryFile(git, gitWorkDir)
      git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
      git.close()
      ApiVersionInfo(subsystem, apiEntry.version, user, comment, date)
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Updates the History.md file on the git repo to contain a readable release history in markdown format
   */
  private def updateHistoryFile(git: Git, gitWorkDir: File): Unit = {
    val (apis, icds)     = getAllVersions(gitWorkDir)
    val (file, fileName) = getHistoryFile(gitWorkDir)
    val exists           = file.exists()
    val pw               = new PrintWriter(file)

    pw.print("""
        |# Release History
        |
        |This file lists the published versions of TMT subsystem APIs and ICDs between subsystems
        |and is automatically generated from the JSON files in the apis and icds subdirectories.
        |
        |## Subsystem API Release History
        |
        | """.stripMargin)

    apis.foreach { api =>
      pw.print(s"""
           |
           |### Subsystem API: ${api.subsystem}
           |
           |${api.subsystem}<br>Version | User | Date | Comment
           |--------|------|------|--------
           |""".stripMargin)

      api.apis.foreach { v =>
        pw.println(s"${v.version}|${v.user}|${v.date}|${v.comment}")
      }
    }

    pw.println("\n## ICD Release History\n")

    icds.foreach { icd =>
      val s1 = icd.subsystems.head
      val s2 = icd.subsystems.tail.head
      pw.print(s"""
           |
           |### ICD between $s1 and $s2
           |
           |ICD<br>Version|$s1<br>Version | $s2<br> Version | User | Date | Comment
           |--------|--------|------|------|--------|--------
           |""".stripMargin)

      icd.icds.foreach { v =>
        val v1 = v.versions.head
        val v2 = v.versions.tail.head
        pw.println(s"${v.icdVersion}|$v1|$v2|${v.user}|${v.date}|${v.comment}")
      }
    }
    pw.close()
    if (!exists) git.add.addFilepattern(fileName).call()
    git.commit().setOnly(fileName).setMessage("automatic update").call
  }

  private def getVersion(sv: SubsystemAndVersion, gitWorkDir: File): String = {
    sv.maybeVersion match {
      case Some(v) => v
      case None =>
        val opt = getSubsystemVersionNumbers(sv, gitWorkDir).headOption
        if (opt.isEmpty) error(s"Missing version for ${sv.subsystem}, no published version found")
        opt.get
    }
  }

  /**
   * Publishes an ICD between the two subsystems by adding an entry to the relevant file in the Git repository.
   *
   * @param subsystems   list containing the two subsystems, both with versions, for the ICD
   * @param majorVersion if true, increment the major version of the ICD
   * @param user         the GitHub user
   * @param password     the GitHub password
   * @param comment      the commit comment
   */
  def publish(
      subsystems: List[SubsystemAndVersion],
      majorVersion: Boolean,
      user: String,
      password: String,
      comment: String
  ): IcdVersionInfo = {
    import IcdVersions._

    val sorted   = subsystems.sorted
    val sv       = sorted.head
    val targetSv = sorted.tail.head

    // Checkout the icds repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git              = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      val (file, fileName) = getIcdFile(sv.subsystem, targetSv.subsystem, gitWorkDir)
      val path             = Paths.get(file.getPath)

      // Get the list of published ICDs for the subsystem and target from GitHub
      val exists      = file.exists()
      val icdVersions = if (exists) Some(IcdVersions.fromJson(new String(Files.readAllBytes(path)))) else None
      val icds        = icdVersions.map(_.icds).getOrElse(Nil)

      // get the new ICD version info (increment the latest version number)
      val newIcdVersion = IcdVersionManager.incrVersion(icds.headOption.map(_.icdVersion), majorVersion)
      val date          = DateTime.now().withZone(DateTimeZone.UTC).toString()
      val v1            = getVersion(sv, gitWorkDir)
      val v2            = getVersion(targetSv, gitWorkDir)
      val icdEntry      = IcdVersions.IcdEntry(newIcdVersion, List(v1, v2), user, comment, date)

      // Prepend the new icd version info to the JSON file and commit/push back to GitHub
      icds.find(e => e.versions.toSet == icdEntry.versions.toSet).foreach { icd =>
        error(s"ICD version ${icd.icdVersion} is already defined for ${sv.subsystem} and ${targetSv.subsystem}")
      }
      val json = Json.prettyPrint(Json.toJson(IcdVersions(List(sv.subsystem, targetSv.subsystem), icdEntry :: icds)))

      val dir = file.getParentFile
      if (!dir.exists()) dir.mkdir()
      Files.write(path, json.getBytes)
      if (!exists) git.add.addFilepattern(fileName).call()
      git.commit().setOnly(fileName).setMessage(comment).call
      updateHistoryFile(git, gitWorkDir)
      git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password)).call()
      git.close()
      IcdVersionInfo(IcdVersion(newIcdVersion, sv.subsystem, v1, targetSv.subsystem, v2), user, comment, date)
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Deletes the contents of the given temporary directory (recursively).
   */
  private[github] def deleteDirectoryRecursively(dir: File): Unit = {
    // just to be safe, don't delete anything that is not in /tmp/
    val p = dir.getPath
    if (!p.startsWith("/tmp/") && !p.startsWith(tmpDir))
      throw new RuntimeException(s"Refusing to delete $dir since not in /tmp/ or $tmpDir")

    if (dir.isDirectory) {
      dir.list.foreach { filePath =>
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
   * @param db             the database to use
   * @param subsystemList  list of subsystems to ingest (two subsystems for an ICD, empty for all subsystems and ICDs)
   * @param feedback       function to display messages while working
   * @param allApiVersions cached API version info (see getAllVersions)
   * @param allIcdVersions cached ICD version info (see getAllVersions)
   */
  def ingest(
      db: IcdDb,
      subsystemList: List[SubsystemAndVersion],
      feedback: String => Unit,
      allApiVersions: List[ApiVersions],
      allIcdVersions: List[IcdVersions]
  ): Unit = {
    // Get the list of subsystems to ingest
    val subsystems = {
      if (subsystemList.nonEmpty) {
        // sort by convention to avoid duplicate ICDs,
        feedback(s"Ingesting ${subsystemList.mkString(" and ")}")
        subsystemList.sorted
      } else {
        feedback(s"Ingesting all published subsystems from $gitBaseUri")
        allSubsystems.map(s => SubsystemAndVersion(s, None)).toList
      }
    }
    subsystems.foreach(ingest(db, _, feedback, allApiVersions))
    importIcdFiles(db, subsystems, feedback, allIcdVersions)
  }

  /**
   * Ingests the given subsystem into the icd db
   * (The given version, or all published versions, if no version was specified)
   *
   * @param db       the database to use
   * @param sv       the subsystem and optional version
   * @param feedback optional feedback function
   */
  def ingest(db: IcdDb, sv: SubsystemAndVersion, feedback: String => Unit, allApiVersions: List[ApiVersions]): Unit = {
    getApiVersions(sv, allApiVersions) match {
      case Some(versionsFound) =>
        sv.maybeVersion.foreach { v =>
          if (!versionsFound.apis.exists(_.version == v))
            warning(s"No published version $v of ${sv.subsystem} was found in the repository")
        }

        def versionFilter(e: ApiEntry) = if (sv.maybeVersion.isEmpty) true else sv.maybeVersion.get == e.version

        val apiEntries = versionsFound.apis.filter(versionFilter)
        ingest(db, sv, apiEntries, feedback)

      case None =>
        warning(s"No published versions of ${sv.subsystem} were found in the repository")
    }

  }

  /**
   * Ingests the given versions of the given subsystem into the icd db
   *
   * @param db         the database to use
   * @param sv         the subsystem and optional version
   * @param apiEntries the (GitHub version) entries for the published versions to ingest
   * @param feedback   optional feedback function
   */
  def ingest(db: IcdDb, sv: SubsystemAndVersion, apiEntries: List[ApiEntry], feedback: String => Unit): Unit = this.synchronized {
    val url = getSubsystemGitHubUrl(sv.subsystem)
    // Checkout the subsystem repo in a temp dir
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).call()
      apiEntries.reverse.foreach { e =>
        feedback(s"Checking out ${sv.subsystem}-${e.version} (commit: ${e.commit})")
        git.checkout().setName(e.commit).call
        feedback(s"Ingesting ${sv.subsystem}-${e.version}")
        // Remove the latest version of the subsystem collections here to avoid leaving old, deleted components in the db
//        db.query.dropSubsystem(sv.subsystem)
        db.ingest(gitWorkDir)
        val date = DateTime.parse(e.date)
        db.versionManager.publishApi(sv.subsystem, Some(e.version), majorVersion = false, e.comment, e.user, date)
      }
      git.close()
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  // Imports the ICD release information for the two subsystems, or all subsystems
  def importIcdFiles(
      db: IcdDb,
      subsystems: List[SubsystemAndVersion],
      feedback: String => Unit,
      allIcdVersions: List[IcdVersions]
  ): Unit = {
    if (subsystems.size == 2) {
      // Import one ICD if subsystem and target options were given
      allIcdVersions.find(_.subsystems == subsystems.map(_.subsystem)).foreach(db.importIcds(_, feedback))
    } else {
      allIcdVersions.foreach(db.importIcds(_, feedback))
    }
  }
}
