package icd.web.client

import icd.web.shared.PublishInfo
import play.api.libs.json._

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

object IcdUtil {
  import icd.web.shared.JsonSupport._

  /**
   * Gets information about the published state of the given (or all of the) subsystems
   * @param maybeSubsystem restrict results to given subsystem related
   * @param mainContent used for error messages
   * @return list of publish info for the given (or all) subsystem
   */
  def getPublishInfo(maybeSubsystem: Option[String], mainContent: MainContent): Future[List[PublishInfo]] = {
    Fetch
      .get(ClientRoutes.getPublishInfo(maybeSubsystem))
      .map { text =>
        Json.fromJson[Array[PublishInfo]](Json.parse(text)) match {
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
