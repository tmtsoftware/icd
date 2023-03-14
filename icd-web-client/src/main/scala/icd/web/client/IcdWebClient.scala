package icd.web.client

import icd.web.shared.{BuildInfo, FitsDictionary, IcdVersion, IcdVizOptions, PdfOptions, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLAnchorElement, HTMLStyleElement, PopStateEvent, document}

import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.TypedTag
import scalacss.ScalatagsCss._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import BrowserHistory._
import Components._
import icd.web.client.PasswordDialog.PasswordDialogListener
import icd.web.client.PublishDialog.PublishDialogListener
import icd.web.client.SelectDialog.SelectDialogListener
import icd.web.client.StatusDialog.StatusDialogListener
import play.api.libs.json._

import scala.scalajs.js.URIUtils
import scala.util.Success

/**
 * Main class for the ICD web app.
 *
 * @param csrfToken         server token used for file upload (for security)
 * @param inputDirSupported true if uploading directories is supported (currently only for Chrome)
 */
//noinspection DuplicatedCode,ScalaUnusedSymbol,SpellCheckingInspection
@JSExportTopLevel("IcdWebClient")
case class IcdWebClient(csrfToken: String, inputDirSupported: Boolean) {

  private val cssSettings = scalacss.devOrProdDefaults
  import cssSettings._

  // Page components
  private val expandToggler = ExpandToggler()
  private val reloadButton  = ReloadButton()
  private val mainContent   = MainContent()
  private val components    = Components(mainContent, ComponentLinkSelectionHandler)
  private val sidebar       = Sidebar(LeftSidebarListener)

  private val historyItem   = NavbarItem("History", "Display the version history for an API or ICD", showVersionHistory())
  private val historyDialog = HistoryDialog(mainContent)

  private val fitsDictionaryItem =
    NavbarItem("FITS Dictionary", "Display information about all FITS keywords", showFitsDictionary())

  private val pdfItem = NavbarPdfItem("PDF", "Generate and display a PDF for the API or ICD", makePdf, showDocumentNumber = true)
  pdfItem.setEnabled(false)

  private val generateItem = NavbarDropDownItem(
    "Generate",
    "Generate code for the selected API/component",
    List("Scala", "Java", "TypeScript", "Python"),
    generateCode
  )
  generateItem.setEnabled(false)

  private val graphItem =
    NavbarGraphItem("Graph", "Generate and display a graph of relationships for the selected components", makeGraph)
  graphItem.setEnabled(false)

  private val archiveItem = NavbarPdfItem(
    "Archive",
    "Generate and display an 'Archived Items' report for the selected subsystem/component (or all subsystems)",
    makeArchivedItemsReport,
    showDocumentNumber = false
  )

  private val missingItem = NavbarPdfItem(
    "Missing",
    "Generate and display a 'Missing Items' report for the selected subsystem (or all subsystems)",
    makeMissingItemsReport,
    showDocumentNumber = false
  )

  private val navbar = Navbar()
  private val layout = Layout()

  // Get the list of subsystems from the server and update the two comboboxes
  private val subsystemNames = SubsystemNames(mainContent, updateSubsystemOptions)

  private val passwordDialog = PasswordDialog(mainContent, PasswordListener)

  private val selectItem   = NavbarItem("Select", "Select the API or ICD to display", selectSubsystems())
  private val selectDialog = SelectDialog(mainContent, Selector, List(pdfItem, generateItem, graphItem))

  private val logoutItem = NavbarItem("Logout", "Log out of the icd web app", logout)

  private val statusItem   = NavbarItem("Status", "Display the published status of a selected subsystem", showStatus())
  private val statusDialog = StatusDialog(mainContent, StatusListener, List(pdfItem, generateItem, graphItem))

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
    import scalatags.JsDom.all._
    // Add CSS styles
    document.head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    navbar.addItem(statusItem)
    navbar.addItem(selectItem)
    navbar.addItem(fileUploadItem)
    navbar.addItem(historyItem)
    navbar.addItem(pdfItem)
    navbar.addItem(generateItem)
    navbar.addItem(graphItem)
    navbar.addItem(archiveItem)
    navbar.addItem(missingItem)
    navbar.addItem(fitsDictionaryItem)
    navbar.addItem(publishItem)
    navbar.addItem(reloadButton)
    navbar.addItem(expandToggler)
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

  // Hide or show the sidebar
  private def setSidebarVisible(show: Boolean): Unit = {
    val s = document.querySelector("#sidebar")
    if (show) {
      s.classList.remove("d-none")
    }
    else {
      s.classList.add("d-none")
    }
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
    mainContent.setContent(selectDialog, "Select Subsystems and Components")
    currentView = SelectView
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
   * Push (or replace) the current app state for the browser history.
   * (Replace is needed if the browser is following a link, in which case the browser automatically pushes something
   * on the stack that we don't want.)
   *
   * If a single component is selected, it should be passed as compName.
   */
  private def pushState(
      viewType: ViewType,
      compName: Option[String] = None,
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
    hist.pushState()
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
    mainContent.clearContent()
    val f = if (maybeSv.isDefined) {
      showBusyCursorWhile {
        components
          .addComponents(maybeSv.get, maybeTargetSv, maybeIcd, searchAllSubsystems, clientApi)
          .map { infoList =>
            infoList.foreach(info => sidebar.addComponent(info.componentModel.component))
            setSidebarVisible(true)
          }
      }
    }
    else Future.successful(())
    currentView = SelectView
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
    links.toArray.foreach(x => x.addEventListener("click", linkListener))
  }

  // Called when the "History" item is selected
  private def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    def showApiVersionHistory(subsystem: String): Unit = {
      historyDialog.setSubsystem(subsystem)
      setSidebarVisible(false)
      mainContent.setContent(historyDialog, s"Subsystem API Version History: $subsystem")
      if (saveHistory) pushState(viewType = VersionView)
    }
    if (currentView == StatusView) {
      statusDialog.getSelectedSubsystem.foreach(showApiVersionHistory)
    }
    else {
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

  // Called when the "FITS Dictionary" item is selected
  private def showFitsDictionary(saveHistory: Boolean = true)(): Unit = {
    import icd.web.shared.JsonSupport._
    setSidebarVisible(false)
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
      currentView = FitsView
      if (saveHistory) pushState(viewType = FitsView)
    }
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Gets a PDF of the currently selected ICD or subsystem API
  private def makePdf(pdfOptions: PdfOptions): Unit = {
    import scalatags.JsDom.all._

    if (currentView == FitsView) {
      val tag = FitsKeywordDialog.getFitsTag
      val uri = ClientRoutes.fitsDictionaryAsPdf(tag, pdfOptions)
      dom.window.open(uri) // opens in new window or tab
    }
    else {
      val maybeSv =
        if (currentView == StatusView)
          statusDialog.getSubsystemWithVersion
        else selectDialog.subsystem.getSubsystemWithVersion()

      maybeSv.foreach { sv =>
        val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion()
        val maybeIcdVersion = selectDialog.icdChooser.getSelectedIcdVersion.map(_.icdVersion)
        val searchAll       = selectDialog.searchAllSubsystems()
        val clientApi       = selectDialog.clientApi()
        val uri             = ClientRoutes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion, searchAll, clientApi, pdfOptions)

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
  }

  private def generateCode(language: String): Unit = {
    import scalatags.JsDom.all._
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion()

    maybeSv.foreach { sv =>
      val className   = s"${sv.subsystem.toLowerCase().capitalize}Api"
      val packageName = s"${sv.subsystem.toLowerCase()}.api"
      val suffix = language
        .toLowerCase()
        .replace("typescript", "ts")
        .replace("python", "py")
      val sourceFile = s"$className.$suffix"
      val uri        = ClientRoutes.generate(sv, language, className, packageName)
      val f = Fetch.get(uri).map { text =>
        val link = document.createElement("a").asInstanceOf[HTMLAnchorElement]
        link.setAttribute("download", sourceFile)
        link.href = "data:," + URIUtils.encodeURIComponent(text)
        link.click();
      }
      showBusyCursorWhile(f)
    }
  }

  // Generates a graph of relationships for the currently selected components
  private def makeGraph(options: IcdVizOptions): Unit = {
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion()

    maybeSv.foreach { sv =>
      val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion()
      val maybeIcdVersion = selectDialog.icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val uri             = ClientRoutes.makeGraph(sv, maybeTargetSv, maybeIcdVersion, options)
      dom.window.open(uri) // opens in new window or tab
    }
  }

  // Gets a PDF with an Archived Items report for the currently selected subsystem API
  private def makeArchivedItemsReport(options: PdfOptions): Unit = {
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion()

    val uri =
      if (maybeSv.isDefined)
        ClientRoutes.archivedItemsReport(maybeSv.get, options)
      else
        ClientRoutes.archivedItemsReportFull(options)
    dom.window.open(uri) // opens in new window or tab
  }

  // Gets a PDF with a Missing Items report for the currently selected subsystem API
  private def makeMissingItemsReport(options: PdfOptions): Unit = {
    val maybeSv =
      if (currentView == StatusView)
        statusDialog.getSubsystemWithVersion
      else selectDialog.subsystem.getSubsystemWithVersion()

    val uri =
      if (maybeSv.isDefined) {
        val maybeTargetSv   = selectDialog.targetSubsystem.getSubsystemWithVersion()
        ClientRoutes.missingItemsReport(maybeSv.get, maybeTargetSv, options)
      } else {
        ClientRoutes.missingItemsReportFull(options)
      }
    dom.window.open(uri) // opens in new window or tab
  }

  private case class ReloadButton() extends Displayable {
    private def reloadPage(): Unit = {
      val main = document.getElementById("mainContent")
      val y    = main.scrollTop
      val f =
        if (currentView == StatusView)
          statusDialog.applySettings()
        else
          selectDialog.applySettings()
      f.onComplete {
        case Success(_) =>
          main.scrollTop = y
        case _ =>
      }
    }

    override def markup(): Element = {
      import scalatags.JsDom.all._
      import scalacss.ScalatagsCss._
      li(
        a(
          button(
            cls := "btn btn-sm",
            Styles.attributeBtn,
            tpe := "button",
            id := "reload",
            title := "Reload the selected subsystem, API or ICD, refresh from icd database",
            onclick := reloadPage _
          )(i(Styles.navbarBtn, cls := "bi bi-arrow-clockwise"))
        )
      ).render
    }
  }

}
