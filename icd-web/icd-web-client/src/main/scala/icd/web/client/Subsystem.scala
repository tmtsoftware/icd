package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLSelectElement
import upickle._

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages the subsystem combobox
 */
@JSExport
object Subsystem {

  private def sel = $id("subsystem").asInstanceOf[HTMLSelectElement]
  private val msg = "Select a subsystem"

  // Gets the list of top level ICDs from the server
  private def getIcdNames: Future[List[String]] = {
    Ajax.get(Routes.icdNames).map { r =>
      read[List[String]](r.responseText)
    }
  }

  // Gets the currently selected subsystem name
  def getSelectedSubsystem: Option[String] =
    sel.value match {
      case `msg` => None
      case s => Some(s)
    }

  // called when an item is selected
  private def subsystemSelected(e: dom.Event) {
    // remove empty option
    if (sel.options.length > 1 && sel.options(0).value == msg)
      sel.remove(0)

    // XXX TODO: Display table with pub/sub info?, update sidebar
    getSelectedSubsystem.foreach(s => println(s"You selected $s"))
  }

  // Update the Subsystem combobox options
  private def updateSubsystemOptions(items: List[String]) = {
    import scalatags.JsDom.all._

    val list = msg :: items
    while (sel.options.length != 0) {
      sel.remove(0)
    }
    for (s <- list) {
      println(s"XXX add subsys opn: $s")
      sel.add(option(value := s)(s).render)
    }
  }

  // Updates the menu
  def update(): Unit = {
    getIcdNames.map(updateSubsystemOptions)
  }

  // Initialize the subsystem combobox
  def init(): Unit = {
    update()
    sel.addEventListener("change", subsystemSelected _, useCapture = false)
  }

}
