package icd.web.client

import icd.web.shared.{BuildInfo, FitsDictionary, IcdVersion, PdfOptions, SharedUtils, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.{HTMLStyleElement, PopStateEvent, document}

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.TypedTag
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import BrowserHistory.*
import Components.*
import icd.web.client.PasswordDialog.PasswordDialogListener
import icd.web.client.PublishDialog.PublishDialogListener
import icd.web.client.SelectDialog.SelectDialogListener
import icd.web.client.StatusDialog.StatusDialogListener
import icd.web.shared.IcdModels.ComponentModel
import icd.web.shared.ComponentInfo
import play.api.libs.json.*

/**
 * Main class for the ICD web app.
 *
 * @param csrfToken         server token used for file upload (for security)
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
//noinspection DuplicatedCode,ScalaUnusedSymbol,SpellCheckingInspection
@JSExportTopLevel("IcdWebClient")
case class IcdWebClient(csrfToken: String, inputDirSupported: Boolean) {

  // Page components
  private val expandToggler = ExpandToggler()
  private val mainContent   = MainContent()
  private val components    = Components(mainContent, ComponentLinkSelectionHandler)
  private val sidebar       = Sidebar(LeftSidebarListener)

  private val fitsDictionaryItem =
    NavbarItem("FITS Dictionary", "Display information about all FITS keywords", showFitsDictionary())

  val pdfButton: PdfButtonItem =
    PdfButtonItem(
      "PDF",
      "mainpdf",
      "Generate and display a PDF containing the FITS Dictionary based on the selected tag",
      makePdf,
      showDocumentNumber = true,
      showDetailButtons = true
    )

  private val navbar = MainNavbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemNames = SubsystemNames(mainContent, updateSubsystemOptions)

  private val passwordDialog = PasswordDialog(mainContent, PasswordListener)

  private val selectItem   = NavbarItem("Select", "Select the API or ICD to display", selectSubsystems())
  private val selectDialog = SelectDialog(mainContent, Selector)
  private val reloadButton = ReloadButton(selectDialog)

  private val logoutItem = NavbarItem("Logout", "Log out of the icd web app", logout)

  private val statusItem   = NavbarItem("Status", "Display the published status of a selected subsystem", showStatus())
  private val statusDialog = StatusDialog(mainContent, StatusListener)

  private val fileUploadItem = NavbarItem("Upload", "Select icd model files to ingest into the icd database", showUploadDialog())
  private val fileUploadDialog = FileUploadDialog(subsystemNames, csrfToken, inputDirSupported)

  private val publishItem   = NavbarItem("Publish", "Shows dialog to publish APIs and ICDs", showPublishDialog())
  private val publishDialog = PublishDialog(mainContent, PublishListener)

  // Call popState() when the user presses the browser Back button
  dom.window.onpopstate = (e: PopStateEvent) => popState(e)

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
          updatePublished().onComplete(_ => showStatus()())
        else
          showPasswordDialog()()
      }
      fileUploadItem.hide()
    }
    else {
      updatePublished().onComplete(_ => showStatus()())
      publishItem.hide()
      logoutItem.hide()
    }
  }

  // Returns future(true) if user is logged in
  private def checkForCookie(): Future[Boolean] = {
    val path = ClientRoutes.checkForCookie
    Fetch.get(path).map { text =>
      Json.fromJson[Boolean](Json.parse(text)).get
    }
  }

  // See if this is a public server
  private def isPublicServer: Future[Boolean] = {
    val path = ClientRoutes.isPublicServer
    Fetch.get(path).map { text =>
      Json.fromJson[Boolean](Json.parse(text)).get
    }
  }

  // Updates the cache of published APIs and ICDs on the server (in case new ones were published)
  private def updatePublished(): Future[Unit] = {
    val path = ClientRoutes.updatePublished
    // XXX TODO: Check response
    Fetch.post(path).map(_ => ())
  }

  // Layout the components on the page
  private def doLayout(): Unit = {
    import scalatags.JsDom.all.*

    navbar.addItem(statusItem)
    navbar.addItem(selectItem)
    navbar.addItem(fileUploadItem)
    navbar.addItem(fitsDictionaryItem)
    navbar.addItem(publishItem)
    navbar.addItem(reloadButton)
    navbar.addItem(expandToggler)
    navbar.addItem(pdfButton)

    navbar.addRightSideItem(logoutItem)

    document.body.appendChild(navbar.markup())
    layout.addItem(sidebar)
    layout.addItem(mainContent)
    document.body.appendChild(layout.markup())
  }

  // Update the list of Subsystem options
  private def updateSubsystemOptions(items: List[String]): Future[Unit] = {
    statusDialog.updateSubsystemOptions(items)
    selectDialog.updateSubsystemOptions(items)
  }

  // Hide or show the navbar
  private def setNavbarVisible(show: Boolean): Unit = {
    val s = document.querySelector(".navbar")
    if (show) {
      s.classList.remove("d-none")
    }
    else {
      s.classList.add("d-none")
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
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    mainContent.setContent(selectDialog, "Select Subsystems and Components")
    if (saveHistory) {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion(),
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion(),
        maybeIcd = selectDialog.icdChooser.getSelectedIcdVersion
      )
    }
    else {
      for {
        _ <- subsystemNames.update()
        _ <- selectDialog.icdChooser.setIcdWithVersion(maybeIcd, saveHistory = false)
        _ <- selectDialog.subsystem.setSubsystemWithVersion(maybeSv, findMatchingIcd = false)
        _ <- selectDialog.targetSubsystem.setSubsystemWithVersion(maybeTargetSv, findMatchingIcd = false)
      } {}
    }
  }

  private def showPasswordDialog()(): Unit = {
    setSidebarVisible(false)
    setNavbarVisible(false)
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    val title = "TIO Software Interface Database System"
    mainContent.setContent(passwordDialog, s"$title ${BuildInfo.version}")
  }

  // Called when the Home/TMT ICD Database navbar item is selected (or through browser history)
  private def showStatus(maybeSubsystem: Option[String] = None, saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    val title = "TIO Software Interface Database System"
    if (saveHistory) {
      mainContent.setContent(statusDialog, s"$title ${BuildInfo.version}")
      pushState(viewType = StatusView, maybeSourceSubsystem = maybeSubsystem.map(SubsystemWithVersion(_, None, None)))
    }
    else {
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
    Fetch.post(path).map { _ =>
      showPasswordDialog()()
      ()
    }
  }

  // Called when the Upload item is selected
  private def showUploadDialog(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    mainContent.setContent(fileUploadDialog, "Upload Subsystem Model Files")
    if (saveHistory) pushState(viewType = UploadView)
  }

  // Called when the Publish item is selected
  private def showPublishDialog(saveHistory: Boolean = true)(): Unit = {
    setSidebarVisible(false)
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    val f = publishDialog.update()
    showBusyCursorWhile(f)
    mainContent.setContent(publishDialog, "Publish APIs and ICDs")
    if (saveHistory) pushState(viewType = PublishView)
  }

  // Listener for sidebar component checkboxes
  private object LeftSidebarListener extends SidebarListener {
    // Called when a component link is selected in the sidebar
    override def componentSelected(componentModel: ComponentModel): Unit = {
      goToComponent(componentModel.component, saveHistory = false)
    }
  }

  /**
   * Called when a component is selected in one of the publisher/subscriber/command tables.
   * If the linked subsystem is the source or target subsystem, use the component from the
   * selected version of the subsystem, otherwise use the latest version.
   */
  private object ComponentLinkSelectionHandler extends ComponentListener {
    def componentSelected(link: ComponentLink): Future[Unit] = {
      val maybeSv              = selectDialog.subsystem.getSubsystemWithVersion()
      val maybeTargetSv        = selectDialog.targetSubsystem.getSubsystemWithVersion()
      val maybeSubsystem       = maybeSv.map(_.subsystem)
      val maybeTargetSubsystem = maybeTargetSv.map(_.subsystem)

      Some(link.subsystem) match {
        case `maybeSubsystem` if maybeSv.flatMap(_.maybeComponent).isEmpty =>
          goToComponent(link.compName)
          Future.successful(())
        case `maybeTargetSubsystem` if maybeTargetSv.flatMap(_.maybeComponent).isEmpty =>
          goToComponent(link.compName)
          Future.successful(())
        case _ =>
          val maybeLinkSv = Some(SubsystemWithVersion(link.subsystem, None, Some(link.compName)))
          for {
            _ <- selectDialog.icdChooser.setIcdWithVersion(None, false, false)
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
  private def goToComponent(compName: String, saveHistory: Boolean = true): Unit = {
    val compId = Components.getComponentInfoId(compName)
    if (compId != null) {
      val elem = document.getElementById(compId)
      if (elem != null) {
        elem.scrollIntoView()
        if (saveHistory) pushState(viewType = ComponentView, compName = Some(compName))
      }
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
        case VersionView => selectDialog.showVersionHistory(saveHistory = false)()
        case FitsView    => showFitsDictionary(saveHistory = false)()
        case StatusView =>
          showStatus(
            hist.maybeSourceSubsystem.map(_.subsystem),
            saveHistory = false
          )()
        case SelectView =>
          for {
            _ <-
              selectDialog.subsystem
                .setSubsystemWithVersion(hist.maybeSourceSubsystem, findMatchingIcd = false)
            _ <-
              selectDialog.targetSubsystem
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
            selectDialog.clientApi(),
            saveHistory = false
          ).foreach { _ =>
            if (hist.maybeUri.isDefined) {
              hist.maybeUri.foreach(uri => dom.window.location.replace(uri))
            }
            else {
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
        searchAllSubsystems: Boolean,
        clientApi: Boolean
    ): Future[Unit] = {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = maybeSv,
        maybeTargetSubsystem = maybeTargetSv,
        maybeIcd = maybeIcd
      )
      updateComponentDisplay(maybeSv, maybeTargetSv, maybeIcd, searchAllSubsystems, clientApi)
    }
  }

  private object StatusListener extends StatusDialogListener {
    override def apiSelected(sv: SubsystemWithVersion): Unit = {
      val maybeSv = Some(sv)
      pushState(viewType = SelectView, maybeSourceSubsystem = maybeSv)
      selectSubsystems(maybeSv = maybeSv, saveHistory = false)()
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
      )()
    }
  }

  private object PasswordListener extends PasswordDialogListener {
    // Called when the correct username and password were given and the apply button pressed
    override def authenticated(token: String): Unit = {
      setNavbarVisible(true)
      // Show the status dialog
      updatePublished().foreach(_ => showStatus()())
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

  private val componentSortOrder = Map(
    "Application" -> 1,
    "Container"   -> 2,
    "Sequencer"   -> 3,
    "Service"     -> 4,
    "Assembly"    -> 5,
    "HCD"         -> 6
  )

  // Sort sidebar components by component type, otherwise alphabetical
  private def sortComponentInfo(i1: ComponentInfo, i2: ComponentInfo): Boolean = {
    val c1 = i1.componentModel
    val c2 = i2.componentModel
    if (c1.subsystem == c2.subsystem) {
      val type1 = c1.componentType
      val type2 = c2.componentType
      if (type1 == type2) {
        c1.component < c2.component
      }
      else {
        componentSortOrder.getOrElse(type1, 99) < componentSortOrder.getOrElse(type2, 99)
      }
    }
    else {
      c1.subsystem < c2.subsystem
    }
  }

  /**
   * Updates the main display to match the selected subsystem(s) and component(s)
   *
   * @return a future indicating when the changes are done
   */
  private def updateComponentDisplay(
      maybeSv: Option[SubsystemWithVersion],
      maybeTargetSv: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean,
      saveHistory: Boolean = true
  ): Future[Unit] = {
    sidebar.clearComponents()
    // Reset the sidebar width to fit component names
    val sidebarElem = document.getElementById("sidebar")
    if (sidebarElem != null) {
      sidebarElem.classList.add("w-auto")
    }
    mainContent.clearContent()
    val f = if (maybeSv.isDefined) {
      reloadButton.setVisible(show = true)
      expandToggler.setVisible(show = true)
      pdfButton.setVisible(show = true)
      showBusyCursorWhile {
        components
          .addComponents(maybeSv.get, maybeTargetSv, maybeIcd, searchAllSubsystems, clientApi)
          .map { infoList =>
            infoList
              .filter(SharedUtils.showComponentInfo)
              .sortWith(sortComponentInfo)
              .foreach(info => sidebar.addComponent(info.componentModel))
            // Only make sidebar visible if the user did not change to another tab
            val title = mainContent.getTitle
            if (title.startsWith("ICD") || title.startsWith("API"))
              setSidebarVisible(true)
          }
      }
    }
    else {
      // Should not get here?
      reloadButton.setVisible(show = false)
      expandToggler.setVisible(show = false)
      pdfButton.setVisible(show = false)
      Future.successful(())
    }
    if (saveHistory) {
      pushState(
        viewType = SelectView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion(),
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion(),
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
        viewType = ComponentView,
        maybeSourceSubsystem = selectDialog.subsystem.getSubsystemWithVersion(),
        maybeTargetSubsystem = selectDialog.targetSubsystem.getSubsystemWithVersion(),
        maybeIcd = selectDialog.icdChooser.getSelectedIcdVersion,
        maybeUri = Some(uri)
      )
    }
  }

  private def addLinkHandlers(): Unit = {
    val links = document.getElementsByTagName("a")
    links.toArray.foreach(x => x.addEventListener("click", linkListener(_)))
  }

  // Gets a PDF of the currently selected ICD or subsystem API
  private def makePdf(pdfOptions: PdfOptions): Unit = {
    import scalatags.JsDom.all.*

    val maybeSv = selectDialog.subsystem.getSubsystemWithVersion()
    maybeSv.foreach { sv =>
      val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion()
      val maybeIcdVersion = selectDialog.icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val searchAll       = selectDialog.searchAllSubsystems()
      val isClientApi     = selectDialog.clientApi()
      val uri             = ClientRoutes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion, searchAll, isClientApi, pdfOptions)

      if (!pdfOptions.details && pdfOptions.expandedIds.nonEmpty) {
        // We need to do a POST in case expandedIds are passed, which can be very long, so create a temp form
        val formId = "tmpPdfForm"
        val tmpForm = form(id := formId, method := "POST", action := uri, target := "_blank")(
          input(`type` := "hidden", name := "expandedIds", value := pdfOptions.expandedIds.mkString(","))
        ).render
        document.body.appendChild(tmpForm)
        tmpForm.submit()
        document.body.removeChild(tmpForm)
      }
      else {
        dom.window.open(uri) // opens in new window or tab
      }
    }
  }

  // Called when the "FITS Dictionary" item is selected
  private def showFitsDictionary(saveHistory: Boolean = true)(): Unit = {
    import icd.web.shared.JsonSupport.*
    setSidebarVisible(false)
    reloadButton.setVisible(show = false)
    expandToggler.setVisible(show = false)
    pdfButton.setVisible(show = false)
    val f = for {
      fitsDict <-
        Fetch
          .get(ClientRoutes.fitsDictionary(None))
          .map { text =>
            Json.fromJson[FitsDictionary](Json.parse(text)).get
          }
    } yield {
      val fitsKeywordDialog = FitsKeywordDialog(fitsDict, ComponentLinkSelectionHandler)
      mainContent.setContent(fitsKeywordDialog, "FITS Dictionary")
      if (saveHistory) pushState(viewType = FitsView)
    }
    showBusyCursorWhile(f.map(_ => ()))
  }
}
