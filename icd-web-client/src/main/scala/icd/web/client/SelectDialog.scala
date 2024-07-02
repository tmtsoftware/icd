package icd.web.client

import icd.web.client.IcdChooser.IcdListener
import icd.web.client.Subsystem.SubsystemListener
import icd.web.shared.{IcdName, IcdVersion, IcdVizOptions, PdfOptions, SubsystemWithVersion}
import org.scalajs.dom
import play.api.libs.json.*
import org.scalajs.dom.html.{Button, Input}
import SelectDialog.*
import icd.web.client.BrowserHistory.VersionView
import org.scalajs.dom.{Element, HTMLAnchorElement, document}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.scalajs.js.URIUtils

/**
 *
 */
object SelectDialog {

  /**
   * Type of a listener for changes in the selected subsystem
   */
  trait SelectDialogListener {

    /**
     * Called when the apply button is pressed
     *
     * @param maybeSv optional selected subsystem and version
     * @param maybeTargetSv optional selected target subsystem and version
     * @param maybeIcd optional selected icd and version
     * @param searchAllSubsystems if true, search all subsystems for dependencies (subscribers, senders)
     * @param clientApi if true, include subscribed events, sent commands in API
     * @return a future indicating when changes are done
     */
    def subsystemsSelected(
        maybeSv: Option[SubsystemWithVersion],
        maybeTargetSv: Option[SubsystemWithVersion],
        maybeIcd: Option[IcdVersion],
        searchAllSubsystems: Boolean,
        clientApi: Boolean
    ): Future[Unit]
  }

  private val msg =
    """
      |Choose an ICD and version to display below. Or select only a single subsystem to see the API.
      |Selecting two subsystems displays the ICD between the two.
      |To narrow the focus, you can optionally select a component in each subsystem.
      |To display only a subsystem API again, make sure the second subsystem is set to "Select Subsystem".
      |""".stripMargin
}

/**
 * Displays the page for selecting the icds, subsystem APIs, components and versions to display
 * @param mainContent used to display errors
 * @param listener called for actions
 */
