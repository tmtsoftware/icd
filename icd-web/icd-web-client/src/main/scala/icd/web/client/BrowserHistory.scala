package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import upickle._


sealed trait ViewType
case object ComponentView extends ViewType
case object UploadView extends ViewType
case object HtmlView extends ViewType
case object PdfView extends ViewType

/**
 * Object used to keep track of browser history for back button
 *
 * @param sourceSubsystem subsystem selected in the left box
 * @param targetSubsystem subsystem selected in the right box
 * @param sourceComponents selected left (source) components
 * @param targetComponents selected right (target) components
 * @param filterChecked true if filter checkbox is checked
 * @param viewType indicates the type of data being displayed
 */
case class BrowserHistory(sourceSubsystem: Option[String], targetSubsystem: Option[String],
                          sourceComponents: List[String], targetComponents: List[String],
                          filterChecked: Boolean,
                          viewType: ViewType) {

   // Pushes the current application history state
  def pushState(): Unit = {
    val json = write(this)
    dom.history.pushState(json, dom.document.title, dom.document.documentURI)
  }
}

object BrowserHistory {

  // Gets  BrowserHistory from the event
  def popState(e: PopStateEvent): BrowserHistory = {
    read[BrowserHistory](e.state.toString)
  }
}
