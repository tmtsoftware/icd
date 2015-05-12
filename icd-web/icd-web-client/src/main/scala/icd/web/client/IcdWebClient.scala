package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLStyleElement
import upickle._

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.TypedTag
import scalacss.Defaults._
import scalacss.ScalatagsCss._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Main class for the ICD web app.
 *
 * @param csrfToken server token used for file upload (for security)
 * @param wsBaseUrl web socket base URL
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
case class IcdWebClient(csrfToken: String, wsBaseUrl: String, inputDirSupported: Boolean) {

  val head = dom.document.head
  val body = dom.document.body

  // Page components
  val subsystem = Subsystem(subsystemSelected)
  val targetSubsystem = Subsystem(targetSubsystemSelected)
  val mainContent = MainContent()
  val components = Components(mainContent)
  val leftSidebar = Sidebar(components)
  val rightSidebar = Sidebar(components)

  val fileUpload = FileUpload(csrfToken = csrfToken, inputDirSupported = inputDirSupported,
    leftSidebar = leftSidebar, rightSidebar = rightSidebar, mainContent = mainContent)

  val viewMenu = ViewMenu(subsystem = subsystem, mainContent = mainContent,
    leftSidebar = leftSidebar, rightSidebar = rightSidebar)

  val navbar = Navbar()
  val layout = Layout()

  val subsystemListeners = List(subsystem.updateSubsystemOptions _, targetSubsystem.updateSubsystemOptions _)
  val subsystemNames = SubsystemNames(mainContent, wsBaseUrl, subsystemListeners)

  doLayout()

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    // Insert the components in the page
    body.appendChild(navbar)
    navbar.addItem(viewMenu)
    navbar.addItem(fileUpload)

    body.appendChild(layout)
    layout.addItem(leftSidebar)
    layout.addItem(mainContent)
    layout.addItem(rightSidebar)

    // Add source and target subsystem items to sidebars
    leftSidebar.addItem(subsystem.markup().render)
    rightSidebar.addItem(targetSubsystem.markup().render)
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(subsystem: String): Future[List[String]] = {
    Ajax.get(Routes.icdComponents(subsystem)).map { r =>
      read[List[String]](r.responseText)
    }.recover {
      case ex =>
        mainContent.displayInternalError(ex)
        Nil
    }
  }

  private def subsystemSelected(subsystem: String): Unit = {
    leftSidebar.clearComponents()
    mainContent.clearContent()
    getComponentNames(subsystem).foreach { names =>
      names.foreach(leftSidebar.addComponent)
    }
  }

  private def targetSubsystemSelected(subsystem: String): Unit = {
    rightSidebar.clearComponents()
    getComponentNames(subsystem).foreach { names =>
      names.foreach(rightSidebar.addComponent)
    }
  }

}

/**
 * Main entry object for the web app
 */
object IcdWebClient extends JSApp {

  /**
   * Main entry point from Play
   * @param settings a JavaScript object containing settings (see class IcdWebClient)
   * @return
   */
  @JSExport
  def init(settings: js.Dynamic) = {
    val csrfToken = settings.csrfToken.toString
    val wsBaseUrl = settings.wsBaseUrl.toString
    val inputDirSupported = settings.inputDirSupported.toString == "true"
    IcdWebClient(csrfToken, wsBaseUrl, inputDirSupported)
  }

  // Main entry point (not used, see init() above)
  @JSExport
  override def main(): Unit = {
  }
}
