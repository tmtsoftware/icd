package icd.web.client

import upickle._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLSelectElement

import scala.concurrent.Future
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object IcdWebClient extends js.JSApp {

  // Gets the list of top level ICDs from the server
  def getIcdNames: Future[List[String]] = {
    Ajax.get(Routes.icdNames).map { r =>
      read[List[String]](r.responseText)
    }
  }

  // Makes the Subsystem combobox
  def makeSubsystemDropDown(idStr: String, titleStr: String, items: List[String]) = {
    import scalatags.JsDom.all._

    // called when an item is selected
    def subsystemSelected = (e: dom.Event) => {
      val sel = e.target.asInstanceOf[HTMLSelectElement]
      println(s"You selected ${sel.value}")
      // remove empty option
      if (sel.options.length > 1 && sel.options(0).value == "...")
        sel.remove(0)
    }

    val list = "..." :: items
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
    import scalatags.JsDom.all._

    val navbar = div(cls := "navbar navbar-inverse navbar-fixed-top", role := "navigation")(
      div(cls := "container-fluid")(
        div(cls := "navbar-header")(// XXX TODO add link to docs?
          a(cls := "navbar-brand", href := "#")("ICD Database")
        )
      )
    )

    val subsystemDropDown = makeSubsystemDropDown("subsystem", "TMT Subsystem", icdNames)

    val sidebar = div(cls := "col-sm-3 col-md-2 sidebar")(
      form(subsystemDropDown)
    )

    val content = div(id := "content")(
      p("XXX Content")
    )

    val pageHeader = h1(cls := "page-header", "XXX Title")

    val pageBody = div(cls := "container-fluid")(
      div(cls := "row")(
        sidebar,
        div(cls := "col-sm-9 col-sm-offset-3 col-md-10 col-md-offset-2 main")(
          pageHeader,
          content
        )
      )
    )

    val elem = dom.document.getElementById("content")
    elem.appendChild(navbar.render)
    elem.appendChild(pageBody.render)
  }
}
