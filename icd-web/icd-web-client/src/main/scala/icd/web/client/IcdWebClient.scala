package icd.web.client

import org.scalajs.dom.Document
import upickle._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLSelectElement

import scala.concurrent.Future
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object IcdWebClient extends js.JSApp {

  // Displays the HTML for the given ICD name
  def displayIcd(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      dom.document.getElementById("content").innerHTML = doc
    }
  }

  // Gets the list of top level ICDs from the server
  def getIcdNames: Future[List[String]] = {
    Ajax.get(Routes.icdNames).map { r =>
      read[List[String]](r.responseText)
    }
  }

  // Gets the HTML for the named ICD
  def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }


  // Makes the Subsystem combobox
  def makeSubsystemDropDown(items: List[String]) = {
    import scalatags.JsDom.all._

    val idStr = "subsystem"
    val titleStr = "Subsystem"
    val msg = "Select a subsystem"

    // called when an item is selected
    def subsystemSelected = (e: dom.Event) => {
      val sel = e.target.asInstanceOf[HTMLSelectElement]
      println(s"You selected ${sel.value}")
      // remove empty option
      if (sel.options.length > 1 && sel.options(0).value == msg)
        sel.remove(0)
      displayIcd(sel.value)
    }

    val list = msg :: items
    div(cls := "btn-group")(
      label(`for` := idStr)(titleStr),
      select(id := idStr, onchange := subsystemSelected)(
        list.map(s => option(value := s)(s)): _*
      )
    )
  }

  // Main entry point
  def main(): Unit = {
    getIcdNames.map(init)
  }

  // Initialize the main layout
  def init(icdNames: List[String]): Unit = {
    val subsystemDropDown = makeSubsystemDropDown(icdNames)
    dom.document.getElementById("navbarItem1").appendChild(subsystemDropDown.render)
  }
}
