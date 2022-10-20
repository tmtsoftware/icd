package icd.web.client

import icd.web.client.IcdChooser.IcdListener
import icd.web.client.Subsystem.SubsystemListener
import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import play.api.libs.json._
import org.scalajs.dom.html.{Button, Input}
import SelectDialog._
import org.scalajs.dom.Element
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.Future
import scala.language.implicitConversions

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
 * @param items items to enable/disable
 */
//noinspection DuplicatedCode
case class SelectDialog(mainContent: MainContent, listener: SelectDialogListener, items: List[Displayable]) extends Displayable {

  val subsystem: Subsystem = Subsystem(SourceSubsystemListener)
  val targetSubsystem: Subsystem = Subsystem(
    TargetSubsystemListener,
    placeholderMsg = "Select Subsystem"
  )
  val subsystemSwapper: SubsystemSwapper = SubsystemSwapper(swapSubsystems)
  val icdChooser: IcdChooser             = IcdChooser(IcdChooserListener)

  // Displays the Apply button
  private val applyButton: Button = {
    import scalatags.JsDom.all._
    button(
      `type` := "submit",
      cls := "btn btn-primary",
      title := "Display the selected API or ICD",
      onclick := apply() _
    )("Apply").render
  }

  // Displays a checkbox for the "search all subsystems for API dependencies" option
  private val searchAllCheckbox: Input = {
    import scalatags.JsDom.all._
    input(`type` := "checkbox", cls := "form-check-input", disabled := true).render
  }

  // Displays a checkbox for the "include client API" option
  private val clientApiCheckbox: Input = {
    import scalatags.JsDom.all._
    input(`type` := "checkbox", cls := "form-check-input", disabled := true, onchange := clientApiCheckboxChanged() _).render
  }

  private def clientApiCheckboxChanged()(e: dom.Event): Unit = {
    val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
    searchAllCheckbox.disabled = maybeTargetSv.isDefined || !clientApi()
  }

  showBusyCursorWhile(icdChooser.updateIcdOptions())
  targetSubsystem.setEnabled(false)
  subsystemSwapper.setEnabled(false)
  applyButton.disabled = true

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
      items.foreach(_.setEnabled(true))
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion()
      targetSubsystem.setEnabled(maybeSv.isDefined)
      applyButton.disabled = maybeSv.isEmpty
      clientApiCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty
      searchAllCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty || !clientApi()
      maybeSv
        .map { sv =>
          for {
            _     <- if (findMatchingIcd) icdChooser.selectMatchingIcd(sv, maybeTargetSv) else Future.successful(())
            names <- getComponentNames(sv)
          } yield {
            subsystem.updateComponentOptions(names)
            subsystem.setSelectedComponent(sv.maybeComponent)
            subsystemSwapper.setEnabled(maybeSv.isDefined && maybeTargetSv.isDefined)
          }
        }
        .getOrElse(Future.successful(()))
    }
  }

  private object TargetSubsystemListener extends SubsystemListener {
    // Called when the target subsystem or version combobox selection is changed
    override def subsystemSelected(
        maybeTargetSv: Option[SubsystemWithVersion],
        findMatchingIcd: Boolean
    ): Future[Unit] = {
      val maybeSv = subsystem.getSubsystemWithVersion()
      clientApiCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty
      searchAllCheckbox.disabled = maybeTargetSv.isDefined || maybeSv.isEmpty || !clientApi()
      maybeSv
        .map { sv =>
          for {
            _     <- if (findMatchingIcd) icdChooser.selectMatchingIcd(sv, maybeTargetSv) else Future.successful(())
            names <- maybeTargetSv.map(getComponentNames).getOrElse(Future.successful(Nil))
          } yield {
            targetSubsystem.updateComponentOptions(names)
            maybeTargetSv.foreach(targetSv => targetSubsystem.setSelectedComponent(targetSv.maybeComponent))
            subsystemSwapper.setEnabled(maybeTargetSv.isDefined)
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
      // XXX TODO FIXME: Use of futures...
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
            val enabled = subsystem.getSelectedSubsystem.isDefined
            targetSubsystem.setEnabled(enabled)
            subsystemSwapper.setEnabled(enabled && targetSubsystem.getSelectedSubsystem.isDefined)
            applyButton.disabled = !enabled
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
  private def apply()(e: dom.Event): Unit = applySettings()

  override def markup(): Element = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(
      cls := "container",
      div(Styles.selectDialogSubsystemRow, p(msg)),
      div(Styles.selectDialogIcdRow, icdChooser.markup()),
      p(strong("Or")),
      div(Styles.selectDialogSubsystemRow, subsystem.markup()),
      div(Styles.subsystemSwapper, subsystemSwapper.markup()),
      div(Styles.selectDialogSubsystemRow, targetSubsystem.markup()),
      div(
        cls := "form-check",
        clientApiCheckbox,
        label(cls := "form-check-label", "Include client API information (subscribed events, sent commands)")
      ),
      div(
        cls := "form-check",
        searchAllCheckbox,
        label(cls := "form-check-label", "Search all TMT subsystems for API dependencies")
      ),
      div(Styles.selectDialogApplyButton, applyButton)
    ).render
  }
}
