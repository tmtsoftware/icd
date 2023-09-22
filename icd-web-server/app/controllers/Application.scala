package controllers

import javax.inject._
import java.security.MessageDigest
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRef
import controllers.ApplicationData.AuthAction
import csw.services.icd.db._
import csw.services.icd.github.IcdGitManager
import icd.web.shared.SharedUtils.Credentials
import icd.web.shared._
import org.eclipse.jgit.api.errors.TransportException
import org.webjars.play._
import play.api.libs.json.Json
import play.api.mvc.Cookie.SameSite.Strict
import play.filters.csrf.{CSRF, CSRFAddToken}
import play.api.mvc._
import play.api.{Configuration, Environment, Mode}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import icd.web.shared.IcdModels.{IcdModel, ServicePath}

import java.net.URLDecoder
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Provides the interface between the web client and the server
 */
//noinspection TypeAnnotation,DuplicatedCode,ScalaUnusedSymbol
@Singleton
class Application @Inject() (
    actorSystem: akka.actor.ActorSystem,
//    myExecutionContext: MyExecutionContext,
    env: Environment,
    addToken: CSRFAddToken,
    assets: AssetsFinder,
    webJarsUtil: WebJarsUtil,
    components: ControllerComponents,
    configuration: Configuration,
    authAction: AuthAction
) extends AbstractController(components) {

  import ApplicationData._
  import JsonSupport._
  import ApplicationActor._

  implicit val timeout          = Timeout(1000.seconds)
  implicit val typedActorSystem = actorSystem.toTyped
  import actorSystem._

  if (!tryDb.isSuccess) {
    println("Error: Failed to connect to the icd database. Make sure mongod is running.")
    System.exit(1)
  }
  private val db: IcdDb = tryDb.get

  // The expected SHA of username:password from application.conf
  val expectedSha = configuration.get[String](cookieName)

  // Use an actor to manage concurrent access to cached data
  val appActor: ActorRef[ApplicationActor.Messages] = {
    val behavior = ApplicationActor.create(db)
    actorSystem.spawn(behavior, "app-actor")
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
  def subsystemNames =
    Action.async {
      val resp: Future[List[String]] = appActor ? GetSubsystemNames
      resp.map(names => Ok(Json.toJson(names)))
    }

  /**
   * Gets information about a named subsystem
   */
  def subsystemInfo(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]) =
    authAction.async {
      val resp: Future[Option[SubsystemInfo]] = appActor ? (GetSubsystemInfo(subsystem, maybeVersion, maybeComponent, _))
      resp.map {
        case Some(info) => Ok(Json.toJson(info))
        case None       => NotFound
      }
    }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, maybeVersion: Option[String]) =
    authAction.async {
      val resp: Future[List[String]] = appActor ? (GetComponents(subsystem, maybeVersion, _))
      resp.map(names => Ok(Json.toJson(names)))
    }

  /**
   * Query the database for information about the subsystem's components
   *
   * @param subsystem      the subsystem
   * @param maybeVersion   the subsystem's version (default: current)
   * @param maybeComponent component name (default all in subsystem)
   * @param searchAll      if true, search all components for API dependencies
   * @param clientApi      if true, include subscribed events, sent commands
   */
  def componentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApi: Option[Boolean]
  ) =
    authAction.async {
      val resp: Future[List[ComponentInfo]] =
        appActor ? (GetComponentInfo(subsystem, maybeVersion, maybeComponent, searchAll, clientApi, _))
      resp.map(info => Ok(Json.toJson(info)))
    }

