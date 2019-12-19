package controllers

import java.io.ByteArrayOutputStream

import javax.inject._
import csw.services.icd.IcdToPdf
import csw.services.icd.db.IcdVersionManager.{SubsystemAndVersion, VersionDiff}
import csw.services.icd.db._
import csw.services.icd.github.IcdGitManager
import diffson.playJson.DiffsonProtocol
import icd.web.shared.{IcdVersion, _}
import org.eclipse.jgit.api.errors.TransportException
import org.webjars.play._
import play.api.libs.json.Json
import play.filters.csrf.{CSRF, CSRFAddToken, CSRFCheck}
import play.api.mvc._
import play.api.{Configuration, Environment, Mode}

import scala.util.Try

// Defines the database used
object Application {
  // Used to access the ICD database
  val tryDb: Try[IcdDb] = Try(IcdDb())
}

/**
 * Provides the interface between the web client and the server
 */
//noinspection TypeAnnotation,DuplicatedCode
@Singleton
class Application @Inject()(
    env: Environment,
    addToken: CSRFAddToken,
    checkToken: CSRFCheck,
    assets: AssetsFinder,
    webJarsUtil: WebJarsUtil,
    webJarAssets: WebJarAssets,
    components: ControllerComponents,
    configuration: Configuration
) extends AbstractController(components) {

  import Application._
  import JsonSupport._

  if (tryDb.isSuccess) {
    println(
      s"icdwebserver running on http://${System.getProperty("http.host", "localhost")}:${System.getProperty("http.port", "9000")}"
    )
  } else {
    println("Error: Failed to connect to the icd database. Make sure mongod is running.")
    System.exit(1)
  }
  private val db = tryDb.get

  // Cache of API and ICD versions published on GitHub (cached for better performance)
  private var (allApiVersions, allIcdVersions) = IcdGitManager.ingestMissing(db)

  // Update the database and cache after a new API or ICD was published (or in case one was published)
  private def updateAfterPublish(): Unit = {
    val pair = IcdGitManager.ingestMissing(db)
    allApiVersions = pair._1
    allIcdVersions = pair._2
  }

  // Somehow disabling the CSRF filter in application.conf and adding it here was needed to make this work
  // (The CSRF token is needed for the file upload dialog in the client)
  def index =
    addToken(Action { implicit request =>
      implicit val environment: Environment = env
      val token                             = Csrf(CSRF.getToken.get.value)
      val debug                             = env.mode == Mode.Dev
      Ok(views.html.index(debug, assets, token, webJarsUtil))
    })

  /**
   * Gets a list of top level subsystem names
   */
  def subsystemNames = Action { implicit request =>
    val subsystemsInDb          = db.query.getSubsystemNames
    val publishedSubsystemNames = allApiVersions.map(_.subsystem)
    val names                   = publishedSubsystemNames ++ subsystemsInDb
    Ok(Json.toJson(names.toSet.toList.sorted))
  }

  /**
   * Gets information about a named subsystem
   */
  def subsystemInfo(subsystem: String, maybeVersion: Option[String]) = Action { implicit request =>
    // Get the subsystem info from the database, or if not found, look in the published GitHub repo
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    db.versionManager.getSubsystemModel(sv) match {
      case Some(model) =>
        // Found in db
        val info = SubsystemInfo(sv, model.title, model.description)
        Ok(Json.toJson(info))
      case None =>
        NotFound
    }
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, maybeVersion: Option[String]) = Action { implicit request =>
    val sv    = SubsystemWithVersion(subsystem, maybeVersion, None)
    val names = db.versionManager.getComponentNames(sv)
    Ok(Json.toJson(names))
  }

  /**
   * Query the database for information about the subsystem's components
   *
   * @param subsystem      the subsystem
   * @param maybeVersion   the subsystem's version (default: current)
   * @param maybeComponent component name (default all in subsystem)
   * @param searchAll if true, search all components for API dependencies
   */
  def componentInfo(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String], searchAll: Option[Boolean]) =
    Action { implicit request =>
      val sv              = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
      val query           = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem)))
      val versionManager  = new CachedIcdVersionManager(query)
      val displayWarnings = searchAll.getOrElse(false)
      val infoList        = new ComponentInfoHelper(displayWarnings).getComponentInfoList(versionManager, sv)
      Ok(Json.toJson(infoList))
    }

  /**
   * Query the database for information about the given components in an ICD
   *
   * @param subsystem           the source subsystem
   * @param maybeVersion        the source subsystem's version (default: current)
   * @param maybeComponent      optional component name (default: all in subsystem)
   * @param target              the target subsystem
   * @param maybeTargetVersion  the target subsystem's version
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   */
  def icdComponentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String]
  ): Action[AnyContent] = Action { implicit request =>
    val sv             = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val targetSv       = SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem, targetSv.subsystem)))
    val versionManager = new CachedIcdVersionManager(query)
    val infoList       = IcdComponentInfo.getComponentInfoList(versionManager, sv, targetSv)
    Ok(Json.toJson(infoList))
  }

  /**
   * Returns the PDF for the given ICD
   *
   * @param subsystem          the source subsystem
   * @param maybeVersion       the source subsystem's version (default: current)
   * @param maybeComponent      optional component name (default: all in subsystem)
   * @param target             the target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param maybeIcdVersion    optional ICD version (default: current)
   */
  def icdAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String]
  ): Action[AnyContent] = Action { implicit request =>
    val out = new ByteArrayOutputStream()

    // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
    // if only the subsystem or target versions were given, use those (default to latest versions)
    val v = maybeIcdVersion.getOrElse("*")

    val versions = db.versionManager.getIcdVersions(subsystem, target)
    val iv       = versions.find(_.icdVersion.icdVersion == v).map(_.icdVersion)
    val (sv, targetSv) = if (iv.isDefined) {
      val i = iv.get
      (
        SubsystemWithVersion(i.subsystem, Some(i.subsystemVersion), maybeComponent),
        SubsystemWithVersion(i.target, Some(i.targetVersion), maybeTargetComponent)
      )
    } else {
      (
        SubsystemWithVersion(subsystem, maybeVersion, maybeComponent),
        SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
      )
    }

    IcdDbPrinter(db, searchAllSubsystems = false).getIcdAsHtml(sv, targetSv, iv) match {
      case Some(html) =>
        IcdToPdf.saveAsPdf(out, html, showLogo = true)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None =>
        NotFound
    }
  }

  /**
   * Returns the PDF for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param searchAll if true, search all components for API dependencies
   */
  def apiAsPdf(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String], searchAll: Option[Boolean]) =
    Action { implicit request =>
      val out = new ByteArrayOutputStream()
      val sv  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
      IcdDbPrinter(db, searchAll.getOrElse(false)).getApiAsHtml(sv) match {
        case Some(html) =>
          IcdToPdf.saveAsPdf(out, html, showLogo = true)
          val bytes = out.toByteArray
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns a detailed list of the versions of the given subsystem
   */
  def getVersions(subsystem: String) = Action { implicit request =>
    val versions = allApiVersions
      .filter(_.subsystem == subsystem)
      .flatMap(_.apis)
      .map(a => VersionInfo(Some(a.version), a.user, a.comment, a.date))
    Ok(Json.toJson(versions))
  }

  /**
   * Returns a list of version names for the given subsystem
   */
  def getVersionNames(subsystem: String) = Action { implicit request =>
    val versions = allApiVersions
      .filter(_.subsystem == subsystem)
      .flatMap(_.apis)
      .map(_.version)
    Ok(Json.toJson(versions))
  }

  /**
   * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
   */
  def getIcdNames = Action { implicit request =>
    val list   = allIcdVersions.map(i => IcdName(i.subsystems.head, i.subsystems.tail.head))
    val sorted = list.sortWith((a, b) => a.subsystem.compareTo(b.subsystem) < 0)
    Ok(Json.toJson(sorted))
  }

  /**
   * Gets a list of versions for the ICD from subsystem to target subsystem
   */
  def getIcdVersions(subsystem: String, target: String) = Action { implicit request =>
    // convert list to use shared IcdVersion class
    val list =
      allIcdVersions.find(i => i.subsystems.head == subsystem && i.subsystems.tail.head == target).toList.flatMap(_.icds).map {
        icd =>
          val icdVersion = IcdVersion(icd.icdVersion, subsystem, icd.versions.head, target, icd.versions.tail.head)
          IcdVersionInfo(icdVersion, icd.user, icd.comment, icd.date)
      }
    Ok(Json.toJson(list))
  }

  // Packages the diff information for return to browser
  private def getDiffInfo(diff: VersionDiff): DiffInfo = {
    import DiffsonProtocol._
    val jsValue = Json.toJson(diff.patch)
    val s       = Json.prettyPrint(jsValue)
    DiffInfo(diff.path, s)
  }

  /**
   * Gets the difference between two subsystem versions
   */
  def diff(subsystem: String, versionsStr: String) = Action { implicit request =>
    val versions = versionsStr.split(',')
    val v1       = versions.head
    val v2       = versions.tail.head
    val v1Opt    = if (v1.nonEmpty) Some(v1) else None
    val v2Opt    = if (v2.nonEmpty) Some(v2) else None
    // convert list to use shared IcdVersion class
    val list = db.versionManager.diff(subsystem, v1Opt, v2Opt).map(getDiffInfo)
    Ok(Json.toJson(list))
  }

  /**
   * Returns OK(true) if Uploads should be allowed in the web app
   * (Should be set to false when running as a public server, on when running locally during development)
   */
  def isUploadAllowed = Action { implicit request =>
    val allowed = configuration.get[Boolean]("icd.allowUpload")
    Ok(Json.toJson(allowed))
  }

  /**
   * Responds with the JSON for the PublishInfo for every subsystem
   */
  def getPublishInfo = Action { implicit request =>
    val publishInfo = IcdGitManager.getPublishInfo
    Ok(Json.toJson(publishInfo))
  }

  /**
   * Publish the selected API (add an entry for the current commit of the master branch on GitHub)
   */
  def publishApi() = Action { implicit request =>
    val maybePublishApiInfo = request.body.asJson.map(json => Json.fromJson[PublishApiInfo](json).get)
    if (maybePublishApiInfo.isEmpty) {
      BadRequest("Missing POST data of type PublishApiInfo")
    } else {
      val publishApiInfo = maybePublishApiInfo.get
      try {
        val problems = IcdGitManager.validate(publishApiInfo.subsystem)
        if (problems.nonEmpty) {
          val msg =
            s"The version of ${publishApiInfo.subsystem} on GitHub did not pass validation: ${problems.map(_.toString).mkString(", ")}."
          NotAcceptable(msg)
        } else {
          val apiVersionInfo = IcdGitManager.publish(
            publishApiInfo.subsystem,
            publishApiInfo.majorVersion,
            publishApiInfo.user,
            publishApiInfo.password,
            publishApiInfo.comment
          )
          updateAfterPublish()
          Ok(Json.toJson(apiVersionInfo))
        }
      } catch {
        case ex: TransportException =>
          Unauthorized(ex.getMessage)
        case ex: Exception =>
          ex.printStackTrace()
          BadRequest(ex.getMessage)
      }
    }
  }

  /**
   * Publish an ICD (add an entry to the icds file on the master branch of https://github.com/tmt-icd/ICD-Model-Files)
   */
  def publishIcd() = Action { implicit request =>
    val maybePublishIcdInfo = request.body.asJson.map(json => Json.fromJson[PublishIcdInfo](json).get)
    if (maybePublishIcdInfo.isEmpty) {
      BadRequest("Missing POST data of type PublishIcdInfo")
    } else {
      val publishIcdInfo = maybePublishIcdInfo.get
      try {
        val sv         = SubsystemAndVersion(publishIcdInfo.subsystem, Some(publishIcdInfo.subsystemVersion))
        val tv         = SubsystemAndVersion(publishIcdInfo.target, Some(publishIcdInfo.targetVersion))
        val subsystems = List(sv, tv)
        val icdVersionInfo = IcdGitManager.publish(
          subsystems,
          publishIcdInfo.majorVersion,
          publishIcdInfo.user,
          publishIcdInfo.password,
          publishIcdInfo.comment
        )
        updateAfterPublish()
        Ok(Json.toJson(icdVersionInfo))
      } catch {
        case ex: TransportException =>
          Unauthorized(ex.getMessage)
        case ex: Exception =>
          ex.printStackTrace()
          BadRequest(ex.getMessage)
      }
    }
  }

  /**
   * Updates the cache of published APIs and ICDs (in case new ones were published)
   */
  def updatePublished() = Action { implicit request =>
    updateAfterPublish()
    Ok.as(JSON)
  }
}
