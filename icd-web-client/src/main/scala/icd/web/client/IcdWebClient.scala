package icd.web.client

import icd.web.shared.{BuildInfo, IcdVersion, PdfOptions, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, PopStateEvent, Window, document}
import org.scalajs.dom.raw.HTMLStyleElement

import scala.concurrent.Future
import scala.scalajs.js.annotation.{JSExportTopLevel, JSGlobalScope}
import scalatags.JsDom.TypedTag
import scalacss.ScalatagsCss._

import scala.concurrent.ExecutionContext.Implicits.global
import BrowserHistory._
import Components._
import icd.web.client.PasswordDialog.PasswordDialogListener
import icd.web.client.PublishDialog.PublishDialogListener
import icd.web.client.SelectDialog.SelectDialogListener
import icd.web.client.StatusDialog.StatusDialogListener
import org.scalajs.dom.ext.Ajax
import org.w3c.dom.html.{HTMLAnchorElement, HTMLFormElement}
import play.api.libs.json.Json

import scala.scalajs.js

// Need to reset this JavaScript variable after loading a new API or ICD. See resources/resize.js
@js.native
@JSGlobalScope
object Globals extends js.Object {
  var navbarExpandAll: Boolean = js.native
}

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

  private val historyItem   = NavbarItem("History", "Display the version history for an API or ICD", showVersionHistory())
  private val historyDialog = HistoryDialog(mainContent)

  private val pdfItem = NavbarPdfItem("PDF", "Generate and display a PDF for the API or ICD", makePdf)
  pdfItem.setEnabled(false)

  private val archiveItem = NavbarPdfItem(
    "Archive",
    "Generate and display an 'Archived Items' report for the selected subsystem (or all subsystems)",
    makeArchivedItemsReport
  )

  private val navbar = Navbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemNames = SubsystemNames(mainContent, updateSubsystemOptions)

  private val passwordDialog = PasswordDialog(mainContent, PasswordListener)

  private val selectItem   = NavbarItem("Select", "Select the API or ICD to display", selectSubsystems())
  private val selectDialog = SelectDialog(mainContent, Selector, pdfItem)

  private val logoutItem = NavbarItem("Logout", "Log out of the icd web app", logout)

  private val statusItem   = NavbarItem("Status", "Display the published status of a selected subsystem", showStatus())
  private val statusDialog = StatusDialog(mainContent, StatusListener, pdfItem)

  private val fileUploadItem   = NavbarItem("Upload", "Select icd model files to ingest into the icd database", showUploadDialog())
  private val fileUploadDialog = FileUploadDialog(subsystemNames, csrfToken, inputDirSupported)

  private val publishItem   = NavbarItem("Publish", "Shows dialog to publish APIs and ICDs", showPublishDialog())
  private val publishDialog = PublishDialog(mainContent, PublishListener)

  // Used to keep track of the current dialog, so that we know which subsystem to use for History, PDF buttons
  private var currentView: ViewType = StatusView

  // Call popState() when the user presses the browser Back button
  dom.window.onpopstate = popState _

  // Initial browser state
  doLayout()

  // If uploads are not allowed, hide the item (Doing this in the background caused issues with jquery).
  // On a public server, uploads should be disabled, on local installations, publishing should be disabled.
  isPublicServer.foreach { publicServer =>
    if (publicServer) {
      // If user is logged in, skip the login dialog
      checkForCookie().foreach { loggedIn =>
        // Refresh the list of published APIs and ICDs when the user refreshes the web app
        if (loggedIn)
          updatePublished().onComplete(_ => showStatus())
        else
          showPasswordDialog()
      }
      fileUploadItem.hide()
    } else {
      updatePublished().onComplete(_ => showStatus())
      publishItem.hide()
      logoutItem.hide()
    }
  }

  // Returns future(true) if user is logged in
  private def checkForCookie(): Future[Boolean] = {
    val path = ClientRoutes.checkForCookie
    Ajax.get(path).map { r =>
      Json.fromJson[Boolean](Json.parse(r.responseText)).get
    }
  }

  // See if this is a public server
  private def isPublicServer: Future[Boolean] = {
    val path = ClientRoutes.isPublicServer
    Ajax.get(path).map { r =>
      Json.fromJson[Boolean](Json.parse(r.responseText)).get
    }
  }

  // Updates the cache of published APIs and ICDs on the server (in case new ones were published)
  private def updatePublished(): Future[Unit] = {
    val path = ClientRoutes.updatePublished
    // XXX TODO: Check response
    Ajax.post(path).map(_ => ())
  }

  // Layout the components on the page
  private def doLayout(): Unit = {
    // Add CSS styles
    head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    navbar.addItem(statusItem)
    navbar.addItem(selectItem)
    navbar.addItem(fileUploadItem)
    navbar.addItem(historyItem)
    navbar.addItem(pdfItem)
    navbar.addItem(archiveItem)
    navbar.addItem(publishItem)
    navbar.addItem(expandToggler)
    navbar.addRightSideItem(logoutItem)

    layout.addItem(sidebar)
    layout.addItem(mainContent)

    body.appendChild(navbar.markup())
    body.appendChild(layout.markup())
  }

  // Update the list of Subsystem options
  private def updateSubsystemOptions(items: List[String]): Future[Unit] = {
    statusDialog.updateSubsystemOptions(items)
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

  // Hide or show the navbar
  private def setNavbarVisible(show: Boolean): Unit = {
    val s = document.querySelector(".navbar")
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
      saveHistory: Boolean = true
  )(): Unit = {
    setSidebarVisible(false)
    mainContent.setContent(selectDialog, "Select Subsystems and Components")
    currentView = SelectView
    if (saveHistory) {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion,
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion,
        maybeIcd = selectDialog.icdChooser.getSelectedIcdVersion
      )
    } else {
      for {
        _ <- subsystemNames.update()
        _ <- selectDialog.icdChooser.setIcdWithVersion(maybeIcd, saveHistory = false)
        _ <- selectDialog.subsystem.setSubsystemWithVersion(maybeSv, findMatchingIcd = false)
        _ <- selectDialog.targetSubsystem.setSubsystemWithVersion(maybeTargetSv, findMatchingIcd = false)
      } {}
    }
  }

  private def showPasswordDialog(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    setNavbarVisible(false)
    val title = "TMT Interface Database System"
    mainContent.setContent(passwordDialog, s"$title ${BuildInfo.version}")
  }

  // Called when the Home/TMT ICD Database navbar item is selected (or through browser history)
  private def showStatus(maybeSubsystem: Option[String] = None, saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    currentView = StatusView
    val title = "TMT Interface Database System"
    if (saveHistory) {
      mainContent.setContent(statusDialog, s"$title ${BuildInfo.version}")
      pushState(viewType = StatusView, maybeSourceSubsystem = maybeSubsystem.map(SubsystemWithVersion(_, None, None)))
    } else {
      for {
        _ <- subsystemNames.update()
      } {
        mainContent.setContent(statusDialog, s"$title ${BuildInfo.version}")
        statusDialog.setSubsystem(maybeSubsystem, saveHistory = false)
      }
    }
  }

  private def logout(): Future[Unit] = {
    val path = ClientRoutes.logout
    Ajax.post(path).map { _ =>
      showPasswordDialog()
      ()
    }
  }

  // Called when the Upload item is selected
  private def showUploadDialog(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    mainContent.setContent(fileUploadDialog, "Upload Subsystem Model Files")
    if (saveHistory) pushState(viewType = UploadView)
  }

  // Called when the Publish item is selected
  private def showPublishDialog(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    val f = publishDialog.update()
    showBusyCursorWhile(f)
    mainContent.setContent(publishDialog, "Publish APIs and ICDs")
    if (saveHistory) pushState(viewType = PublishView)
  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
    // Called when a component link is selected in the sidebar
    override def componentSelected(componentName: String): Unit = {
      goToComponent(componentName, saveHistory = false)
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

      Some(link.subsystem) match {
        case `maybeSubsystem` if maybeSv.flatMap(_.maybeComponent).isEmpty =>
          goToComponent(link.compName)
        case `maybeTargetSubsystem` if maybeTargetSv.flatMap(_.maybeComponent).isEmpty =>
          goToComponent(link.compName)
        case _ =>
          val maybeLinkSv = Some(SubsystemWithVersion(link.subsystem, None, Some(link.compName)))
          for {
            _ <- selectDialog.targetSubsystem.setSubsystemWithVersion(None)
            _ <- selectDialog.subsystem.setSubsystemWithVersion(maybeLinkSv)
            _ <- selectDialog.applySettings()
          } yield {
            goToComponent(link.compName)
          }
      }

    }
  }

  // Jump to the component description
  private def goToComponent(compName: String, replace: Boolean = false, saveHistory: Boolean = true): Unit = {
    val compId = Components.getComponentInfoId(compName)
    if (replace) {
      val baseUrl = dom.window.location.href.split('#')(0)
      val url     = s"$baseUrl#$compId"
      dom.window.location.replace(url)
      if (saveHistory) pushState(viewType = ComponentView, compName = Some(compName), replace)
    } else {
      val url = s"#$compId"
      dom.window.location.hash = url
      if (saveHistory) pushState(viewType = ComponentView, compName = Some(compName), replace)
    }
  }

  /**
   * Push (or replace) the current app state for the browser history.
   * (Replace is needed if the browser is following a link, in which case the browser automatically pushes something
   * on the stack that we don't want.)
   *
   * If a single component is selected, it should be passed as compName.
   */
  private def pushState(
      viewType: ViewType,
      compName: Option[String] = None,
      replace: Boolean = false,
      maybeSourceSubsystem: Option[SubsystemWithVersion] = None,
      maybeTargetSubsystem: Option[SubsystemWithVersion] = None,
      maybeIcd: Option[IcdVersion] = None,
      maybeUri: Option[String] = None
  ): Unit = {
    val hist = BrowserHistory(
      maybeSourceSubsystem,
      maybeTargetSubsystem,
      maybeIcd,
      viewType,
      compName,
      maybeUri
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
      hist.viewType match {
        case UploadView  => showUploadDialog(saveHistory = false)()
        case PublishView => showPublishDialog(saveHistory = false)()
        case VersionView => showVersionHistory(saveHistory = false)()
        case StatusView =>
          showStatus(
            hist.maybeSourceSubsystem.map(_.subsystem),
            saveHistory = false
          )
        case SelectView =>
          for {
            _ <- selectDialog.subsystem
                  .setSubsystemWithVersion(hist.maybeSourceSubsystem, findMatchingIcd = false)
            _ <- selectDialog.targetSubsystem
                  .setSubsystemWithVersion(hist.maybeTargetSubsystem, findMatchingIcd = false)
            _ <- selectDialog.icdChooser.setIcdWithVersion(hist.maybeIcd, notifyListener = false, saveHistory = false)
          } {
            selectSubsystems(
              hist.maybeSourceSubsystem,
              hist.maybeTargetSubsystem,
              hist.maybeIcd,
              saveHistory = false
            )()
          }
        case ComponentView /*| IcdView*/ =>
          updateComponentDisplay(
            hist.maybeSourceSubsystem,
            hist.maybeTargetSubsystem,
            hist.maybeIcd,
            selectDialog.searchAllSubsystems(),
            saveHistory = false
          ).foreach { _ =>
            if (hist.maybeUri.isDefined) {
              hist.maybeUri.foreach(uri => dom.window.location.replace(uri))
            } else {
              hist.currentCompnent.foreach(compName => goToComponent(compName, saveHistory = false))
            }
          }
      }
    }
  }

  private object Selector extends SelectDialogListener {
    override def subsystemsSelected(
        maybeSv: Option[SubsystemWithVersion],
        maybeTargetSv: Option[SubsystemWithVersion],
        maybeIcd: Option[IcdVersion],
        searchAllSubsystems: Boolean
    ): Future[Unit] = {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = maybeSv,
        maybeTargetSubsystem = maybeTargetSv,
        maybeIcd = maybeIcd
      )
      updateComponentDisplay(maybeSv, maybeTargetSv, maybeIcd, searchAllSubsystems)
    }
  }

  private object StatusListener extends StatusDialogListener {
    override def apiSelected(sv: SubsystemWithVersion): Unit = {
      val maybeSv = Some(sv)
      pushState(viewType = SelectView, maybeSourceSubsystem = maybeSv)
      selectSubsystems(maybeSv = maybeSv, saveHistory = false)
    }

    override def icdSelected(icdVersion: IcdVersion): Unit = {
      val maybeSourceSubsystem = Some(SubsystemWithVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion), None))
      val maybeTargetSubsystem = Some(SubsystemWithVersion(icdVersion.target, Some(icdVersion.targetVersion), None))
      val maybeIcd             = Some(icdVersion)
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = maybeSourceSubsystem,
        maybeTargetSubsystem = maybeTargetSubsystem,
        maybeIcd = maybeIcd
      )
      selectSubsystems(
        maybeSv = maybeSourceSubsystem,
        maybeTargetSv = maybeTargetSubsystem,
        maybeIcd = maybeIcd,
        saveHistory = false
      )
    }
  }

  private object PasswordListener extends PasswordDialogListener {
    // Called when the correct username and password were given and the apply button pressed
    override def authenticated(token: String): Unit = {
      setNavbarVisible(true)
      // Show the status dialog
      updatePublished().foreach(_ => showStatus())
    }
  }

  private object PublishListener extends PublishDialogListener {
    override def publishChange(): Future[Unit] = {
      for {
        _ <- updatePublished()
        _ <- subsystemNames.update()
        _ <- selectDialog.icdChooser.updateIcdOptions()

      } yield {}
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
      searchAllSubsystems: Boolean,
      saveHistory: Boolean = true
  ): Future[Unit] = {
    sidebar.clearComponents()
    mainContent.clearContent()
    Globals.navbarExpandAll = false
    val f = if (maybeSv.isDefined) {
      showBusyCursorWhile {
        components
          .addComponents(maybeSv.get, maybeTargetSv, maybeIcd, searchAllSubsystems)
          .map { infoList =>
            infoList.foreach(info => sidebar.addComponent(info.componentModel.component))
            setSidebarVisible(true)
          }
      }
    } else Future.successful(())
    currentView = SelectView
    if (saveHistory) {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion,
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion,
        maybeIcd = selectDialog.icdChooser.getSelectedIcdVersion
      )
    }
    f.foreach(_ => addLinkHandlers())
    f
  }

  private def linkListener(e: dom.Event): Unit = {
    val ar = e.target.toString.split('#')
    if (ar.length == 2) {
      val uri = s"#${ar.tail.head}"
      pushState(
        replace = false,
        viewType = ComponentView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion,
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion,
        maybeIcd = selectDialog.icdChooser.getSelectedIcdVersion,
        maybeUri = Some(uri)
      )
    }
  }

  private def addLinkHandlers(): Unit = {
    val links = document.getElementsByTagName("a")
    links.toArray.foreach(x => x.addEventListener("click", linkListener))
  }

  // Called when the "Show ICD Version History" menu item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    def showApiVersionHistory(subsystem: String): Unit = {
      historyDialog.setSubsystem(subsystem)
      setSidebarVisible(false)
      mainContent.setContent(historyDialog, s"Subsystem API Version History: $subsystem")
      if (saveHistory) pushState(viewType = VersionView)
    }
    if (currentView == StatusView) {
      statusDialog.getSelectedSubsystem.foreach(showApiVersionHistory)
    } else {
      selectDialog.icdChooser.getSelectedIcd match {
        case Some(icdName) =>
          historyDialog.setIcd(icdName)
          setSidebarVisible(false)
          mainContent.setContent(historyDialog, s"ICD Version History: ${icdName.subsystem} to ${icdName.target}")
          if (saveHistory) pushState(viewType = VersionView)
        case None =>
          selectDialog.subsystem.getSelectedSubsystem.foreach(showApiVersionHistory)
      }
    }
  }

  // Gets a PDF of the currently selected ICD or subsystem API
  private def makePdf(pdfOptions: PdfOptions): Unit = {
    import scalatags.JsDom.all._
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion

    maybeSv.foreach { sv =>
      val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion
      val maybeIcdVersion = selectDialog.icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val searchAll       = selectDialog.searchAllSubsystems()
      val uri             = ClientRoutes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion, searchAll, pdfOptions)

      if (!pdfOptions.details && pdfOptions.expandedIds.nonEmpty) {
        // We need to do a POST in case expandedIds are passed, which can be very long, so create a temp form
        val formId = "tmpPdfForm"
        val tmpForm = form(id := formId, method := "POST", action := uri, target := "_blank")(
          input(`type` := "hidden", name := "expandedIds", value := pdfOptions.expandedIds.mkString(","))
        ).render
        document.body.appendChild(tmpForm)
        tmpForm.submit();
        document.body.removeChild(tmpForm);
      } else {
        dom.window.open(uri) // opens in new window or tab
      }
    }
  }

  // Gets a PDF with an Archived Items report for the currently selected subsystem API
  private def makeArchivedItemsReport(options: PdfOptions): Unit = {
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion

    val uri =
      if (maybeSv.isDefined)
        ClientRoutes.archivedItemsReport(maybeSv.get, options)
      else
        ClientRoutes.archivedItemsReportFull(options)
    dom.window.open(uri) // opens in new window or tab
  }
}
