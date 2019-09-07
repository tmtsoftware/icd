package icd.web.client

import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.PopStateEvent
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLStyleElement
import play.api.libs.json._
import org.querki.jquery._

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.TypedTag
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
//noinspection DuplicatedCode
@JSExportTopLevel("IcdWebClient")
case class IcdWebClient(csrfToken: String, inputDirSupported: Boolean) {

  private val cssSettings = scalacss.devOrProdDefaults

  import cssSettings._

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
    targetSubsystem.updateSubsystemOptions(items)
  }

  // Hide or show the sidebar
  private def setSidebarVisible(show: Boolean): Unit = {
    val s = $("#sidebar")
    if (show) {
      s.removeClass("hide")
    } else {
      s.addClass("hide")
    }
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
      val maybeSv = subsystem.getSubsystemWithVersion
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion
      val maybeSubsystem = maybeSv.map(_.subsystem)
      val maybeTargetSubsystem = maybeTargetSv.map(_.subsystem)
      val maybeLinkSv = Some(link.subsystem) match {
        case `maybeSubsystem` => maybeSv
        case `maybeTargetSubsystem` => maybeTargetSv
        case _ => Some(SubsystemWithVersion(link.subsystem, None, None))
      }
      for {
        _ <- targetSubsystem.setSubsystemWithVersion(None, saveHistory = false)
        _ <- subsystem.setSubsystemWithVersion(maybeLinkSv, saveHistory = false)
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
        _ <- subsystem.setSubsystemWithVersion(hist.maybeSourceSubsystem, saveHistory = false)
        _ <- targetSubsystem.setSubsystemWithVersion(hist.maybeTargetSubsystem, saveHistory = false)
        _ <- icdChooser.setIcdWithVersion(hist.maybeIcd, saveHistory = false)
      } {
        hist.viewType match {
          case UploadView => uploadSelected(saveHistory = false)()
          case VersionView => showVersionHistory(saveHistory = false)()
          case ComponentView | IcdView =>
            hist.maybeSourceSubsystem.foreach { sv =>
              updateComponentDisplay(sv, hist.maybeTargetSubsystem).foreach { _ =>
                hist.currentCompnent.foreach(compName => goToComponent(compName, replace = true))
              }
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
   * Updates the main display to match the selected subsystem and component(s)
   *
   * @return a future indicating when the changes are done
   */
  private def updateComponentDisplay(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Future[Unit] = {
    val maybeIcd = icdChooser.getSelectedIcdVersion
    setSidebarVisible(true)
    mainContent.clearContent()
    showBusyCursorWhile {
      components.addComponents(sv, maybeTargetSv, maybeIcd)
    }
  }

  // Gets the list of subcomponents for the selected subsystem
  // XXX TODO FIXME: Is this needed? Done on the server side!
  private def getComponentNames(sv: SubsystemWithVersion): Future[List[String]] = {
    if (sv.maybeComponent.isDefined) {
      Future(List(sv.maybeComponent.get))
    } else {
      val path = Routes.components(sv.subsystem, sv.maybeVersion)
      Ajax.get(path).map { r =>
        Json.fromJson[List[String]](Json.parse(r.responseText)).get
      }.recover {
        case ex =>
          mainContent.displayInternalError(ex)
          Nil
      }
    }
  }

  private object UI {
    def setEnabled(enabled: Boolean): Unit = {
      subsystemSwapper.setEnabled(enabled)
      icdChooser.setEnabled(enabled)
    }
  }

  // XXX TODO FIXME: Replace for {} with async?
  private object SourceSubsystemListener extends SubsystemListener {
    // Called when the source subsystem (or version) combobox selection is changed
    override def subsystemSelected(maybeSv: Option[SubsystemWithVersion], saveHistory: Boolean): Future[Unit] = {
      sidebar.clearComponents()
      mainContent.clearContent()
      // Target subsystem can't be the same as the selected subsystem
      UI.setEnabled(false)
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion
      maybeSv.map {sv =>
        for {
          _ <- icdChooser.selectMatchingIcd(sv, maybeTargetSv)
          names <- getComponentNames(sv)
          _ <- Future.successful {
            names.foreach(sidebar.addComponent)
          }
          _ <- updateComponentDisplay(sv, maybeTargetSv)
        } yield {
          if (saveHistory) pushState(viewType = ComponentView) else ()
          UI.setEnabled(true)
        }
      }.getOrElse(Future.successful())
    }
  }

  private object TargetSubsystemListener extends SubsystemListener {
    // Called when the target subsystem or version combobox selection is changed
    override def subsystemSelected(maybeTargSv: Option[SubsystemWithVersion], saveHistory: Boolean): Future[Unit] = {
      val maybeSv = subsystem.getSubsystemWithVersion
      UI.setEnabled(false)
      maybeSv.map {sv =>
        for {
          _ <- icdChooser.selectMatchingIcd(sv, maybeTargSv)
          _ <- updateComponentDisplay(sv, maybeTargSv)
        } yield {
          if (saveHistory) pushState(viewType = ComponentView)
          UI.setEnabled(true)
        }
      }.getOrElse(Future.successful())
    }
  }

  // Swap source and target subsystems
  private def swapSubsystems(): Unit = {
    for {
      sv2 <- targetSubsystem.getSubsystemWithVersion
      sv1 <- subsystem.getSubsystemWithVersion
    } {
      sidebar.clearComponents()
      mainContent.clearContent()
      UI.setEnabled(false)
      for {
        _ <- icdChooser.selectMatchingIcd(sv2, Some(sv1))
        _ <- targetSubsystem.setSubsystemWithVersion(Some(sv1), notifyListener = false, saveHistory = false)
        _ <- subsystem.setSubsystemWithVersion(Some(sv2))
      } {
        UI.setEnabled(true)
      }
    }
  }

  private object IcdChooserListener extends IcdListener {
    // Called when the ICD (or ICD version) combobox selection is changed
    override def icdSelected(maybeIcdVersion: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit] = {
      maybeIcdVersion match {
        case Some(icdVersion) =>
          // XXX TODO FIXME add optional selected component
          val sv = SubsystemWithVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion), None)
          val targetSv = SubsystemWithVersion(icdVersion.target, Some(icdVersion.targetVersion), None)
          UI.setEnabled(false)
          for {
            _ <- subsystem.setSubsystemWithVersion(Some(sv), notifyListener = false, saveHistory = false)
            _ <- targetSubsystem.setSubsystemWithVersion(Some(targetSv), notifyListener = false, saveHistory = false)
          } yield {
            // Update the display
            sidebar.clearComponents()
            mainContent.clearContent()
            val f = getComponentNames(sv).flatMap { names => // Future!
              names.foreach(sidebar.addComponent)
              updateComponentDisplay(sv, Some(targetSv)).map { _ =>
                if (saveHistory) pushState(viewType = IcdView)
              }
            }
            f.onComplete {
              _ => UI.setEnabled(true)
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
    val maybeSv = subsystem.getSubsystemWithVersion
    maybeSv.foreach { sv =>
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion
      val maybeIcdVersion = icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val uri = Routes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion)
      // dom.window.location.assign(uri) // opens in same window
      dom.window.open(uri) // opens in new window or tab
    }
  }
}

///**
//  * Main entry object for the web app
//  */
//object IcdWebClient {
//
//  import scala.scalajs.js.Dynamic.global
//
////  /**
////    * Main entry point from Play
////    *
////    * @param settings a JavaScript object containing settings (see class IcdWebClient)
////    * @return
////    */
////  def init(settings: js.Dynamic) = {
////    val csrfToken = settings.csrfToken.toString
////    val inputDirSupported = settings.inputDirSupported.toString == "true"
////    IcdWebClient(csrfToken, inputDirSupported)
////  }
//
//  // Main entry point
//  def main(args: Array[String]): Unit = {
//    val csrfToken = args(0)
//    val inputDirSupported = args(1) == "true"
//    IcdWebClient(csrfToken, inputDirSupported)
//  }
//}
