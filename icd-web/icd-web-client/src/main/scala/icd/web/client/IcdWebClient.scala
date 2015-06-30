package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.PopStateEvent
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLStyleElement
import shared.IcdVersion
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
import Subsystem._
import IcdChooser._
import Components._

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
  private val icdChooser = IcdChooser(IcdChooserListener)
  private val mainContent = MainContent()
  private val components = Components(mainContent, ComponentLinkSelectionHandler)
  private val sidebar = Sidebar(LeftSidebarListener)

  private val fileUploadItem = NavbarItem("Upload", uploadSelected())
  private val fileUploadDialog = FileUploadDialog(csrfToken, inputDirSupported)

  private val publishItem = NavbarItem("Publish", publishItemSelected())
  private val publishDialog = PublishDialog(subsystem, targetSubsystem, icdChooser)

  private val historyItem = NavbarItem("History", showVersionHistory())
  private val versionHistory = VersionHistory(mainContent)

  private val printItem = NavbarItem("Print", printContent)

  private val navbar = Navbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemListeners = List(
    subsystem.updateSubsystemOptions _,
    targetSubsystem.updateSubsystemOptions _)
  SubsystemNames(mainContent, wsBaseUrl, updateSubsystemOptions)

  icdChooser.updateIcdOptions()

  // Call popState() when the user presses the browser Back button
  dom.window.onpopstate = popState _

  // Initial browser state
  pushState(viewType = ComponentView)
  doLayout()

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    // Insert the components in the page
    navbar.addItem(subsystem)
    navbar.addItem(targetSubsystem)
    navbar.addItem(icdChooser)

    navbar.addItem(fileUploadItem)
    navbar.addItem(publishItem)
    navbar.addItem(historyItem)
    navbar.addItem(printItem)

    layout.addItem(sidebar)
    layout.addItem(mainContent)

    body.appendChild(navbar.markup())
    body.appendChild(layout.markup())
  }

  // Update the list of Subsystem options
  private def updateSubsystemOptions(items: List[String]): Unit = {
    subsystem.updateSubsystemOptions(items)
    subsystem.getSelectedSubsystem match {
      case Some(subsys) ⇒
        targetSubsystem.updateSubsystemOptions(items.filter(_ != subsys))
      case None ⇒
        targetSubsystem.updateSubsystemOptions(items)
    }
  }

  // Called when the Upload item is selected
  private def uploadSelected(saveHistory: Boolean = true)(): Unit = {
    mainContent.setContent(fileUploadDialog, "Upload ICD Files")
    if (saveHistory) pushState(viewType = UploadView)
  }

  // Called when the Publish item is selected
  private def publishItemSelected(saveHistory: Boolean = true)(): Unit = {
    val title = if (targetSubsystem.getSubsystemWithVersion.subsystemOpt.isDefined)
      "Publish ICD"
    else "Publish API"
    publishDialog.subsystemChanged()
    mainContent.setContent(publishDialog, title)
    if (saveHistory) pushState(viewType = PublishView)
  }

  /**
   * Returns a list of the component names for the selected target subsystem,
   * or None if "All" is selected in the combobox.
   * (If a target subsystem is selected, only components that communicate with it are displayed.)
   */
  private def getFilter: Future[Option[List[String]]] = {
    if (targetSubsystem.isDefault) Future.successful(None)
    else {
      getComponentNames(targetSubsystem.getSubsystemWithVersion).map(Some(_))
    }
  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
    override def componentSelected(componentName: String, checked: Boolean): Unit = {
      getFilter.map { filter ⇒
        if (checked)
          components.addComponent(componentName, filter,
            subsystem.getSubsystemWithVersion,
            targetSubsystem.getSubsystemWithVersion,
            icdChooser.getSelectedIcdVersion)
        else
          components.removeComponentInfo(componentName)

        pushState(viewType = ComponentView)
      }
    }
  }

  /**
   * Called when a component is selected in one of the publisher/subscriber/command tables.
   */
  private object ComponentLinkSelectionHandler extends ComponentListener {
    def componentSelected(link: ComponentLink): Unit = {
      val sv = SubsystemWithVersion(Some(link.subsystem), None) // XXX where to get version?
      components.setComponent(sv, link.compName)
      pushState(viewType = ComponentLinkView, linkComponent = Some(link))

    }
  }

  /**
   * Push the current app state for the browser history
   */
  private def pushState(viewType: ViewType, linkComponent: Option[ComponentLink] = None): Unit = {
    val hist = BrowserHistory(
      subsystem.getSubsystemWithVersion,
      targetSubsystem.getSubsystemWithVersion,
      icdChooser.getSelectedIcdVersion,
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
        _ ← subsystem.setSubsystemWithVersion(hist.sourceSubsystem, saveHistory = false)
        _ ← targetSubsystem.setSubsystemWithVersion(hist.targetSubsystem, saveHistory = false)
        _ ← icdChooser.setIcdWithVersion(hist.icdOpt, saveHistory = false)
      } {
        val changed = sidebar.setSelectedComponents(hist.sourceComponents)
        hist.viewType match {
          case UploadView    ⇒ uploadSelected(saveHistory = false)()
          case PublishView   ⇒ publishItemSelected(saveHistory = false)()
          //          case HtmlView      ⇒ viewIcdAsHtml(saveHistory = false)()
          //          case PdfView       ⇒ viewIcdAsPdf(saveHistory = false)()
          case VersionView   ⇒ showVersionHistory(saveHistory = false)()
          case ComponentView ⇒ updateComponentDisplay()
          case IcdView       ⇒ updateComponentDisplay()
          case ComponentLinkView ⇒ hist.linkComponent.foreach { link ⇒
            val sv = SubsystemWithVersion(Some(link.subsystem), None) // XXX where to get version?
            components.setComponent(sv, link.compName)
          }
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
      val sub = subsystem.getSubsystemWithVersion
      val targetOpt = targetSubsystem.getSubsystemWithVersion
      val icdOpt = icdChooser.getSelectedIcdVersion
      components.addComponents(sidebar.getSelectedComponents, filter, sub, targetOpt, icdOpt)
    }
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(sv: SubsystemWithVersion): Future[List[String]] = {
    if (sv.subsystemOpt.isDefined) {
      val path = Routes.components(sv.subsystemOpt.get, sv.versionOpt)
      Ajax.get(path).map { r ⇒
        read[List[String]](r.responseText)
      }.recover {
        case ex ⇒
          mainContent.displayInternalError(ex)
          Nil
      }
    } else Future.successful(Nil)
  }

  private object SourceSubsystemListener extends SubsystemListener {
    // Called when the source subsystem (or version) combobox selection is changed
    override def subsystemSelected(sv: SubsystemWithVersion, saveHistory: Boolean): Future[Unit] = {
      icdChooser.setIcdWithVersion(None, notifyListener = false, saveHistory = false)
      sidebar.clearComponents()
      mainContent.clearContent()
      sv.subsystemOpt match {
        case Some(selectedSubsystem) ⇒
          // Target subsystem can't be the same as the selected subsystem
          targetSubsystem.setAllOptionsEnabled()
          targetSubsystem.disableOption(selectedSubsystem)
          for {
            names ← getComponentNames(sv)
            _ ← Future.successful {
              names.foreach(sidebar.addComponent)
            }
            _ ← updateComponentDisplay()
          } yield if (saveHistory) pushState(viewType = ComponentView)
        case None ⇒
          targetSubsystem.setAllOptionsEnabled()
          Future.successful()
      }
    }
  }

  private object TargetSubsystemListener extends SubsystemListener {
    // Called when the target subsystem or version combobox selection is changed
    override def subsystemSelected(targSv: SubsystemWithVersion, saveHistory: Boolean): Future[Unit] = {
      // Target subsystem can't be the same as the selected subsystem
      targSv.subsystemOpt match {
        case Some(selectedTarget) ⇒
          subsystem.setAllOptionsEnabled()
          subsystem.disableOption(selectedTarget)
        case None ⇒
          subsystem.setAllOptionsEnabled()
      }

      icdChooser.setIcdWithVersion(None, notifyListener = false, saveHistory = false)
      updateComponentDisplay().map { _ ⇒
        if (saveHistory) pushState(viewType = ComponentView)
      }
    }
  }

  private object IcdChooserListener extends IcdListener {
    // Called when the ICD (or ICD version) combobox selection is changed
    override def icdSelected(icdVersionOpt: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit] = {
      icdVersionOpt match {
        case Some(icdVersion) ⇒
          val sv = SubsystemWithVersion(Some(icdVersion.subsystem), Some(icdVersion.subsystemVersion))
          val tv = SubsystemWithVersion(Some(icdVersion.target), Some(icdVersion.targetVersion))
          for {
            _ ← subsystem.setSubsystemWithVersion(sv, notifyListener = false, saveHistory = false)
            _ ← targetSubsystem.setSubsystemWithVersion(tv, notifyListener = false, saveHistory = false)
          } yield {
            // Prevent selecting the same subsystem for source and target
            subsystem.setAllOptionsEnabled()
            subsystem.disableOption(icdVersion.target)
            targetSubsystem.setAllOptionsEnabled()
            targetSubsystem.disableOption(icdVersion.subsystem)
            // Update the display
            sidebar.clearComponents()
            mainContent.clearContent()
            getComponentNames(sv).flatMap { names ⇒ // Future!
              names.foreach(sidebar.addComponent)
              updateComponentDisplay().map { _ ⇒
                if (saveHistory) pushState(viewType = IcdView)
              }
            }
          }
        case None ⇒ Future.successful()
      }
    }
  }

  // Called when the "Show ICD Version History" menu item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    icdChooser.getSelectedIcd match {
      case Some(icdName) ⇒
        versionHistory.setIcd(icdName)
        mainContent.setContent(versionHistory, s"ICD Version History: ${
          icdName.subsystem
        } to ${
          icdName.target
        }")
        if (saveHistory) pushState(viewType = VersionView)
      case None ⇒ subsystem.getSelectedSubsystem match {
        case Some(name) ⇒
          versionHistory.setSubsystem(name)
          mainContent.setContent(versionHistory, s"Subsystem API Version History: $name")
          if (saveHistory) pushState(viewType = VersionView)
        case None ⇒
      }
    }
  }

  // Opens the browser's print dialog
  private def printContent(): Unit = {
    dom.window.print()
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