//  /**
//   * Query the database for all of the published system events, ordered by subsystem/component
//   */
//  def eventList() =
//    authAction.async {
//      val resp: Future[List[AllEventList.EventsForSubsystem]] =
//        appActor ? GetEventList
//      resp.map(info => Ok(Json.toJson(info)))
//    }

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
  ): Action[AnyContent] =
    authAction.async {
      val resp: Future[List[ComponentInfo]] = appActor ? (GetIcdComponentInfo(
        subsystem,
        maybeVersion,
        maybeComponent,
        target,
        maybeTargetVersion,
        maybeTargetComponent,
        _
      ))
      resp.map(info => Ok(Json.toJson(info)))
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
   * @param maybeOrientation   "portrait" or "landscape" (default)
   * @param maybeFontSize       base font size
   * @param maybeLineHeight     line-height for HTML
   * @param maybePaperSize      Letter, Legal, A4, A3, default: Letter
   * @param maybeDetails        If true, the PDF lists all detailed info, otherwise only the expanded rows in web app
   * @param documentNumber      optional document number to include in PDF under subtitle
   */
  def icdAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String],
      maybeDetails: Option[Boolean],
      documentNumber: Option[String]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      val expandedIds =
        if (request.method == "POST")
          request.body.asFormUrlEncoded.get("expandedIds").headOption.getOrElse("").split(',').toList
        else Nil

      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetIcdAsPdf(
          subsystem,
          maybeVersion,
          maybeComponent,
          target,
          maybeTargetVersion,
          maybeTargetComponent,
          maybeIcdVersion,
          PdfOptions(
            maybeOrientation,
            maybeFontSize,
            maybeLineHeight,
            maybePaperSize,
            maybeDetails,
            expandedIds,
            documentNumber = documentNumber.getOrElse("")
          ),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns the PDF for the given subsystem API
   *
   * @param subsystem        the source subsystem
   * @param maybeVersion     the source subsystem's version (default: current)
   * @param maybeComponent   optional component (default: all in subsystem)
   * @param searchAll        if true, search all components for API dependencies
   * @param clientApi        if true, include subscribed events and sent commands in API
   * @param maybeOrientation "portrait" or "landscape" (default)
   * @param maybeFontSize    base font size
   * @param maybeLineHeight  line-height for HTML
   * @param maybePaperSize   Letter, Legal, A4, A3, default: Letter
   * @param maybeDetails     If true, the PDF lists all detailed info, otherwise only the expanded rows in web app
   * @param documentNumber      optional document number to include in PDF under subtitle
   */
  def apiAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApi: Option[Boolean],
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String],
      maybeDetails: Option[Boolean],
      documentNumber: Option[String]
  ) =
    Action.async { implicit request =>
      val expandedIds =
        if (request.method == "POST")
          request.body.asFormUrlEncoded.get("expandedIds").headOption.getOrElse("").split(',').toList
        else Nil
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetApiAsPdf(
          subsystem,
          maybeVersion,
          maybeComponent,
          searchAll,
          clientApi,
          PdfOptions(
            maybeOrientation,
            maybeFontSize,
            maybeLineHeight,
            maybePaperSize,
            maybeDetails,
            expandedIds,
            documentNumber = documentNumber.getOrElse("")
          ),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns a PDF for the FITS keyword information
   *
   * @param tag "All" for all keywords, otherwise restrict output to given tag, as defined in DMS-Model-Files/FITS-Dictionary
   * @param maybeOrientation "portrait" or "landscape" (default)
   * @param maybeFontSize    base font size
   * @param maybeLineHeight  line-height for HTML
   * @param maybePaperSize   Letter, Legal, A4, A3, default: Letter
   * @param maybeDetails     Not used (for compatibility with other PDF generating APIs)
   *
   */
  def fitsDictionaryAsPdf(
      tag: String,
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String],
      maybeDetails: Option[Boolean]
  ) =
    Action.async { implicit request =>
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetFitsDictionaryAsPdf(
          tag,
          PdfOptions(maybeOrientation, maybeFontSize, maybeLineHeight, maybePaperSize),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns the archived items report (PDF) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   * @param maybeFontSize base font size for body text (default: 10)
   */
  def archivedItemsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String]
  ) =
    authAction.async {
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetArchivedItemsReport(
          subsystem,
          maybeVersion,
          maybeComponent,
          PdfOptions(maybeOrientation, maybeFontSize, maybeLineHeight, maybePaperSize),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns the archived items report (HTML) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   */
  def archivedItemsReportHtml(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String]
  ) =
    authAction.async {
      val resp: Future[Option[String]] = appActor ? (
        GetArchivedItemsReportHtml(
          subsystem,
          maybeVersion,
          maybeComponent,
          _
        )
      )
      resp.map {
        case Some(html) =>
          Ok(html).as("text/html")
        case None =>
          NotFound
      }
    }

  /**
   * Returns the archived items report (PDF) for all current subsystems
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   * @param maybeFontSize base font size for body text (default: 10)
   */
  def archivedItemsReportFull(
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String]
  ) =
    authAction.async {
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetArchivedItemsReportFull(
          PdfOptions(maybeOrientation, maybeFontSize, maybeLineHeight, maybePaperSize),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns a missing items report (PDF) for the given subsystem/component API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param maybeTarget    optional target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   * @param maybeFontSize base font size for body text (default: 10)
   */
  def missingItemsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String]
  ) =
    authAction.async {
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetMissingItemsReport(
          subsystem,
          maybeVersion,
          maybeComponent,
          maybeTarget,
          maybeTargetVersion,
          maybeTargetComponent,
          PdfOptions(maybeOrientation, maybeFontSize, maybeLineHeight, maybePaperSize),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns a missing items report (PDF) for all current subsystems
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   * @param maybeFontSize base font size for body text (default: 10)
   */
  def missingItemsReportFull(
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String]
  ) =
    authAction.async {
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        GetMissingItemsReportFull(
          PdfOptions(maybeOrientation, maybeFontSize, maybeLineHeight, maybePaperSize),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          Ok(bytes).as("application/pdf")
        case None =>
          NotFound
      }
    }

  /**
   * Returns the PDF for the given ICD
   *
   * @param subsystem          the source subsystem
   * @param maybeVersion       the source subsystem's version (default: current)
   * @param maybeComponent     optional component name (default: all in subsystem)
   * @param maybeTarget        optional target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param maybeIcdVersion    optional ICD version (default: current)
   * @param maybeRatio Image aspect ratio (y/x)
   * @param maybeMissingEvents Plot missing events
   * @param maybeMissingCommands Plot missing commands
   * @param maybeCommandLabels Plot command labels
   * @param maybeEventLabels Plot event labels
   * @param maybeGroupSubsystems Group components from same subsystem together
   * @param maybeLayout Dot layout engine: One of {dot,fdp,sfdp,twopi,neato,circo,patchwork}
   * @param maybeOverlap Node overlap handling: {true,false,scale}
   * @param maybeSplines Use splines for edges?
   * @param maybeOmitTypes list of component types (HCD,Assembly,Sequencer,Application) to omit as primaries (default={'HCD'})
   */
  def makeGraph(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      maybeRatio: Option[Double],
      maybeMissingEvents: Option[Boolean],
      maybeMissingCommands: Option[Boolean],
      maybeCommandLabels: Option[Boolean],
      maybeEventLabels: Option[Boolean],
      maybeGroupSubsystems: Option[Boolean],
      maybeLayout: Option[String],
      maybeOverlap: Option[String],
      maybeSplines: Option[Boolean],
      maybeOmitTypes: Option[String],
      maybeImageFormat: Option[String]
  ): Action[AnyContent] = {
    import IcdVizOptions._
    Action.async { implicit request =>
      val resp: Future[Option[Array[Byte]]] = appActor ? (
        MakeGraph(
          subsystem,
          maybeVersion,
          maybeComponent,
          maybeTarget,
          maybeTargetVersion,
          maybeTargetComponent,
          maybeIcdVersion,
          IcdVizOptions(
            ratio = maybeRatio.getOrElse(defaultRatio),
            missingEvents = maybeMissingEvents.getOrElse(defaultMissingEvents),
            missingCommands = maybeMissingCommands.getOrElse(defaultMissingCommands),
            commandLabels = maybeCommandLabels.getOrElse(defaultCommandLabels),
            eventLabels = maybeEventLabels.getOrElse(defaultEventLabels),
            groupSubsystems = maybeGroupSubsystems.getOrElse(defaultGroupSubsystems),
            layout = maybeLayout.getOrElse(defaultLayout),
            overlap = maybeOverlap.getOrElse(defaultOverlap),
            splines = maybeSplines.getOrElse(defaultUseSplines),
            omitTypes = maybeOmitTypes.getOrElse(defaultOmit).split(",").toList,
            imageFormat = maybeImageFormat.getOrElse(defaultImageFormat)
          ),
          _
        )
      )
      resp.map {
        case Some(bytes) =>
          val contentType = maybeImageFormat.getOrElse(defaultImageFormat).toLowerCase() match {
            case "png" => "image/png"
            case "svg" => "image/svg+xml"
            case "pdf" => "application/pdf"
            case "eps" => "application/postscript"
            case _     => "application/pdf"
          }
          Ok(bytes).as(contentType)
        case None =>
          NotFound
      }
    }
  }

  /**
   * Returns a detailed list of the versions of the given subsystem
   */
  def getVersions(subsystem: String) =
    authAction.async {
      val resp: Future[List[VersionInfo]] = appActor ? (GetVersions(subsystem, _))
      resp.map(versions => Ok(Json.toJson(versions)))
    }

  /**
   * Returns a list of version names for the given subsystem
   */
  def getVersionNames(subsystem: String) =
    authAction.async {
      val resp: Future[List[String]] = appActor ? (GetVersionNames(subsystem, _))
      resp.map(names => Ok(Json.toJson(names)))
    }

  /**
   * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
   */
  def getIcdNames =
    Action.async {
      val resp: Future[List[IcdName]] = appActor ? GetIcdNames
      resp.map(names => Ok(Json.toJson(names)))
    }

  /**
   * Gets a list of versions for the ICD from subsystem to target subsystem
   */
  def getIcdVersions(subsystem: String, target: String) =
    authAction.async {
      val resp: Future[List[IcdVersionInfo]] = appActor ? (GetIcdVersions(subsystem, target, _))
      resp.map(list => Ok(Json.toJson(list)))
    }

  /**
   * Gets the difference between two subsystem versions
   */
  def diff(subsystem: String, versionsStr: String) =
    authAction.async {
      val resp: Future[List[DiffInfo]] = appActor ? (GetDiff(subsystem, versionsStr, _))
      resp.map(list => Ok(Json.toJson(list)))
    }

  /**
   * Returns OK(true) if this is a public icd web server (upload not allowed,publish allowed, password protected)
   */
  def isPublicServer =
    Action {
      val publicServer = configuration.get[Boolean]("icd.isPublicServer")
      Ok(Json.toJson(publicServer))
    }

  /**
   * Responds with the JSON for the PublishInfo for every subsystem
   */
  def getPublishInfo(maybeSubsystem: Option[String]) =
    authAction {
      val publishInfo = IcdGitManager.getPublishInfo(maybeSubsystem)
      Ok(Json.toJson(publishInfo))
    }

  /**
   * Checks if the given GitHub user and password are valid for publish
   */
  def checkGitHubCredentials() =
    authAction { implicit request =>
      val maybeGitHubCredentials = request.body.asJson.map(json => Json.fromJson[GitHubCredentials](json).get)
      if (maybeGitHubCredentials.isEmpty) {
        BadRequest("Missing POST data of type GitHubCredentials")
      }
      else {
        val gitHubCredentials = maybeGitHubCredentials.get
        try {
          IcdGitManager.checkGitHubCredentials(gitHubCredentials)
          Ok.as(JSON)
        }
        catch {
          case ex: TransportException =>
            Unauthorized(ex.getMessage)
          case ex: Exception =>
            ex.printStackTrace()
            BadRequest(ex.getMessage)
        }
      }
    }

  private def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new mutable.StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  /**
   * Checks if the given user and password are valid for using the web app
   */
  def checkCredentials() =
    Action { implicit request =>
      val maybeCredentials = request.body.asJson.map(json => Json.fromJson[Credentials](json).get)
      if (maybeCredentials.isEmpty) {
        BadRequest("Missing POST data of type Credentials")
      }
      else {
        val credentials = maybeCredentials.get
        try {
          val sha = convertBytesToHex(MessageDigest.getInstance("SHA-256").digest(credentials.toString.getBytes))
          if (sha == expectedSha)
            Ok.as(JSON).withCookies(Cookie(cookieName, sha, sameSite = Some(Strict)))
          else
            Unauthorized("Wrong user name or password")
        }
        catch {
          case ex: Exception =>
            ex.printStackTrace()
            BadRequest(ex.getMessage)
        }
      }
    }

  /**
   * Checks if the user is already logged in (returns true if logged in)
   */
  def checkForCookie() =
    Action { implicit request =>
      request.cookies.get(cookieName) match {
        case Some(cookie) => Ok(Json.toJson(cookie.value == expectedSha))
        case None         => Ok(Json.toJson(false))
      }
    }

  /**
   * Log out of the web app
   */
  def logout() =
    authAction {
      Ok.as(JSON).discardingCookies(DiscardingCookie(cookieName))
    }

  /**
   * Publish the selected API (add an entry for the current commit of the master branch on GitHub)
   */
  def publishApi() =
    authAction.async { implicit request =>
      val maybePublishApiInfo = request.body.asJson.map(json => Json.fromJson[PublishApiInfo](json).get)
      if (maybePublishApiInfo.isEmpty) {
        Future(BadRequest("Missing POST data of type PublishApiInfo"))
      }
      else {
        val publishApiInfo = maybePublishApiInfo.get
        val problems       = IcdGitManager.validate(publishApiInfo.subsystem)
        if (problems.nonEmpty) {
          val msg =
            s"The version of ${publishApiInfo.subsystem} on GitHub did not pass validation: ${problems.map(_.toString).mkString(", ")}."
          Future(NotAcceptable(msg))
        }
        else {
          val resp: Future[Try[ApiVersionInfo]] = appActor ? (PublishApi(publishApiInfo, _))
          resp.map {
            case Success(info) =>
              Ok(Json.toJson(info))
            case Failure(ex) =>
              ex match {
                case ex: TransportException =>
                  Unauthorized(ex.getMessage)
                case ex: Exception =>
                  ex.printStackTrace()
                  BadRequest(ex.getMessage)
              }
          }
        }
      }
    }

  /**
   * Publish an ICD (add an entry to the icds file on the master branch of https://github.com/tmt-icd/ICD-Model-Files)
   */
  def publishIcd() =
    authAction.async { implicit request =>
      val maybePublishIcdInfo = request.body.asJson.map(json => Json.fromJson[PublishIcdInfo](json).get)
      if (maybePublishIcdInfo.isEmpty) {
        Future(BadRequest("Missing POST data of type PublishIcdInfo"))
      }
      else {
        val publishIcdInfo                    = maybePublishIcdInfo.get
        val resp: Future[Try[IcdVersionInfo]] = appActor ? (PublishIcd(publishIcdInfo, _))
        resp.map {
          case Success(info) =>
            Ok(Json.toJson(info))
          case Failure(ex) =>
            ex match {
              case ex: TransportException =>
                Unauthorized(ex.getMessage)
              case ex: Exception =>
                ex.printStackTrace()
                BadRequest(ex.getMessage)
            }
        }
      }
    }

  /**
   * Unublish the selected API (removes an entry from the file in the master branch on GitHub)
   */
  def unpublishApi() =
    authAction.async { implicit request =>
      val maybeUnpublishApiInfo = request.body.asJson.map(json => Json.fromJson[UnpublishApiInfo](json).get)
      if (maybeUnpublishApiInfo.isEmpty) {
        Future(BadRequest("Missing POST data of type UnpublishApiInfo"))
      }
      else {
        val unpublishApiInfo                          = maybeUnpublishApiInfo.get
        val resp: Future[Try[Option[ApiVersionInfo]]] = appActor ? (UnpublishApi(unpublishApiInfo, _))
        resp.map {
          case Success(info) =>
            info match {
              case Some(apiVersionInfo) =>
                Ok(Json.toJson(apiVersionInfo))
              case None =>
                NotFound(s"${unpublishApiInfo.subsystem}-${unpublishApiInfo.subsystemVersion} was not found")
            }
            Ok(Json.toJson(info))
          case Failure(ex) =>
            ex match {
              case ex: TransportException =>
                Unauthorized(ex.getMessage)
              case ex: Exception =>
                ex.printStackTrace()
                BadRequest(ex.getMessage)
            }
        }
      }
    }

  /**
   * Unpublish an ICD (remove an entry in the icds file on the master branch of https://github.com/tmt-icd/ICD-Model-Files)
   */
  def unpublishIcd() =
    authAction.async { implicit request =>
      val maybeUnpublishIcdInfo = request.body.asJson.map(json => Json.fromJson[UnpublishIcdInfo](json).get)
      if (maybeUnpublishIcdInfo.isEmpty) {
        Future(BadRequest("Missing POST data of type UnpublishIcdInfo"))
      }
      else {
        val unpublishIcdInfo                          = maybeUnpublishIcdInfo.get
        val resp: Future[Try[Option[IcdVersionInfo]]] = appActor ? (UnpublishIcd(unpublishIcdInfo, _))
        resp.map {
          case Success(info) =>
            info match {
              case Some(icdVersionInfo) =>
                Ok(Json.toJson(icdVersionInfo))
              case None =>
                NotFound(s"ICD version ${unpublishIcdInfo.icdVersion} was not found")
            }
            Ok(Json.toJson(info))
          case Failure(ex) =>
            ex match {
              case ex: TransportException =>
                Unauthorized(ex.getMessage)
              case ex: Exception =>
                ex.printStackTrace()
                BadRequest(ex.getMessage)
            }
        }
      }
    }

  /**
   * Updates the cache of published APIs and ICDs (in case new ones were published)
   */
  def updatePublished() =
    authAction {
      appActor ? UpdatePublished
      Ok.as(JSON)
    }

  /**
   * Gets optional information about the ICD between two subsystems
   * (from the <subsystem>-icd-model.conf files)
   *
   * @param subsystem           the source subsystem
   * @param maybeVersion        the source subsystem's version (default: current)
   * @param target              the target subsystem
   * @param maybeTargetVersion  the target subsystem's version
   */
  def icdModelList(
      subsystem: String,
      maybeVersion: Option[String],
      target: String,
      maybeTargetVersion: Option[String]
  ): Action[AnyContent] =
    authAction.async {
      val resp: Future[List[IcdModel]] = appActor ? (GetIcdModels(
        subsystem,
        maybeVersion,
        target,
        maybeTargetVersion,
        _
      ))
      resp.map(info => Ok(Json.toJson(info)))
    }

  /**
   * Returns the generated source code for the given subsystem/component API in the given language
   *
   * @param subsystem        the source subsystem
   * @param lang             the language to generate (scala, java, typescript, python)
   * @param className        the top level class name to generate
   * @param maybeVersion     the source subsystem's version (default: current)
   * @param maybeComponent   optional component (default: all in subsystem)
   * @param maybePackageName optional package name for generated scala/java code
   */
  def generate(
      subsystem: String,
      lang: String,
      className: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybePackageName: Option[String]
  ) =
    Action.async { implicit request =>
      val resp: Future[Option[String]] = appActor ? (
        Generate(
          subsystem,
          lang,
          className,
          maybeVersion,
          maybeComponent,
          maybePackageName,
          _
        )
      )
      resp.map {
        case Some(s) =>
          Ok(s).as("text/plain")
        case None =>
          NotFound
      }
    }

  /**
   * Query the database for the FITS Dictionary and tags
   */
  def fitsDictionary(
      subsystem: Option[String],
      maybeComponent: Option[String]
  ) =
    authAction.async {
      val resp: Future[FitsDictionary] = appActor ? (GetFitsDictionary(subsystem, maybeComponent, _))
      resp.map(info => Ok(Json.toJson(info)))
    }

  /**
   * Query the database for the contents of the OpenApi JSON description for the given HTTP service.
   *
   * @param subsystem the subsystem that provides the service
   * @param component the component name
   * @param service the service name
   * @param version optional version for the subsystem
   * @param paths optional paths to include in the OpenApi result (in the format: method:path,method:path,...)
   *
   * @return the OpenAPI JSON string for the service
   */
  def openApi(
      subsystem: String,
      component: String,
      service: String,
      version: Option[String],
      paths: Option[String]
  ) =
    authAction.async {
      val pathList = paths
        .map(URLDecoder.decode(_, "UTF-8"))
        .map(_.split(',').toList.flatMap { s =>
          s.split(':') match {
            case Array(method, path) => Some(ServicePath(method, path))
            case _                   => None
          }
        })
        .getOrElse(Nil)
      val resp: Future[Option[String]] = appActor ? (GetOpenApi(subsystem, component, service, version, pathList, _))
      resp.map {
        case Some(openApi) => Ok(Json.parse(openApi))
        case None          => NotFound
      }
    }

}