//noinspection DuplicatedCode
case class SelectDialog(mainContent: MainContent, listener: SelectDialogListener) extends Displayable {

  private val historyDialog = HistoryDialog(mainContent)

  private val pdfButton =
    PdfButtonItem(
      "PDF",
      "selectpdf",
      "Generate and display a PDF for the API or ICD",
      makePdf,
      showDocumentNumber = true,
      showDetailButtons = false
    )
  pdfButton.setEnabled(false)

  private val generateButton = DropDownButtonItem(
    "Generate",
    "Generate code for the selected API/component",
    List("Scala", "Java", "TypeScript", "Python"),
    generateCode
  )
  generateButton.setEnabled(false)

  private val graphButton =
    GraphButtonItem("Graph", "Generate and display a graph of relationships for the selected components", makeGraph)
  graphButton.setEnabled(false)

  private val archiveButton =
    PdfButtonItem(
      "Archive",
      "archivepdf",
      "Generate and display an 'Archived Items' report for the selected subsystem/component (or all subsystems)",
      makeArchivedItemsReport,
      showDocumentNumber = false,
      showDetailButtons = false
    )

  private val alarmsButton =
    PdfButtonItem(
      "Alarms",
      "alarmpdf",
      "Generate and display an 'Alarms' report for the selected subsystem/component (or all subsystems)",
      makeAlarmsReport,
      showDocumentNumber = false,
      showDetailButtons = false
    )

  private val missingButton =
    PdfButtonItem(
      "Missing",
      "missingpdf",
      "Generate and display a 'Missing Items' report for the selected subsystems/components (or all subsystems)",
      makeMissingItemsReport,
      showDocumentNumber = false,
      showDetailButtons = false
    )

  val subsystem: Subsystem = Subsystem(SourceSubsystemListener)
  val targetSubsystem: Subsystem = Subsystem(
    TargetSubsystemListener,
    placeholderMsg = "Select Subsystem"
  )
  private val subsystemSwapper: SubsystemSwapper = SubsystemSwapper(swapSubsystems)
  val icdChooser: IcdChooser                     = IcdChooser(IcdChooserListener)

  // Displays the Apply button
  private val applyButton: Button = {
    import scalatags.JsDom.all.*

    button(
      `type` := "button",
      cls := "btn btn-primary",
      title := "Display the selected API or ICD",
      onclick := apply() _
    )("Apply").render
  }
  applyButton.disabled = true

  private val historyButton: Button = {
    import scalatags.JsDom.all.*

    button(
      `type` := "button",
      cls := "btn btn-secondary",
      title := "Display the version history for an API or ICD",
      onclick := showVersionHistory() _
    )("History").render
  }
  historyButton.disabled = true

  // Displays a checkbox for the "search all subsystems for API dependencies" option
  private val searchAllCheckbox: Input = {
    import scalatags.JsDom.all.*
    input(`type` := "checkbox", cls := "form-check-input", disabled := true).render
  }

  // Displays a checkbox for the "include client API" option
  private val clientApiCheckbox: Input = {
    import scalatags.JsDom.all.*
    input(`type` := "checkbox", cls := "form-check-input", disabled := true, onchange := clientApiCheckboxChanged() _).render
  }

  //noinspection ScalaUnusedSymbol
  private def clientApiCheckboxChanged()(e: dom.Event): Unit = {
    val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
    searchAllCheckbox.disabled = maybeTargetSv.isDefined || !clientApi()
  }

  showBusyCursorWhile(icdChooser.updateIcdOptions())
  targetSubsystem.setEnabled(false)
  subsystemSwapper.setEnabled(false)

  def searchAllSubsystems(): Boolean = {
    searchAllCheckbox.checked && clientApiCheckbox.checked
  }

  def clientApi(): Boolean = {
    clientApiCheckbox.checked
  }

  // Update the list of Subsystem options
  def updateSubsystemOptions(items: List[String]): Future[Unit] = {
    for {
      _ <- subsystem.updateSubsystemOptions(items)
      _ <- targetSubsystem.updateSubsystemOptions(items)
    } yield {}
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(sv: SubsystemWithVersion): Future[List[String]] = {
    val path = ClientRoutes.components(sv.subsystem, sv.maybeVersion)
    Fetch
      .get(path)
      .map { text =>
        Json.fromJson[Array[String]](Json.parse(text)).map(_.toList).get
      }
  }

  private object SourceSubsystemListener extends SubsystemListener {
    // Called when the source subsystem (or version) combobox selection is changed
    override def subsystemSelected(
        maybeSv: Option[SubsystemWithVersion],
        findMatchingIcd: Boolean
    ): Future[Unit] = {
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
      targetSubsystem.setEnabled(maybeSv.isDefined)
      applyButton.disabled = maybeSv.isEmpty
      pdfButton.setEnabled(maybeSv.isDefined)
      graphButton.setEnabled(maybeSv.isDefined)
      generateButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      archiveButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      alarmsButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      clientApiCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty
      searchAllCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty || !clientApi()
      historyButton.disabled = maybeSv.isEmpty
      maybeSv
        .map { sv =>
          for {
            _     <- if (findMatchingIcd) icdChooser.selectMatchingIcd(sv, maybeTargetSv) else Future.successful(())
            names <- getComponentNames(sv)
          } yield {
            subsystem.updateComponentOptions(names)
            subsystem.setSelectedComponent(sv.maybeComponent)
            subsystemSwapper.setEnabled(maybeSv.isDefined && maybeTargetSv.isDefined)
            historyButton.disabled = maybeSv.isEmpty || (maybeTargetSv.isDefined && icdChooser.getSelectedIcd.isEmpty)
          }
        }
        .getOrElse {
          subsystem.updateComponentOptions(Nil)
          targetSubsystem.setSubsystemWithVersion(None)
          targetSubsystem.updateComponentOptions(Nil)
          icdChooser.setIcdWithVersion(None, notifyListener = false, saveHistory = false)
          Future.successful(())
        }
    }
  }

  private object TargetSubsystemListener extends SubsystemListener {
    // Called when the target subsystem or version combobox selection is changed
    override def subsystemSelected(
        maybeTargetSv: Option[SubsystemWithVersion],
        findMatchingIcd: Boolean
    ): Future[Unit] = {
      val maybeSv = subsystem.getSubsystemWithVersion()
      generateButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      archiveButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      alarmsButton.setEnabled(maybeSv.isDefined && maybeTargetSv.isEmpty)
      clientApiCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty
      searchAllCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty || !clientApi()
      historyButton.disabled = maybeSv.isEmpty
      maybeSv
        .map { sv =>
          for {
            _     <- if (findMatchingIcd) icdChooser.selectMatchingIcd(sv, maybeTargetSv) else Future.successful(())
            names <- maybeTargetSv.map(getComponentNames).getOrElse(Future.successful(Nil))
          } yield {
            targetSubsystem.updateComponentOptions(names)
            maybeTargetSv.foreach(targetSv => targetSubsystem.setSelectedComponent(targetSv.maybeComponent))
            subsystemSwapper.setEnabled(maybeTargetSv.isDefined)
            historyButton.disabled = maybeSv.isEmpty || (maybeTargetSv.isDefined && icdChooser.getSelectedIcd.isEmpty)
          }
        }
        .getOrElse(Future.successful(()))
    }
  }

  // Swap source and target subsystems
  private def swapSubsystems(): Unit = {
    for {
      sv2 <- targetSubsystem.getSubsystemWithVersion()
      sv1 <- subsystem.getSubsystemWithVersion()
    } {
      icdChooser.selectMatchingIcd(sv2, Some(sv1))
      val selectedSubsystemComponent       = subsystem.getSelectedComponent
      val selectedTargetSubsystemComponent = targetSubsystem.getSelectedComponent
      subsystem.setSubsystemWithVersion(Some(sv2), findMatchingIcd = false)
      subsystem.setSelectedComponent(selectedTargetSubsystemComponent)
      targetSubsystem.setSubsystemWithVersion(Some(sv1), findMatchingIcd = false)
      targetSubsystem.setSelectedComponent(selectedSubsystemComponent)
    }
  }

  private object IcdChooserListener extends IcdListener {
    // Called when the ICD (or ICD version) combobox selection is changed
    override def icdSelected(maybeIcdVersion: Option[IcdVersion]): Future[Unit] = {
      maybeIcdVersion match {
        case Some(icdVersion) =>
          val sv       = SubsystemWithVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion), None)
          val targetSv = SubsystemWithVersion(icdVersion.target, Some(icdVersion.targetVersion), None)
          for {
            _ <- subsystem.setSubsystemWithVersion(Some(sv), findMatchingIcd = false)
            _ <- targetSubsystem.setSubsystemWithVersion(Some(targetSv), findMatchingIcd = false)
          } yield {
            targetSubsystem.setEnabled(true)
            subsystemSwapper.setEnabled(true)
            applyButton.disabled = false
            historyButton.disabled = false
          }
        case None => Future.successful(())
      }
    }
  }

  // Display the selected subsystems and components
  def applySettings(): Future[Unit] = {
    val maybeSv       = subsystem.getSubsystemWithVersion()
    val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
    val maybeIcd      = icdChooser.getSelectedIcdVersion
    val showClientApi = clientApi() || maybeTargetSv.isDefined
    val searchAll     = searchAllSubsystems() && maybeTargetSv.isEmpty
    listener.subsystemsSelected(maybeSv, maybeTargetSv, maybeIcd, searchAll, showClientApi)
  }

  // Called when the Apply button is pressed
  //noinspection ScalaUnusedSymbol
  private def apply()(e: dom.Event): Unit = applySettings()

  override def markup(): Element = {
    import scalatags.JsDom.all.*

    div(
      cls := "container",
      div(cls := "selectDialogSubsystemRow", p(msg)),
      div(cls := "selectDialogIcdRow", icdChooser.markup()),
      p(strong("Or")),
      div(cls := "selectDialogSubsystemRow", subsystem.markup()),
      div(id := "subsystemSwapper", subsystemSwapper.markup()),
      div(cls := "selectDialogSubsystemRow", targetSubsystem.markup()),
      div(
        style := "padding-top: 20px",
        cls := "form-check",
        clientApiCheckbox,
        label(cls := "form-check-label", "Include client API information (subscribed events, sent commands)")
      ),
      div(
        style := "padding-top: 10px",
        cls := "form-check",
        searchAllCheckbox,
        label(cls := "form-check-label", "Search all TIO subsystems for API dependencies")
      ),
      div(
        style := "padding-top: 20px",
        span(cls := "selectDialogButton")(applyButton),
        span(cls := "selectDialogButton")(historyButton),
        pdfButton.markup(),
        generateButton.markup(),
        graphButton.markup(),
        archiveButton.markup(),
        alarmsButton.markup(),
        missingButton.markup()
      )
    ).render
  }

  // Called when the "History" item is selected
  def showVersionHistory(saveHistory: Boolean = true)(): Unit = {
    def showApiVersionHistory(subsystem: String): Unit = {
      historyDialog.setSubsystem(subsystem)
      setSidebarVisible(false)
      mainContent.setContent(historyDialog, s"Subsystem API Version History: $subsystem")
      if (saveHistory) pushState(viewType = VersionView)
    }
    def showIcdVersionHistory(icdName: IcdName): Unit = {
      historyDialog.setIcd(icdName)
      setSidebarVisible(false)
      mainContent.setContent(historyDialog, s"ICD Version History: ${icdName.subsystem} to ${icdName.target}")
      if (saveHistory) pushState(viewType = VersionView)
    }
    icdChooser.getSelectedIcd match {
      case Some(icdName) =>
        showIcdVersionHistory(icdName)
      case None =>
        subsystem.getSelectedSubsystem.foreach(showApiVersionHistory)
    }
  }

  // Gets a PDF of the currently selected ICD or subsystem API
  private def makePdf(pdfOptions: PdfOptions): Unit = {
    import scalatags.JsDom.all.*

    val maybeSv = subsystem.getSubsystemWithVersion()
    maybeSv.foreach { sv =>
      val maybeTargetSv   = targetSubsystem.getSubsystemWithVersion()
      val maybeIcdVersion = icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val searchAll       = searchAllSubsystems()
      val isClientApi     = clientApi()
      val uri             = ClientRoutes.icdAsPdf(sv, maybeTargetSv, maybeIcdVersion, searchAll, isClientApi, pdfOptions)
      dom.window.open(uri) // opens in new window or tab
    }
  }

  private def generateCode(language: String): Unit = {
    import scalatags.JsDom.all.*
    val maybeSv = subsystem.getSubsystemWithVersion()
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
    val maybeSv = subsystem.getSubsystemWithVersion()
    maybeSv.foreach { sv =>
      val maybeTargetSv   = targetSubsystem.getSubsystemWithVersion()
      val maybeIcdVersion = icdChooser.getSelectedIcdVersion.map(_.icdVersion)
      val uri             = ClientRoutes.makeGraph(sv, maybeTargetSv, maybeIcdVersion, options)
      dom.window.open(uri) // opens in new window or tab
    }
  }

  // Gets a PDF with an Archived Items report for the currently selected subsystem API
  private def makeArchivedItemsReport(options: PdfOptions): Unit = {
    val maybeSv = subsystem.getSubsystemWithVersion()
    val uri =
      if (maybeSv.isDefined)
        ClientRoutes.archivedItemsReport(maybeSv.get, options)
      else
        ClientRoutes.archivedItemsReportFull(options)
    dom.window.open(uri) // opens in new window or tab
  }

  // Gets a PDF with an Alarms report for the currently selected subsystem API
  private def makeAlarmsReport(options: PdfOptions): Unit = {
    val maybeSv = subsystem.getSubsystemWithVersion()
    val uri =
      if (maybeSv.isDefined)
        ClientRoutes.alarmsReport(maybeSv.get, options)
      else
        ClientRoutes.alarmsReportFull(options)
    dom.window.open(uri) // opens in new window or tab
  }

  // Gets a PDF with a Missing Items report for the currently selected subsystem API
  private def makeMissingItemsReport(options: PdfOptions): Unit = {
    val maybeSv = subsystem.getSubsystemWithVersion()
    val uri =
      if (maybeSv.isDefined) {
        val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
        ClientRoutes.missingItemsReport(maybeSv.get, maybeTargetSv, options)
      }
      else {
        ClientRoutes.missingItemsReportFull(options)
      }
    dom.window.open(uri) // opens in new window or tab
  }
}
