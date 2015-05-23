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
  private val subsystem = Subsystem(subsystemSelected)
  private val targetSubsystem = Subsystem(targetSubsystemSelected,
    labelStr = "Target", msg = "All", removeMsg = false, showFilterCheckbox = true)
  private val mainContent = MainContent()
  private val components = Components(mainContent, componentLinkSelected())
  private val leftSidebar = Sidebar(subsystem, componentSelected)
  private val rightSidebar = Sidebar(targetSubsystem, targetComponentSelected)

  private val fileUpload = FileUpload(csrfToken, inputDirSupported = inputDirSupported, uploadSelected())
  private val fileUploadDialog = FileUploadDialog(csrfToken, inputDirSupported)

  private val viewMenu = ViewMenu(viewIcdAsHtml = viewIcdAsHtml(), viewIcdAsPdf = viewIcdAsPdf())

  private val navbar = Navbar()
  private val layout = Layout()

  private val subsystemListeners = List(subsystem.updateSubsystemOptions _, targetSubsystem.updateSubsystemOptions _)
  private val subsystemNameUpdater = SubsystemNames(mainContent, wsBaseUrl, subsystemListeners)

  private val versionHistory = VersionHistory(mainContent)

  dom.window.onpopstate = popState _
  pushState(viewType = ComponentView)
  doLayout()

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    // Insert the components in the page
    navbar.addItem(viewMenu)
    navbar.addItem(fileUpload)

    layout.addItem(leftSidebar)
    layout.addItem(mainContent)
    layout.addItem(rightSidebar)

    body.appendChild(navbar)
    body.appendChild(layout)
    body.appendChild(versionHistory)
  }

  // Called when the Upload item is selected
  private def uploadSelected(saveHistory: Boolean = true)(): Unit = {
    mainContent.setContent("Upload ICD", fileUploadDialog)
    if (saveHistory) pushState(viewType = UploadView)
  }

  /**
   * Returns a list of the component names selected in the target sidebar, if the Filter checkbox is checked
   * and "All" is not selected in the target subsystem combobox
   */
  private def getFilter = if (targetSubsystem.isFilterSelected && !targetSubsystem.isDefault)
    Some(rightSidebar.getSelectedComponents)
  else None

  /**
   * Called when a component in the left sidebar is checked or unchecked
   * @param componentName the component name
   * @param checked true if the checkbox is checked
   */
  private def componentSelected(componentName: String, checked: Boolean): Unit = {
    val filter = getFilter
    if (checked)
      components.addComponent(componentName, filter)
    else
      components.removeComponent(componentName)

    pushState(viewType = ComponentView)
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
      leftSidebar.getSelectedComponents,
      rightSidebar.getSelectedComponents,
      filterChecked = targetSubsystem.isFilterSelected,
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
      subsystem.setSelectedSubsystem(hist.sourceSubsystem)
      targetSubsystem.setFilterSelected(hist.filterChecked)
      targetSubsystem.setSelectedSubsystem(hist.targetSubsystem)
      leftSidebar.setSelectedComponents(hist.sourceComponents)
      rightSidebar.setSelectedComponents(hist.targetComponents)

      hist.viewType match {
        case UploadView        ⇒ uploadSelected(saveHistory = false)()
        case HtmlView          ⇒ viewIcdAsHtml(saveHistory = false)()
        case PdfView           ⇒ viewIcdAsPdf(saveHistory = false)()
        case ComponentView     ⇒ updateComponentDisplay()
        case ComponentLinkView ⇒ hist.linkComponent.foreach(componentLinkSelected(saveHistory = false))
      }
    }
  }

  /**
   * Updates the main display to match the selected components
   */
  private def updateComponentDisplay(): Unit = {
    val filter = getFilter
    mainContent.clearContent()
    leftSidebar.getSelectedComponents.foreach(components.addComponent(_, filter))
    if (!targetSubsystem.isFilterSelected) {
      rightSidebar.getSelectedComponents.foreach(components.addComponent(_, None))
    }
  }

  /**
   * Called when a component in the right sidebar is checked or unchecked
   * @param componentName the component name
   * @param checked true if the checkbox is checked
   */
  private def targetComponentSelected(componentName: String, checked: Boolean): Unit = {
    if (targetSubsystem.isFilterSelected) {
      updateComponentDisplay()
    } else {
      if (checked)
        components.addComponent(componentName, None)
      else
        components.removeComponent(componentName)
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

  // Called when the source (left) subsystem combobox selection is changed
  private def subsystemSelected(subsystemOpt: Option[String], notUsed: Boolean): Unit = {
    leftSidebar.clearComponents()
    mainContent.clearContent()
    subsystemOpt.foreach { subsystem ⇒
      getComponentNames(subsystem).foreach { names ⇒ // Future!
        names.foreach(leftSidebar.addComponent)
        updateComponentDisplay()
        pushState(viewType = ComponentView)
      }
      versionHistory.setSubsystem(subsystem)
    }
  }

  // Called when the target (right) subsystem combobox selection is changed
  private def targetSubsystemSelected(subsystemOpt: Option[String], filterChecked: Boolean): Unit = {
    rightSidebar.clearComponents()
    subsystemOpt.foreach { subsystem ⇒
      getComponentNames(subsystem).foreach { names ⇒ // Future!
        names.foreach(rightSidebar.addComponent)
        updateComponentDisplay()
        pushState(viewType = ComponentView)
      }
    }
  }

  // Called when the View ICD as HTML item is selected
  private def viewIcdAsHtml(saveHistory: Boolean = true)() = {
    // Displays the HTML for the given ICD name
    def displayIcdAsHtml(name: String): Unit = {
      getIcdHtml(name).map { doc ⇒
        mainContent.setContent(s"ICD: $name", doc)
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
  private def viewIcdAsPdf(saveHistory: Boolean = true)() = {
    for (name ← subsystem.getSelectedSubsystem) {
      dom.window.location.assign(Routes.icdPdf(name))
      if (saveHistory) pushState(viewType = PdfView)
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
