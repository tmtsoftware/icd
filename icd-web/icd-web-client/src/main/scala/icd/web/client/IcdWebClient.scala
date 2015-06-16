package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.PopStateEvent
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
import BrowserHistory._

/**
 * Main class for the ICD web app.
 *
 * @param csrfToken server token used for file upload (for security)
 * @param wsBaseUrl web socket base URL
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
case class IcdWebClient(csrfToken: String, wsBaseUrl: String, inputDirSupported: Boolean) {

  private val head = dom.document.head
  private val body = dom.document.body

  // Page components
  private val subsystem = Subsystem(SourceSubsystemListener)
  private val targetSubsystem = Subsystem(TargetSubsystemListener, labelStr = "Target", msg = "All", removeMsg = false)
  private val mainContent = MainContent()
  private val components = Components(mainContent, componentLinkSelected())
  private val sidebar = Sidebar(LeftSidebarListener)

  private val fileUpload = FileUpload(csrfToken, inputDirSupported = inputDirSupported, uploadSelected())
  private val fileUploadDialog = FileUploadDialog(csrfToken, inputDirSupported)

  private val viewMenu = ViewMenu(
    viewAsHtml = viewIcdAsHtml(),
    viewAsPdf = viewIcdAsPdf(),
    showVersionHistory = showVersionHistory())

  private val navbar = Navbar()
  private val layout = Layout()

  private val versionHistory = VersionHistory(mainContent)

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemListeners = List(subsystem.updateSubsystemOptions _, targetSubsystem.updateSubsystemOptions _)
  SubsystemNames(mainContent, wsBaseUrl, subsystemListeners)

  dom.window.onpopstate = popState _

  // Note: This prevents leaving the web app with with back button
  pushState(viewType = ComponentView)

  doLayout()

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    // Insert the components in the page
    navbar.addItem(subsystem)
    navbar.addItem(targetSubsystem)
    navbar.addItem(viewMenu)
    navbar.addItem(fileUpload)

    layout.addItem(sidebar)
    layout.addItem(mainContent)

    body.appendChild(navbar.markup())
    body.appendChild(layout.markup())
  }

  // Called when the Upload item is selected
  private def uploadSelected(saveHistory: Boolean = true)(): Unit = {
    mainContent.setContent("Upload ICD", fileUploadDialog)
    if (saveHistory) pushState(viewType = UploadView)
  }

  /**
   * Returns a list of the component names for the selected target subsystem,
   * or None if "All" is not selected in the combobox.
   * (If a target subsystem is selected, only components that communicate with it are displayed.)
   */
  private def getFilter: Future[Option[List[String]]] = {
    if (targetSubsystem.isDefault) Future.successful(None)
    else {
      getComponentNames(targetSubsystem.getSelectedSubsystem.get).map(Some(_))
    }
  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
    override def componentSelected(componentName: String, checked: Boolean): Unit = {
      getFilter.map { filter ⇒
        if (checked)
          components.addComponent(componentName, filter,
            subsystem.getSelectedSubsystem.get, targetSubsystem.getSelectedSubsystem)
        else
          components.removeComponentInfo(componentName)

        pushState(viewType = ComponentView)
      }
    }

  }

  /**
   * Called when a component is selected in one of the publisher/subscriber/command tables.
   * @param componentName the component name
   */
  private def componentLinkSelected(saveHistory: Boolean = true)(componentName: String): Unit = {
    components.setComponent(componentName)
    if (saveHistory) pushState(viewType = ComponentLinkView, linkComponent = Some(componentName))
  }

  /**
   * Push the current app state for the browser history
   */
  private def pushState(viewType: ViewType, linkComponent: Option[String] = None): Unit = {
    val hist = BrowserHistory(
      subsystem.getSelectedSubsystem,
      targetSubsystem.getSelectedSubsystem,
      sidebar.getSelectedComponents,
      viewType = viewType,
      linkComponent = linkComponent)
    hist.pushState()
  }

  /**
   * Called when the user presses the Back button in the browser
   */
  private def popState(e: PopStateEvent): Unit = {
    BrowserHistory.popState(e).foreach { hist ⇒
      e.preventDefault()
      // Make sure to wait for futures to complete, so things happen in the right order
      for {
        _ ← subsystem.setSelectedSubsystem(hist.sourceSubsystem, saveHistory = false)
        _ ← targetSubsystem.setSelectedSubsystem(hist.targetSubsystem, saveHistory = false)
      } {
        sidebar.setSelectedComponents(hist.sourceComponents)
        hist.viewType match {
          case UploadView        ⇒ uploadSelected(saveHistory = false)()
          case HtmlView          ⇒ viewIcdAsHtml(saveHistory = false)()
          case PdfView           ⇒ viewIcdAsPdf(saveHistory = false)()
          case VersionView       ⇒ showVersionHistory(saveHistory = false)()
          case ComponentView     ⇒ updateComponentDisplay()
          case ComponentLinkView ⇒ hist.linkComponent.foreach(componentLinkSelected(saveHistory = false))
        }
      }
    }
  }

  /**
   * Updates the main display to match the selected components
   * @return a future indicating when the changes are done
   */
  private def updateComponentDisplay(): Future[Unit] = {
    getFilter.flatMap { filter ⇒
      mainContent.clearContent()
      val sub = subsystem.getSelectedSubsystem.get
      val targetOpt = targetSubsystem.getSelectedSubsystem
      components.addComponents(sidebar.getSelectedComponents, filter, sub, targetOpt)
    }
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(subsystem: String): Future[List[String]] = {
    Ajax.get(Routes.icdComponents(subsystem)).map { r ⇒
      read[List[String]](r.responseText)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
        Nil
    }
  }

  // Called when the source subsystem combobox selection is changed
  private object SourceSubsystemListener extends SubsystemListener {
    override def subsystemSelected(subsystemOpt: Option[String], saveHistory: Boolean): Future[Unit] = {
      sidebar.clearComponents()
      mainContent.clearContent()
      subsystemOpt match {
        case Some(selectedSubsystem) ⇒
          getComponentNames(selectedSubsystem).flatMap { names ⇒ // Future!
            names.foreach(sidebar.addComponent)
            updateComponentDisplay().map { _ ⇒
              if (saveHistory) pushState(viewType = ComponentView)
            }
          }
        case None ⇒ Future.successful()
      }
    }

  }

  // Called when the target subsystem combobox selection is changed
  private object TargetSubsystemListener extends SubsystemListener {
    override def subsystemSelected(subsystemOpt: Option[String], saveHistory: Boolean): Future[Unit] = {
      updateComponentDisplay().map { _ ⇒
        if (saveHistory) pushState(viewType = ComponentView)
      }
    }
  }

  // Called when the View ICD as HTML item is selected
  private def viewIcdAsHtml(saveHistory: Boolean = true)(): Unit = {
    // Displays the HTML for the given ICD name
    def displayIcdAsHtml(name: String): Unit = {
      getIcdHtml(name).map { doc ⇒
        mainContent.setContent(s"API: $name", doc)
        if (saveHistory) pushState(viewType = HtmlView)
      }
    }

    // Gets the HTML for the named ICD
    def getIcdHtml(name: String): Future[String] = {
      Ajax.get(Routes.icdHtml(name)).map { r ⇒
        r.responseText
      }
    }

    for (name ← subsystem.getSelectedSubsystem) {
      displayIcdAsHtml(name)
    }
  }

  // Called when the View ICD as PDF item is selected
  private def viewIcdAsPdf(saveHistory: Boolean = true)(): Unit = {
    for (name ← subsystem.getSelectedSubsystem) {
      dom.window.location.assign(Routes.icdPdf(name))
      if (saveHistory) pushState(viewType = PdfView)
    }
  }

  // Called when the "Show ICD Version History" menu item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    for (name ← subsystem.getSelectedSubsystem) {
      versionHistory.setSubsystem(name)
      mainContent.setContent(s"Version History for $name", versionHistory)
      if (saveHistory) pushState(viewType = VersionView)
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
