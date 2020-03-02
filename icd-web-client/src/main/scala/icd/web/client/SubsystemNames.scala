package icd.web.client

import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import SubsystemNames._

object SubsystemNames {
  // Type of a listener for changes in the list of subsystem names
  type Listener = List[String] => Future[Unit]
}

/**
 * Manages getting the list of subsystem names from the server and notifying listeners
 */
case class SubsystemNames(mainContent: MainContent, listener: Listener) {

  // Initialize by requesting the list of subsystem names and then updating the menus
  update()

  // Gets the list of subsystems from the server
  private def getSubsystemNames: Future[List[String]] = {
    Ajax
      .get(ClientRoutes.subsystems)
      .map { r =>
        Json.fromJson[List[String]](Json.parse(r.responseText)).get
      }
      .recover {
        case ex =>
          mainContent.displayInternalError(ex)
          Nil
      }
  }

  private def notifyListener(items: List[String]): Future[Unit] = {
    listener(items)
  }

  // Updates the menu
  def update(): Future[Unit] = {
    getSubsystemNames.flatMap(notifyListener)
  }
}
