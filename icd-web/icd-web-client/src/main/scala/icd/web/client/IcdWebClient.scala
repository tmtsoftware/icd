package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLAnchorElement

import scala.concurrent.Future
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

object IcdWebClient extends JSApp {

  // Displays the HTML for the given ICD name
  def displayIcdAsHtml(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      setContent(s"ICD: $name", doc)
    }
  }

  // Gets the HTML for the named ICD
  def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }

//  // Gets a link to the PDF for the selected ICD
  def getIcdPdfLink: String = Subsystem.getSelectedSubsystem match {
    case Some(name) => Routes.icdPdf(name)
    case None => "#"
  }

  // Called when the View ICD as HTML item is selected
  def viewIcdAsHtml(e: dom.Event) = {
    Sidebar.uncheckAll()
    for(name <- Subsystem.getSelectedSubsystem) {
      displayIcdAsHtml(name)
    }
  }

  // Main entry point
  @JSExport
  def init(settings: js.Dynamic): Unit = {
    val csrfToken = settings.csrfToken.toString
    val wsBaseUrl = settings.wsBaseUrl.toString
    val inputDirSupported = settings.inputDirSupported.toString == "true"

    Subsystem.init(wsBaseUrl)
    FileUpload.init(csrfToken, inputDirSupported)

    $id("viewIcdAsHtml").addEventListener("click", viewIcdAsHtml _, useCapture = false)

    // called when a new subsystem is selected to update some links
    def subsystemSelected(e: dom.Event): Unit = {
      val a = $id("viewIcdAsPdf").asInstanceOf[HTMLAnchorElement]
      a.href = getIcdPdfLink
    }
    Subsystem.sel.addEventListener("change", subsystemSelected _, useCapture = false)
  }


  // Main entry point (not used, see init() above)
  @JSExport
  def main(): Unit = {
  }
}
