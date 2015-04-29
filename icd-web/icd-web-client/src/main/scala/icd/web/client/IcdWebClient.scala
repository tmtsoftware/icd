package icd.web.client

import org.scalajs.dom.{FileList, Document}
import upickle._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}

import scala.concurrent.Future
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.{JSExport, JSExportDescendentObjects}

object IcdWebClient extends JSApp {

  // Displays the HTML for the given ICD name
  def displayIcdAsHtml(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      $id("contentTitle").textContent = s"ICD: $name"
      $id("content").innerHTML = doc
    }
  }

  // Displays the PDF for the given ICD name
  def displayIcdAsPdf(name: String): Unit = {
    // XXX TODO
  }

  // Gets the HTML for the named ICD
  def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }

  // Called when the View ICD as HTML item is selected
  def viewIcdAsHtml(e: dom.Event) = {
    for(name <- Subsystem.getSelectedSubsystem) {
      displayIcdAsHtml(name)
    }
  }

  // Called when the View ICD as PDF item is selected
  def viewIcdAsPdf(e: dom.Event) = {
    for(name <- Subsystem.getSelectedSubsystem) {
      displayIcdAsPdf(name)
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
    $id("viewIcdAsPdf").addEventListener("click", viewIcdAsPdf _, useCapture = false)

  }


  // Main entry point (not used)
  @JSExport
  def main(): Unit = {
  }
}
