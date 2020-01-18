package csw.services.icd.github

import java.io.{File, PrintWriter}
import java.nio.file.{FileSystems, Files, Paths}

import csw.services.icd.{IcdValidator, Problem}
import csw.services.icd.db.ApiVersions.ApiEntry
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import csw.services.icd.db.{ApiVersions, IcdDb, IcdVersionManager, IcdVersions, Subsystems}
import icd.web.shared.{ApiVersionInfo, GitHubCredentials, IcdVersion, IcdVersionInfo, PublishInfo}
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
      .filter(_.apis.nonEmpty)
      .sorted

    val icdVersions = Option(icdsDir.listFiles)
      .getOrElse(Array())
      .toList
      .map(_.toPath)
      .filter(icdMatcher.matches)
      .map(path => IcdVersions.fromJson(new String(Files.readAllBytes(path))))
      .filter(_.icds.nonEmpty)
      .sorted

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

  // Gets the latest commit id of the given repo
  private def getRepoCommitId(url: String): String = {
    Git
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
      .head
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
    allIcdVersions.find(i => i.subsystems == Subsystems.sorted(subsystems.map(_.subsystem)))
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
    val (s, t) = if (Subsystems.compare(subsystem, target) > 0) (target, subsystem) else (subsystem, target)
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
   * @param subsystemList  list of subsystems to ingest (or two subsystems for an ICD, empty for all subsystems and ICDs)
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
        subsystemList.sorted
      } else {
        Subsystems.allSubsystems.map(s => SubsystemAndVersion(s, None))
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
        ingest(db, sv.subsystem, apiEntries, feedback)

      case None =>
        warning(s"No published versions of ${sv.subsystem} were found in the repository")
    }

  }

  /**
   * Ingests the given versions of the given subsystem into the icd db
   *
   * @param db         the database to use
   * @param subsystem  the subsystem to ingest into the db
   * @param apiEntries the (GitHub version) entries for the published versions to ingest
   * @param feedback   optional feedback function
   */
  def ingest(db: IcdDb, subsystem: String, apiEntries: List[ApiEntry], feedback: String => Unit): Unit = this.synchronized {
    // Checkout the subsystem repo in a temp dir
    val url        = getSubsystemGitHubUrl(subsystem)
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).call()
      apiEntries.reverse.foreach { e =>
        feedback(s"Checking out $subsystem-${e.version} (commit: ${e.commit})")
        git.checkout().setName(e.commit).call
        feedback(s"Ingesting $subsystem-${e.version}")
        val problems = db.ingest(gitWorkDir)
        problems.foreach(p => feedback(p.errorMessage()))
        db.query.afterIngestSubsystem(subsystem, problems, db.dbName)
        if (!problems.exists(_.severity != "warning")) {
          val date = DateTime.parse(e.date)
          db.versionManager.publishApi(subsystem, Some(e.version), majorVersion = false, e.comment, e.user, date)
        }
      }
      git.close()
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Validates the current version of the given subsystem that is checked in on GitHub
   *
   * @param subsystem  the subsystem to ingest into the db
   */
  def validate(subsystem: String): List[Problem] = {
    // Checkout the subsystem repo in a temp dir
    val url        = getSubsystemGitHubUrl(subsystem)
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git      = Git.cloneRepository.setDirectory(gitWorkDir).setURI(url).call
      val problems = IcdValidator.validateDirRecursive(gitWorkDir)
      git.close()
      problems
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  // Imports the ICD release information for the two subsystems, or all subsystems
  private def importIcdFiles(
      db: IcdDb,
      subsystems: List[SubsystemAndVersion],
      feedback: String => Unit,
      allIcdVersions: List[IcdVersions]
  ): Unit = {
    subsystems.size match {
      case 1 =>
      case 2 =>
        // Import one ICD if subsystem and target options were given
        allIcdVersions.find(_.subsystems == subsystems.map(_.subsystem)).foreach(db.importIcds(_, feedback))
      case _ =>
        allIcdVersions.foreach(db.importIcds(_, feedback))
    }
  }

  // Map of subsystem name to latest commit id for repos that were empty at time of development.
  // This is used to speed up the check if a repo is empty. If the commit id changes,
  // then we assume somebody added something.
  private val emptyRepos = Map(
    "CIS"    -> "7f42a42871015c5e5bdaea64f49bd26619217314",
    "CLN"    -> "29eefda09df5b1415ca69724ee131aafadcb2f87",
    "CRYO"   -> "73714a1bee9fc38c4a14ab4e9e5d3bfa731c7136",
    "CSW"    -> "0be40700817fb3ffd4393aba58207a696ae16321",
    "DMS"    -> "f9b257eded51a2daf1b13b28ef16a781f21882c7",
    "DPS"    -> "132c3aacfb1f13bed0751bf4ead3ae14827b4092",
    "FMCS"   -> "cc40c0589cbcfe299ed2166de85f2d6a83743643",
    "GMS"    -> "ed5023527d099a042bbddd1c85d9ecdec9bf4942",
    "LGSF"   -> "b01dfa73a1f3e5c677f819aa855e8158e12c1bc5",
    "MODHIS" -> "6139728cc2e1c1ba64724c5cff40c16452ee8da9",
    "NSCU"   -> "5f26e4dd18bee60f0c631e625d4b02558dccd3e1",
    "PFCS"   -> "a01b55689f5d36436c7018acfa0888f598bf6a7d",
    "PSFR"   -> "21413dae346a90dba6e3dfb1fca53dab96d46d31",
    "REFR"   -> "e57687a18f61875deea19cb1ba83ceca3d1fc9a5",
    "RPG"    -> "a62a7d5ee0354d5402124bfc56a88c46f9845979",
    "RTC"    -> "45a2a564c0e57fe628e10214aa9f0924bfc4e8fe",
    "SCMS"   -> "13916c6a742b3f37f8c6a131a1ff9291edf3f781",
    "SOSS"   -> "48684f6dc2a54d290c69edbfdd4909528a2b27a7",
    "TINS"   -> "4acb42a07e098f5acd3998a638ca11184b6e3110",
    "WFOS"   -> "330391bb97a21ff5c888594517019cb65f5c34fc"
  )

  case class SubsystemGitInfo(commitId: String, isEmpty: Boolean)

  private def getSubsystemGitInfo(subsystem: String): SubsystemGitInfo = {
    val url      = getSubsystemGitHubUrl(subsystem)
    val commitId = getRepoCommitId(url)
//    println(s"""    "$subsystem" -> "$commitId", """)
    val isEmpty = emptyRepos.get(subsystem).contains(commitId)
    SubsystemGitInfo(commitId, isEmpty)
  }

  /**
   * Checks that the given GitHub credentials are valid for publishing and throws an exception if not
   */
  def checkGitHubCredentials(gitHubCredentials: GitHubCredentials): Unit = {
    val gitWorkDir = Files.createTempDirectory("icds").toFile
    try {
      val git = Git.cloneRepository.setDirectory(gitWorkDir).setURI(gitBaseUri).call
      git.push.setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitHubCredentials.user, gitHubCredentials.password)).call()
      git.close()
    } finally {
      deleteDirectoryRecursively(gitWorkDir)
    }
  }

  /**
   * Returns information about the publish state for each known subsystem,
   * including the latest API version, list of ICDs each subsystem is involved in,
   * whether there are changes ready to be published...
   *
   * @param maybeSubsystem return info only for the given subsystem (default: for all known subsystems)
   */
  def getPublishInfo(maybeSubsystem: Option[String] = None): List[PublishInfo] = {
    val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions
    val subsystems = maybeSubsystem match {
      case Some(subsystem) => List(subsystem)
      case None => Subsystems.allSubsystems
    }
    subsystems.map { subsystem =>
      val subsystemGitInfo = getSubsystemGitInfo(subsystem)
      val maybeApiVersions = allApiVersions.find(_.subsystem == subsystem)
      val maybeApiVersionList = maybeApiVersions.toList
        .flatMap(_.apis)
        .map { apiEntry =>
          ApiVersionInfo(subsystem, apiEntry.version, apiEntry.user, apiEntry.comment, apiEntry.date)
        }
      val icdVersions = allIcdVersions
        .filter(_.subsystems.head == subsystem)
        .flatMap { icdVersions =>
          val subsystem1 = icdVersions.subsystems.head
          val subsystem2 = icdVersions.subsystems.tail.head
          icdVersions.icds.map { icdEntry =>
            val version1   = icdEntry.versions.head
            val version2   = icdEntry.versions.tail.head
            val icdVersion = IcdVersion(icdEntry.icdVersion, subsystem1, version1, subsystem2, version2)
            IcdVersionInfo(icdVersion, icdEntry.user, icdEntry.comment, icdEntry.date)
          }
        }
      val publishedCommitId = maybeApiVersions.map(_.apis.head.commit)
      val readyToPublish    = !(subsystemGitInfo.isEmpty || publishedCommitId.contains(subsystemGitInfo.commitId))
      PublishInfo(subsystem, maybeApiVersionList, icdVersions, readyToPublish)
    }
  }

  /**
   * Ingest any APIs or versions of APIs that are published, but not yet in the database
   * and return a pair of lists containing API and ICD version info.
   *
   * @param db the icd database
   * @return a pair containing lists of all API and ICD versions
   */
  def ingestMissing(db: IcdDb): (List[ApiVersions], List[IcdVersions]) = {
    val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions

    // Ingest any missing subsystems
    val missingSubsystems = allApiVersions
      .map(_.subsystem)
      .toSet
      .diff(db.query.getSubsystemNames.toSet)
      .map(SubsystemAndVersion(_, None))
    if (missingSubsystems.nonEmpty) {
      println(s"Updating the ICD database with changes from GitHub")
      IcdGitManager.ingest(db, missingSubsystems.toList, (s: String) => println(s), allApiVersions, allIcdVersions)
    }

    // Ingest any missing published subsystem versions
    val missingSubsystemVersions = allApiVersions
      .flatMap { apiVersions =>
        val versions = db.versionManager.getVersionNames(apiVersions.subsystem).toSet
        apiVersions.apis
          .filter(apiEntry => !versions.contains(apiEntry.version))
          .map(apiEntry => SubsystemAndVersion(apiVersions.subsystem, Some(apiEntry.version)))
      }
    if (missingSubsystemVersions.nonEmpty) {
      println(s"Updating the ICD database with newly published changes from GitHub")
      IcdGitManager.ingest(db, missingSubsystemVersions, (s: String) => println(s), allApiVersions, allIcdVersions)
    }

    (allApiVersions, allIcdVersions)
  }

}
