package icd.web.client

import icd.web.shared.PublishInfo
import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object IcdUtil {
  import icd.web.shared.JsonSupport._

  /**
   * Gets information about the published state of the given (or all of the) subsystems
   * @param maybeSubsystem restrict results to given subsystem related
   * @param mainContent used for error messages
   * @return list of publish info for the given (or all) subsystem
   */
  def getPublishInfo(maybeSubsystem: Option[String], mainContent: MainContent): Future[List[PublishInfo]] = {
    Ajax
      .get(ClientRoutes.getPublishInfo(maybeSubsystem))
      .map { r =>
        Json.fromJson[Array[PublishInfo]](Json.parse(r.responseText)) match {
          case JsSuccess(ar: Array[PublishInfo], _: JsPath) =>
            ar.toList
          case e: JsError =>
            mainContent.displayInternalError(JsError.toJson(e).toString())
            Nil
        }
      }
//      .recover {
//        case ex =>
//          mainContent.displayInternalError(ex)
//          Nil
//      }
  }

}
