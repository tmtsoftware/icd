package icd.web.client

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.TypedTag

@JSExport
object JsStuff {
  //    $(this).parents('.btn-group').find('.dropdown-toggle').html(selText+' <span class="caret"></span>');

  def itemSelected(s: String): Unit = {
    println(s"You selected $s")
  }
}

object IcdWebClient extends js.JSApp {
  def main(): Unit = {
    import scalatags.JsDom.all._

    val navbar = div(cls := "navbar navbar-inverse navbar-fixed-top", role := "navigation")(
      div(cls := "container-fluid")(
        div(cls := "navbar-header")(
          a(cls := "navbar-brand", href := "XXX TODO")("ICD Database")
        )
      )
    )


    // XXX TODO FIXME
    def makeDropDown(title: String, list: List[String]) = {

      div(cls:="btn-group")(
        a(cls:="btn btn-default dropdown-toggle btn-select", href:="#")(
          title + " ",
          span(cls:="caret")
        ),
        ul(cls:="dropdown-menu")(
          list.map(s => li(a(href:="#", onclick := s"icd.web.client.JsStuff.itemSelected($s)")(s))):_*
        )
      )
    }

    val subsystemDropDown = makeDropDown("TMT Subsystem", List("XXX1", "XXX2"))

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
