package icd.web.client

import icd.web.shared.{SubsystemWithVersion, IcdVersion}
import org.scalajs.dom
import org.scalajs.dom.PopStateEvent
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLStyleElement
import play.api.libs.json._
import org.querki.jquery._

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
 * @param csrfToken         server token used for file upload (for security)
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
case class IcdWebClient(csrfToken: String, inputDirSupported: Boolean) {

  private val head = dom.document.head
  private val body = dom.document.body

  // Page components
  private val subsystem = Subsystem(SourceSubsystemListener)
  private val targetSubsystem = Subsystem(
    TargetSubsystemListener,
    labelStr = "Target", placeholderMsg = "All", enablePlaceholder = true
  )
  private val subsystemSwapper = SubsystemSwapper(swapSubsystems)
  private val expandToggler = ExpandToggler()
  private val icdChooser = IcdChooser(IcdChooserListener)
  private val mainContent = MainContent()
  private val components = Components(mainContent, ComponentLinkSelectionHandler)
  private val sidebar = Sidebar(LeftSidebarListener)

  //  private val publishItem = NavbarItem("Publish", publishItemSelected())
  //  private val publishDialog = PublishDialog(subsystem, targetSubsystem, icdChooser)

  private val historyItem = NavbarItem("History", showVersionHistory())
  private val versionHistory = VersionHistory(mainContent)

  private val pdfItem = NavbarItem("PDF", makePdf)

  private val navbar = Navbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemNames = SubsystemNames(mainContent, updateSubsystemOptions)

  private val fileUploadItem = NavbarItem("Upload", uploadSelected())
  private val fileUploadDialog = FileUploadDialog(subsystemNames, csrfToken, inputDirSupported)

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
    navbar.addItem(subsystemSwapper)
    navbar.addItem(targetSubsystem)
    navbar.addItem(icdChooser)

    navbar.addItem(fileUploadItem)
    //    navbar.addItem(publishItem)
    navbar.addItem(historyItem)
    navbar.addItem(pdfItem)
    navbar.addItem(expandToggler)

    layout.addItem(sidebar)
    layout.addItem(mainContent)

    body.appendChild(navbar.markup())
    body.appendChild(layout.markup())
  }

  // Update the list of Subsystem options
  private def updateSubsystemOptions(items: List[String]): Unit = {
    subsystem.updateSubsystemOptions(items)
    subsystem.getSelectedSubsystem match {
      case Some(subsys) =>
        targetSubsystem.updateSubsystemOptions(items)
        targetSubsystem.disableOption(subsys)
      case None =>
        targetSubsystem.updateSubsystemOptions(items)
    }
  }

  // Hide or show the sidebar
  private def setSidebarVisible(show: Boolean): Unit = {
    val s = $("#sidebar")
    if (show) s.removeClass("hide") else s.addClass("hide")
  }

  // Called when the Upload item is selected
  private def uploadSelected(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    mainContent.setContent(fileUploadDialog, "Upload ICD Files")
    if (saveHistory) pushState(viewType = UploadView)
  }

  //  // Called when the Publish item is selected
  //  private def publishItemSelected(saveHistory: Boolean = true)(): Unit = {
  //    val title = if (targetSubsystem.getSubsystemWithVersion.subsystemOpt.isDefined)
  //      "Publish ICD"
  //    else "Publish API"
  //    publishDialog.subsystemChanged()
  //    setSidebarVisible(false)
  //    mainContent.setContent(publishDialog, title)
  //    if (saveHistory) pushState(viewType = PublishView)
  //  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
    override def componentCheckboxChanged(componentName: String, checked: Boolean): Unit = {
      if (checked)
        components.addComponent(
          componentName,
          subsystem.getSubsystemWithVersion,
          targetSubsystem.getSubsystemWithVersion
        )
      else
        components.removeComponentInfo(componentName)

      pushState(viewType = ComponentView)
    }

    // Called when a component link is selected in the sidebar
    override def componentSelected(componentName: String): Unit = {
      goToComponent(componentName)
      pushState(viewType = ComponentView, compName = Some(componentName), replace = true)
    }
  }

  /**
   * Called when a component is selected in one of the publisher/subscriber/command tables.
   * If the linked subsystem is the source or target subsystem, use the component from the
   * selected version of the subsystem, otherwise use the latest version.
   */
  private object ComponentLinkSelectionHandler extends ComponentListener {
    def componentSelected(link: ComponentLink): Unit = {
      val source = subsystem.getSubsystemWithVersion
      val target = targetSubsystem.getSubsystemWithVersion
      val sv = Some(link.subsystem) match {
        case source.subsystemOpt => source
        case target.subsystemOpt => target
        case _                   => SubsystemWithVersion(Some(link.subsystem), None)
      }
      val newTarget = SubsystemWithVersion(None, None)
      for {
        _ <- targetSubsystem.setSubsystemWithVersion(newTarget, saveHistory = false)
        _ <- subsystem.setSubsystemWithVersion(sv, saveHistory = false)
      } yield {
        goToComponent(link.compName)
        pushState(viewType = ComponentView, compName = Some(link.compName), replace = true)
      }
    }
  }

  // Jump to the component description
  private def goToComponent(compName: String, replace: Boolean = false): Unit = {
    val compId = Components.getComponentInfoId(compName)
    if (replace) {
      val baseUrl = dom.window.location.href.split('#')(0)
      dom.window.location.replace(s"$baseUrl#$compId")
    } else {
      dom.window.location.hash = s"#$compId"
    }
  }

  /**
   * Push (or replace) the current app state for the browser history.
   * (Replace is needed if the browser is following a link, in which case the browser automatically pushes something
   * on the stack that we don't want.)
   *
   * If a single component is selected, it should be passed as compName.
   */
  private def pushState(viewType: ViewType, compName: Option[String] = None, replace: Boolean = false): Unit = {
    val hist = BrowserHistory(
      subsystem.getSubsystemWithVersion,
      targetSubsystem.getSubsystemWithVersion,
      icdChooser.getSelectedIcdVersion,
      sidebar.getSelectedComponents,
      viewType = viewType,
      compName
    )

    if (replace) {
      hist.replaceState()
    } else {
      hist.pushState()
    }
  }

  /**
   * Called when the user presses the Back button in the browser
   */
  private def popState(e: PopStateEvent): Unit = {
    BrowserHistory.popState(e).foreach { hist =>
      e.preventDefault()
      // Make sure to wait for futures to complete, so things happen in the right order
      for {
        _ <- subsystem.setSubsystemWithVersion(hist.sourceSubsystem, saveHistory = false)
        _ <- targetSubsystem.setSubsystemWithVersion(hist.targetSubsystem, saveHistory = false)
        _ <- icdChooser.setIcdWithVersion(hist.icdOpt, saveHistory = false)
      } {
        sidebar.setSelectedComponents(hist.sourceComponents)
        hist.viewType match {
          case UploadView  => uploadSelected(saveHistory = false)()
          //          case PublishView => publishItemSelected(saveHistory = false)()
          case VersionView => showVersionHistory(saveHistory = false)()
          case ComponentView | IcdView =>
            updateComponentDisplay(hist.sourceSubsystem, hist.targetSubsystem, hist.sourceComponents).onSuccess {
              case _ => hist.currentCompnent.foreach(compName => goToComponent(compName, replace = true))
            }
        }
      }
    }
  }

  // Show/hide the busy cursor while the future is running
  private def showBusyCursorWhile(f: Future[Unit]): Future[Unit] = {
    $("div").css("cursor", "progress")
    f.onComplete { _ => $("div").css("cursor", "default") }
    f
  }

  /**
   * Updates the main display to match the selected components
   *
   * @return a future indicating when the changes are done
   */
  private def updateComponentDisplay(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion, compNames: List[String]): Future[Unit] = {
    val icdOpt = icdChooser.getSelectedIcdVersion
    setSidebarVisible(true)
    mainContent.clearContent()
    showBusyCursorWhile {
      components.addComponents(compNames, sv, targetSv, icdOpt)
    }
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(sv: SubsystemWithVersion): Future[List[String]] = {
    if (sv.subsystemOpt.isDefined) {
      val path = Routes.components(sv.subsystemOpt.get, sv.versionOpt)
      Ajax.get(path).map { r =>
        Json.fromJson[List[String]](Json.parse(r.responseText)).get
      }.recover {
        case ex =>
          mainContent.displayInternalError(ex)
          Nil
      }
    } else Future.successful(Nil)
  }

  private object SourceSubsystemListener extends SubsystemListener {
    // Called when the source subsystem (or version) combobox selection is changed
    override def subsystemSelected(sv: SubsystemWithVersion, saveHistory: Boolean): Future[Unit] = {
      sidebar.clearComponents()
      mainContent.clearContent()
      sv.subsystemOpt match {
        case Some(selectedSubsystem) =>
          // Target subsystem can't be the same as the selected subsystem
          targetSubsystem.setAllOptionsEnabled()
          targetSubsystem.disableOption(selectedSubsystem)
          val targetSv = targetSubsystem.getSubsystemWithVersion
          for {
            _ <- icdChooser.selectMatchingIcd(sv, targetSv)
            names <- getComponentNames(sv)
            _ <- Future.successful { names.foreach(sidebar.addComponent) }
            _ <- updateComponentDisplay(sv, targetSv, names)
          } yield {
            if (saveHistory) pushState(viewType = ComponentView) else ()
          }
        case None =>
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
        case Some(selectedTarget) =>
          subsystem.setAllOptionsEnabled()
          subsystem.disableOption(selectedTarget)
        case None =>
          subsystem.setAllOptionsEnabled()
      }
      val sv = subsystem.getSubsystemWithVersion
      for {
        _ <- icdChooser.selectMatchingIcd(sv, targSv)
        _ <- updateComponentDisplay(sv, targSv, sidebar.getSelectedComponents)
      } yield {
        if (saveHistory) pushState(viewType = ComponentView)
      }
    }
  }

  // Swap source and target subsystems
  private def swapSubsystems(): Unit = {
    val sv2 = targetSubsystem.getSubsystemWithVersion
    if (sv2.subsystemOpt.isDefined) {
      val sv1 = subsystem.getSubsystemWithVersion
      sidebar.clearComponents()
      mainContent.clearContent()
      for {
        _ <- icdChooser.selectMatchingIcd(sv2, sv1)
        _ <- targetSubsystem.setSubsystemWithVersion(sv1, notifyListener = false, saveHistory = false)
        _ <- subsystem.setSubsystemWithVersion(sv2)
      } {
        sv1.subsystemOpt match {
          case Some(selectedTarget) =>
            subsystem.setAllOptionsEnabled()
            subsystem.disableOption(selectedTarget)
          case None =>
            subsystem.setAllOptionsEnabled()
        }
      }
    }
  }

  private object IcdChooserListener extends IcdListener {
    // Called when the ICD (or ICD version) combobox selection is changed
    override def icdSelected(icdVersionOpt: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit] = {
      icdVersionOpt match {
        case Some(icdVersion) =>
          val sv = SubsystemWithVersion(Some(icdVersion.subsystem), Some(icdVersion.subsystemVersion))
          val tv = SubsystemWithVersion(Some(icdVersion.target), Some(icdVersion.targetVersion))
          for {
            _ <- subsystem.setSubsystemWithVersion(sv, notifyListener = false, saveHistory = false)
            _ <- targetSubsystem.setSubsystemWithVersion(tv, notifyListener = false, saveHistory = false)
          } yield {
            // Prevent selecting the same subsystem for source and target
            subsystem.setAllOptionsEnabled()
            subsystem.disableOption(icdVersion.target)
            targetSubsystem.setAllOptionsEnabled()
            targetSubsystem.disableOption(icdVersion.subsystem)
            // Update the display
            sidebar.clearComponents()
            mainContent.clearContent()
            getComponentNames(sv).onSuccess {
              case names => // Future!
                names.foreach(sidebar.addComponent)
                updateComponentDisplay(sv, tv, names).map { _ =>
                  if (saveHistory) pushState(viewType = IcdView)
                }
            }
          }
        case None => Future.successful()
      }
    }
  }

  // Called when the "Show ICD Version History" menu item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    icdChooser.getSelectedIcd match {
      case Some(icdName) =>
        versionHistory.setIcd(icdName)
        setSidebarVisible(false)
        mainContent.setContent(versionHistory, s"ICD Version History: ${
          icdName.subsystem
        } to ${
          icdName.target
        }")
        if (saveHistory) pushState(viewType = VersionView)
      case None => subsystem.getSelectedSubsystem match {
        case Some(name) =>
          versionHistory.setSubsystem(name)
          setSidebarVisible(false)
          mainContent.setContent(versionHistory, s"Subsystem API Version History: $name")
          if (saveHistory) pushState(viewType = VersionView)
        case None =>
      }
    }
  }

  // Gets a PDF of the currently selected ICD or subsystem API
  private def makePdf(): Unit = {
    val sv = subsystem.getSubsystemWithVersion
    sv.subsystemOpt.foreach { subsys =>
      val compNames = sidebar.getSelectedComponents
      val tv = targetSubsystem.getSubsystemWithVersion
      val icdVersion = icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val uri = Routes.icdAsPdf(subsys, sv.versionOpt, compNames, tv, icdVersion)
      // dom.window.location.assign(uri) // opens in same window
      dom.window.open(uri) // opens in new window or tab
    }
  }
}

/**
 * Main entry object for the web app
 */
object IcdWebClient extends JSApp {

  /**
   * Main entry point from Play
   *
   * @param settings a JavaScript object containing settings (see class IcdWebClient)
   * @return
   */
  @JSExport
  def init(settings: js.Dynamic) = {
    val csrfToken = settings.csrfToken.toString
    val inputDirSupported = settings.inputDirSupported.toString == "true"
    IcdWebClient(csrfToken, inputDirSupported)
  }

  // Main entry point (not used, see init() above)
  @JSExport
  override def main(): Unit = {
  }
}
