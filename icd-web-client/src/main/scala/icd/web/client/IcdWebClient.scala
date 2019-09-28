package icd.web.client

import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.{PopStateEvent, document}
import org.scalajs.dom.raw.{HTMLDivElement, HTMLStyleElement}

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.TypedTag
import scalacss.ScalatagsCss._

import scala.concurrent.ExecutionContext.Implicits.global
import BrowserHistory._
import Components._
import icd.web.client.SelectDialog.SelectDialogListener

/**
 * Main class for the ICD web app.
 *
 * @param csrfToken         server token used for file upload (for security)
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
//noinspection DuplicatedCode
@JSExportTopLevel("IcdWebClient")
case class IcdWebClient(csrfToken: String, inputDirSupported: Boolean) {

  private val cssSettings = scalacss.devOrProdDefaults
  import cssSettings._

  private val head = dom.document.head
  private val body = dom.document.body

  // Page components
  private val expandToggler = ExpandToggler()
  private val mainContent   = MainContent()
  private val components    = Components(mainContent, ComponentLinkSelectionHandler)
  private val sidebar       = Sidebar(LeftSidebarListener)

  private val historyItem    = NavbarItem("History", showVersionHistory())
  private val versionHistory = VersionHistory(mainContent)

  private val pdfItem = NavbarItem("PDF", makePdf)

  private val navbar = Navbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemNames = SubsystemNames(mainContent, updateSubsystemOptions)

  private val selectItem   = NavbarItem("Select", selectSubsystems())
  private val selectDialog = SelectDialog(subsystemNames, mainContent, Selector)

  private val fileUploadItem   = NavbarItem("Upload", uploadSelected())
  private val fileUploadDialog = FileUploadDialog(subsystemNames, csrfToken, inputDirSupported)

  // Call popState() when the user presses the browser Back button
  dom.window.onpopstate = popState _

  // Initial browser state
  doLayout()
  selectSubsystems()

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    navbar.addItem(selectItem)
    navbar.addItem(fileUploadItem)
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
    selectDialog.updateSubsystemOptions(items)
  }

  // Hide or show the sidebar
  private def setSidebarVisible(show: Boolean): Unit = {
    val s = document.querySelector("#sidebar")
    if (show) {
      s.classList.remove("hide")
    } else {
      s.classList.add("hide")
    }
  }

  // Called when the Select navbar item is selected (or through browser history)
  private def selectSubsystems(
      maybeSv: Option[SubsystemWithVersion] = None,
      maybeTargetSv: Option[SubsystemWithVersion] = None,
      maybeIcd: Option[IcdVersion] = None,
      maybeSourceComponent: Option[String] = None,
      maybeTargetComponent: Option[String] = None,
      saveHistory: Boolean = true
  )(): Unit = {
    setSidebarVisible(false)
    mainContent.setContent(selectDialog, "Select Subsystems and Components")
    selectDialog.subsystem.setSelectedComponent(maybeSourceComponent)
    selectDialog.targetSubsystem.setSelectedComponent(maybeTargetComponent)
    if (saveHistory) pushState(viewType = SelectView)
  }

  // Called when the Upload item is selected
  private def uploadSelected(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    mainContent.setContent(fileUploadDialog, "Upload Subsystem Model Files")
    if (saveHistory) pushState(viewType = UploadView)
  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
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
      val maybeSv              = selectDialog.subsystem.getSubsystemWithVersion
      val maybeTargetSv        = selectDialog.targetSubsystem.getSubsystemWithVersion
      val maybeSubsystem       = maybeSv.map(_.subsystem)
      val maybeTargetSubsystem = maybeTargetSv.map(_.subsystem)
      val maybeLinkSv = Some(link.subsystem) match {
        case `maybeSubsystem`       => maybeSv
        case `maybeTargetSubsystem` => maybeTargetSv
        case _                      => Some(SubsystemWithVersion(link.subsystem, None, None))
      }
      for {
        _ <- selectDialog.targetSubsystem.setSubsystemWithVersion(None, saveHistory = false)
        _ <- selectDialog.subsystem.setSubsystemWithVersion(maybeLinkSv, saveHistory = false)
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
      selectDialog.subsystem.getSubsystemWithVersion,
      selectDialog.targetSubsystem.getSubsystemWithVersion,
      selectDialog.icdChooser.getSelectedIcdVersion,
      selectDialog.subsystem.getSelectedComponent,
      selectDialog.targetSubsystem.getSelectedComponent,
      viewType,
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
        _ <- selectDialog.subsystem.setSubsystemWithVersion(hist.maybeSourceSubsystem, saveHistory = false)
        _ <- selectDialog.targetSubsystem.setSubsystemWithVersion(hist.maybeTargetSubsystem, saveHistory = false)
        _ <- selectDialog.icdChooser.setIcdWithVersion(hist.maybeIcd, saveHistory = false)
      } {
        hist.viewType match {
          case UploadView  => uploadSelected(saveHistory = false)()
          case VersionView => showVersionHistory(saveHistory = false)()
          case SelectView =>
            selectSubsystems(
              hist.maybeSourceSubsystem,
              hist.maybeTargetSubsystem,
              hist.maybeIcd,
              hist.maybeSourceComponent,
              hist.maybeTargetComponent,
              saveHistory = false
            )()
          case ComponentView | IcdView =>
            updateComponentDisplay(
              hist.maybeSourceSubsystem,
              hist.maybeTargetSubsystem,
              hist.maybeIcd,
              saveHistory = false
            ).foreach { _ =>
              hist.currentCompnent.foreach(compName => goToComponent(compName, replace = true))
            }
        }
      }
    }
  }

  // Show/hide the busy cursor while the future is running
  private def showBusyCursorWhile(f: Future[Unit]): Future[Unit] = {
    body.classList.add("busyWaiting")
    f.onComplete { _ =>
      body.classList.remove("busyWaiting")
    }
    f
  }

  private object Selector extends SelectDialogListener {
    override def subsystemsSelected(
        maybeSv: Option[SubsystemWithVersion],
        maybeTargetSv: Option[SubsystemWithVersion],
        maybeIcd: Option[IcdVersion]
    ): Future[Unit] = {
      updateComponentDisplay(maybeSv, maybeTargetSv, maybeIcd)
    }
  }

  /**
   * Updates the main display to match the selected subsystem and component(s)
   *
   * @return a future indicating when the changes are done
   */
  private def updateComponentDisplay(
      maybeSv: Option[SubsystemWithVersion],
      maybeTargetSv: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      saveHistory: Boolean = true
  ): Future[Unit] = {
    sidebar.clearComponents()
    mainContent.clearContent()
    val f = if (maybeSv.isDefined) {
      showBusyCursorWhile {
        val f = components.addComponents(maybeSv.get, maybeTargetSv, maybeIcd)
        selectDialog.subsystem.getComponents.foreach(sidebar.addComponent)
        setSidebarVisible(true)
        f
      }
    } else Future.successful(())
    if (saveHistory) {
      pushState(viewType = SelectView)
      pushState(viewType = IcdView)
    }
    f
  }

  // Called when the "Show ICD Version History" menu item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    selectDialog.icdChooser.getSelectedIcd match {
      case Some(icdName) =>
        versionHistory.setIcd(icdName)
        setSidebarVisible(false)
        mainContent.setContent(versionHistory, s"ICD Version History: ${icdName.subsystem} to ${icdName.target}")
        if (saveHistory) pushState(viewType = VersionView)
      case None =>
        selectDialog.subsystem.getSelectedSubsystem match {
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
    val maybeSv = selectDialog.subsystem.getSubsystemWithVersion
    maybeSv.foreach { sv =>
      val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion
      val maybeIcdVersion = selectDialog.icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val uri             = Routes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion)
      // dom.window.location.assign(uri) // opens in same window
      dom.window.open(uri) // opens in new window or tab
    }
  }
}
