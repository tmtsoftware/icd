package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import upickle.default._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import SubsystemNames._

object SubsystemNames {
  // Type of a listener for changes in the list of subsystem names
  type Listener = List[String] => Unit
}

/**
 * Manages getting the list of subsystem (top level ICD) names from the server and notifying listeners
 */
case class SubsystemNames(mainContent: MainContent, wsBaseUrl: String, listener: Listener) {

  // Initialize by requesting the list of subsystem names and then listening on a websocket for
  // future updates to the list
  update()

  // Arrange to be notified when DB changes, so we can update the combobox with the list of ICDs
  val socket = new dom.WebSocket(wsBaseUrl)
  socket.onmessage = wsReceive _

  // Gets the list of top level ICDs from the server
  private def getSubsystemNames: Future[List[String]] = {
    Ajax.get(Routes.subsystems).map { r =>
      read[List[String]](r.responseText)
    }.recover {
      case ex =>
        mainContent.displayInternalError(ex)
        Nil
    }
  }

  private def notifyListener(items: List[String]): Unit = {
    listener(items)
  }

  // Updates the menu
  def update(): Unit = {
    getSubsystemNames.map(notifyListener)
  }

  // Called when the DB is changed, for example after an upload/ingest
  private def wsReceive(e: dom.Event) = {
    update()
  }

}
