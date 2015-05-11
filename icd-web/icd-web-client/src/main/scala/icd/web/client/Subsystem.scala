package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLSelectElement
import upickle._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalatags.JsDom.TypedTag

/**
 * Manages the subsystem combobox
 */
object Subsystem {
  // The id of the select item
  val selectId = "subsystem"

  // Reference the file select item in the HTML document
  lazy val sel = $id(selectId).asInstanceOf[HTMLSelectElement]

  private val msg = "Select a subsystem"


  // HTML for the subsystem combobox
  private def markup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    li(
      a(
        label(`for` := selectId, title := "Subsystem"),
        select(id := selectId)(
          option(value := msg)(msg)
        )
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
    sel.value match {
      case `msg` => None
      case s => Some(s)
    }

  // called when an item is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (sel.options.length > 1 && sel.options(0).value == msg)
      sel.remove(0)

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
    while (sel.options.length != 0) {
      sel.remove(0)
    }
    for (s <- list) {
      sel.add(option(value := s)(s).render)
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
    sel.addEventListener("change", subsystemSelected _, useCapture = false)

    // Called when the DB is changed, for example after an upload/ingest
    def wsReceive(e: dom.Event) = {
      update()
    }

    // Arrange to be notified when DB changes, so we can update the combobox with the list of ICDs
    val socket = new dom.WebSocket(wsBaseUrl)
    socket.onmessage = wsReceive _
  }

}
