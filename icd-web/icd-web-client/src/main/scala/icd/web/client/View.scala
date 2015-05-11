package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scalatags.JsDom.TypedTag

/**
 * Manages the View menu
 */
object View {

  // Displays the HTML for the given ICD name
  private def displayIcdAsHtml(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      Main.setContent(s"ICD: $name", doc)
    }
  }

  // Gets the HTML for the named ICD
  private def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }

  // Called when the View ICD as HTML item is selected
  private def viewIcdAsHtml(e: dom.Event) = {
    LeftSidebar.uncheckAll()
    RightSidebar.uncheckAll()
    for(name <- Subsystem.getSelectedSubsystem) {
      displayIcdAsHtml(name)
    }
  }

  // Called when the View ICD as PDF item is selected
  private def viewIcdAsPdf(e: dom.Event) = {
    for(name <- Subsystem.getSelectedSubsystem) {
      dom.window.location.assign(Routes.icdPdf(name))
    }
  }

  // Returns the HTML markup for the view menu item
  private def markup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    li(cls := "dropdown")(
      a(cls := "dropdown-toggle", "data-toggle".attr := "dropdown", "View", b(cls := "caret")),
      ul(cls := "dropdown-menu")(
        li(a(onclick := viewIcdAsHtml _)("View ICD as HTML")),
        li(a(onclick := viewIcdAsPdf _)("View ICD as PDF"))
      )
    )
  }


  def init(): Unit = {
    Navbar.addItem(markup().render)
  }
}
