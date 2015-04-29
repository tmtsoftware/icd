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

  var csrfToken: String = ""
  var wsBaseUrl: String = ""

  // Displays the HTML for the given ICD name
  def displayIcd(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      $id("content").innerHTML = doc
    }
  }

  // Gets the HTML for the named ICD
  def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }

  // Main entry point
  @JSExport
  def init(settings: js.Dynamic): Unit = {
    csrfToken = settings.csrfToken.toString
    wsBaseUrl = settings.wsBaseUrl.toString

    Subsystem.init(wsBaseUrl)
    FileUpload.init(csrfToken)
  }


  // Main entry point (not used)
  @JSExport
  def main(): Unit = {
  }
}
