package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax
import upickle._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._

/**
 * Manages the subsystem combobox
 */
object Subsystem {
  private val msg = "Select a subsystem"

  val selectItem = select()(
    option(value := msg)(msg)
  ).render

  // HTML for the subsystem combobox
  private def markup(): TypedTag[Element] = {
    li(
      a(
        label("Subsystem ", selectItem)
      )
    )
  }

  // Gets the list of top level ICDs from the server
  private def getIcdNames: Future[List[String]] = {
    Ajax.get(Routes.icdNames).map { r =>
      read[List[String]](r.responseText)
    }.recover {
      case ex =>
        Main.displayInternalError(ex)
        Nil
    }
  }

  // Gets the list of subcomponents for the selected ICD
  private def getComponentNames(icdName: String): Future[List[String]] = {
    Ajax.get(Routes.icdComponents(icdName)).map { r =>
      read[List[String]](r.responseText)
    }.recover {
      case ex =>
        Main.displayInternalError(ex)
        Nil
    }
  }

  // Gets the currently selected subsystem name
  def getSelectedSubsystem: Option[String] =
    selectItem.value match {
      case `msg` => None
      case subsystemName => Some(subsystemName)
    }

  // called when an item is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (selectItem.options.length > 1 && selectItem.options(0).value == msg)
      selectItem.remove(0)

    LeftSidebar.clearComponents()
    Main.clearContent()

    getSelectedSubsystem.foreach { subsystem =>
      LeftSidebar.addComponent(subsystem)
      getComponentNames(subsystem).foreach { names =>
        names.foreach(LeftSidebar.addComponent)
      }
    }
  }

  // Update the Subsystem combobox options
  private def updateSubsystemOptions(items: List[String]): Unit = {
    import scalatags.JsDom.all._

    val list = msg :: items
    while (selectItem.options.length != 0) {
      selectItem.remove(0)
    }
    for (s <- list) {
      selectItem.add(option(value := s)(s).render)
    }
  }

  // Updates the menu
  def update(): Unit = {
    getIcdNames.map(updateSubsystemOptions)
  }

  // Initialize the subsystem combobox
  def init(wsBaseUrl: String): Unit = {
    Navbar.addItem(markup().render)
    update()
    selectItem.addEventListener("change", subsystemSelected _, useCapture = false)

    // Called when the DB is changed, for example after an upload/ingest
    def wsReceive(e: dom.Event) = {
      update()
    }

    // Arrange to be notified when DB changes, so we can update the combobox with the list of ICDs
    val socket = new dom.WebSocket(wsBaseUrl)
    socket.onmessage = wsReceive _
  }

}
