package icd.web.client

import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom._
import play.api.libs.json._
import BrowserHistory._

object BrowserHistory {
  // JSON support

  import icd.web.shared.JsonSupport._

  implicit val viewTypeWrites: Writes[ViewType] =
    (v: ViewType) => JsString(v match {
      case ComponentView => "ComponentView"
      case IcdView => "IcdView"
      case UploadView => "UploadView"
      case VersionView => "VersionView"
    })
  implicit val viewTypeReads: Reads[ViewType] = {
    case JsString(s) => JsSuccess(s match {
      case "ComponentView" => ComponentView
      case "IcdView" => IcdView
      case "UploadView" => UploadView
      case "VersionView" => VersionView
    })
    case _ => JsError("bad ViewType")
  }
  implicit val browserHistoryFormat: OFormat[BrowserHistory] = Json.format[BrowserHistory]

  // Type of a view in the application, used to restore the view
  sealed trait ViewType

  // XXX TODO FIXME: Still needed?
  // Viewing components based on checkbox states in sidebar
  case object ComponentView extends ViewType

  // Viewing an ICD
  case object IcdView extends ViewType

  // Uploading ICD files
  case object UploadView extends ViewType

  //  // Publishing an API or ICD
  //  case object PublishView extends ViewType

  // Viewing the version history
  case object VersionView extends ViewType

  // Gets  BrowserHistory from the event
  def popState(e: PopStateEvent): Option[BrowserHistory] = {
    if (e.state == null) None
    else {
      Json.fromJson[BrowserHistory](Json.parse(e.state.toString)) match {
        case JsSuccess(h: BrowserHistory, _: JsPath) => Some(h)
        case _ => None
      }
    }
  }
}

/**
 * Object used to keep track of browser history for back button
 *
 * @param maybeSourceSubsystem optional source subsystem selected in the left box
 * @param maybeTargetSubsystem optional target subsystem selected in the right box
 * @param maybeIcd             the ICD with version, if one was selected
 * @param viewType             indicates the type of data being displayed
 * @param currentCompnent  optional current component
 */
case class BrowserHistory(
                           maybeSourceSubsystem: Option[SubsystemWithVersion],
                           maybeTargetSubsystem: Option[SubsystemWithVersion],
                           maybeIcd: Option[IcdVersion],
                           viewType: ViewType,
                           currentCompnent: Option[String]) {

  // Pushes the current application history state (Note that the title is ignored in some browsers)
  def pushState(): Unit = {
    val json = Json.toJson(this).toString()
    dom.window.history.pushState(json, dom.document.title, dom.document.documentURI)
  }

  // Replaces the current application history state (Note that the title is ignored in some browsers)
  def replaceState(): Unit = {
    val json = Json.toJson(this).toString()
    dom.window.history.replaceState(json, dom.document.title, dom.document.documentURI)
  }
}

