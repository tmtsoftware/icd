package icd.web.client

import play.api.libs.json.*

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import SubsystemNames.*

object SubsystemNames {
  // Type of a listener for changes in the list of subsystem names
  type Listener = List[String] => Future[Unit]
}

/**
 * Manages getting the list of subsystem names from the server and notifying listeners
 */
case class SubsystemNames(mainContent: MainContent, listener: Listener) {

  // Initialize by requesting the list of subsystem names and then updating the menus
  showBusyCursorWhile(update())

  // Gets the list of subsystems from the server
  private def getSubsystemNames: Future[List[String]] = {
    Fetch
      .get(ClientRoutes.subsystems)
      .map { text =>
        Json.fromJson[List[String]](Json.parse(text)).get
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
