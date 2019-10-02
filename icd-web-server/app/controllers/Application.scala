package controllers

import java.io.ByteArrayOutputStream

import javax.inject._
import csw.services.icd.IcdToPdf
import csw.services.icd.db.ApiVersions.ApiEntry
import csw.services.icd.db.IcdVersionManager.{SubsystemAndVersion, VersionDiff}
import csw.services.icd.db._
import csw.services.icd.github.IcdGitManager
import diffson.playJson.DiffsonProtocol
import icd.web.shared.IcdModels.SubsystemModel
import icd.web.shared.{IcdVersion, _}
import org.webjars.play._
import play.api.libs.json.Json
import play.filters.csrf.{CSRF, CSRFAddToken, CSRFCheck}
import play.api.mvc._
import play.api.{Environment, Mode}

// Defines the database used
object Application {
  // Used to access the ICD database
  val db: IcdDb = IcdDb()
}

/**
 * Provides the interface between the web client and the server
 */
//noinspection TypeAnnotation
@Singleton
class Application @Inject()(
    env: Environment,
    addToken: CSRFAddToken,
    checkToken: CSRFCheck,
    assets: AssetsFinder,
    webJarsUtil: WebJarsUtil,
    webJarAssets: WebJarAssets,
    components: ControllerComponents
) extends AbstractController(components) {

  import Application._
  import JsonSupport._

  println(
    s"icdwebserver running on http://${System.getProperty("http.host", "localhost")}:${System.getProperty("http.port", "9000")}"
  )

  // cache of API and ICD versions published on GitHub (until next browser refresh)
  val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions

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
    Ok(Json.toJson(names.sorted.toSet))
  }

  /**
   * Ingests a published subsystem and returns the db model, if found
   *
   * @param subsystem    the subsystem name
   * @param maybeVersion optional version to ingest (otherwise all versions)
   * @return the subsystem database model
   */
  private def ingestPublishedSubsystem(subsystem: String, maybeVersion: Option[String]): Option[SubsystemModel] = {

    // Gets the matching published ApiEntry for the subsystem version
    def getApiEntry(apiVersions: ApiVersions): Option[ApiEntry] = maybeVersion match {
      case Some(version) => apiVersions.apis.find(_.version == version)
      case None          => Some(apiVersions.apis.head)
    }

    allApiVersions.find(a => a.subsystem == subsystem).flatMap(getApiEntry) match {
      case Some(apiEntry) =>
        val sv = SubsystemAndVersion(subsystem, Some(apiEntry.version))
        IcdGitManager.ingest(db, sv, List(apiEntry), println(_))
        db.versionManager.getSubsystemModel(SubsystemWithVersion(subsystem, maybeVersion, None))
      case None => None
    }
  }

  /**
   * Gets information about a named subsystem
   */
  def subsystemInfo(subsystem: String, maybeVersion: Option[String]) = Action { implicit request =>
    // Gets the matching subsystem info from GitHub, if published there
    def getPublishedSubsystemInfo: Option[SubsystemInfo] = {
      ingestPublishedSubsystem(subsystem, maybeVersion).map { model =>
        // XXX TODO FIXME: Need to pass in optional component?
        val sv = SubsystemWithVersion(model.subsystem, maybeVersion, None)
        SubsystemInfo(sv, model.title, model.description)
      }
    }

    // Get the subsystem info from the database, or if not found, look in the published GitHub repo
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    db.versionManager.getSubsystemModel(sv) match {
      case Some(model) =>
        // Found in db
        val info = SubsystemInfo(sv, model.title, model.description)
        Ok(Json.toJson(info))
      case None =>
        // Not found in db, check if its a published version on GitHub, and if so, ingest it first
        getPublishedSubsystemInfo match {
          case Some(info) =>
            Ok(Json.toJson(info))
          case None =>
            NotFound
        }
    }
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, maybeVersion: Option[String]) = Action { implicit request =>
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    if (db.versionManager.getSubsystemModel(sv).isEmpty) {
      // Not found in db, check if its a published version on GitHub, and if so, ingest it first
      ingestPublishedSubsystem(subsystem, maybeVersion) // Make sure subsystem is ingested from GitHub if needed
    }

    val names = db.versionManager.getComponentNames(sv)
    Ok(Json.toJson(names))
  }

  /**
   * Gets information about a named component in the given version of the given subsystem
   *
   * @param subsystem      the subsystem
   * @param maybeVersion   the subsystem's version (default: current)
   * @param maybeComponent component name (default all in subsystem)
   */
  def componentInfo(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]) = Action {
    implicit request =>
      val sv       = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
      val infoList = ComponentInfoHelper.getComponentInfoList(db, sv)
      Ok(Json.toJson(infoList))
  }

  /**
   * Adds information about the ICD to the database if needed by ingesting it from the GitHub ICD repo
   */
  private def ingestPublishedIcd(icdVersion: IcdVersion): Unit = {
    val v = icdVersion.icdVersion
    if (v.nonEmpty && v != "*") {
      val sv       = SubsystemAndVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion))
      val targetSv = SubsystemAndVersion(icdVersion.target, Some(icdVersion.targetVersion))
      val icds     = db.versionManager.getIcdVersions(icdVersion.subsystem, icdVersion.target)
      if (!icds.exists(_.icdVersion.icdVersion == v))
        IcdGitManager.importIcdFiles(db, List(sv, targetSv), println(_), allIcdVersions)
    }
  }

  /**
   * Gets information about a component in a given version of a subsystem
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
    val sv       = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val targetSv = SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
    if (db.versionManager.getSubsystemModel(targetSv).isEmpty) {
      ingestPublishedSubsystem(target, maybeTargetVersion)
    }
    val infoList = IcdComponentInfo.getComponentInfoList(db, sv, targetSv)
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

    // Make sure the database has the ICDs that were published on GitHub
    if (v != "*" && maybeVersion.isDefined && maybeTargetVersion.isDefined)
      ingestPublishedIcd(IcdVersion(v, subsystem, maybeVersion.get, target, maybeTargetVersion.get))

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

    IcdDbPrinter(db).getIcdAsHtml(sv, targetSv, iv) match {
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
   */
  def apiAsPdf(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]) = Action { implicit request =>
    val out = new ByteArrayOutputStream()
    val sv  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    IcdDbPrinter(db).getApiAsHtml(sv) match {
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
    val versions = allApiVersions.filter(_.subsystem == subsystem).flatMap(_.apis).map(_.version)
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
      allIcdVersions.find(i => i.subsystems.contains(subsystem) && i.subsystems.contains(target)).toList.flatMap(_.icds).map {
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

}
