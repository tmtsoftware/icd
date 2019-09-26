package icd.web.client

import icd.web.client.BrowserHistory.{ComponentView, IcdView}
import icd.web.client.IcdChooser.IcdListener
import icd.web.client.Subsystem.SubsystemListener
import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom._
import play.api.libs.json._
import org.querki.jquery._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.html.Button
import SelectDialog._

import scala.concurrent.ExecutionContext.Implicits.global
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
     * @return a future indicating when changes are done
     */
    def subsystemsSelected(
        maybeSv: Option[SubsystemWithVersion],
        maybeTargetSv: Option[SubsystemWithVersion],
        maybeIcd: Option[IcdVersion]
    ): Future[Unit]
  }

  /**
   * Value displayed for the unpublished working version of the subsystem
   */
  private val unpublishedVersion = "*"

  private val msg = "Select an ICD from the list or one or two subsystems (and optional components)"
}

/**
 * Displays the page for selecting the icds, subsystem APIs, components and versions to display
 */
case class SelectDialog(subsystemNames: SubsystemNames, mainContent: MainContent, listener: SelectDialogListener)
    extends Displayable {

  val subsystem = Subsystem(SourceSubsystemListener)
  val targetSubsystem = Subsystem(
    TargetSubsystemListener,
    labelStr = "Target",
    placeholderMsg = "All",
    enablePlaceholder = true
  )
  val subsystemSwapper = SubsystemSwapper(swapSubsystems)
  val icdChooser       = IcdChooser(IcdChooserListener)

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

  icdChooser.updateIcdOptions()

  // Update the list of Subsystem options
  def updateSubsystemOptions(items: List[String]): Unit = {
    subsystem.updateSubsystemOptions(items)
    targetSubsystem.updateSubsystemOptions(items)
  }

  // Gets the list of subcomponents for the selected subsystem
  private def getComponentNames(sv: SubsystemWithVersion): Future[List[String]] = {
    if (sv.maybeComponent.isDefined) {
      Future(List(sv.maybeComponent.get))
    } else {
      val path = Routes.components(sv.subsystem, sv.maybeVersion)
      Ajax
        .get(path)
        .map { r =>
          Json.fromJson[List[String]](Json.parse(r.responseText)).get
        }
        .recover {
          case ex =>
            mainContent.displayInternalError(ex)
            Nil
        }
    }
  }

  private object SourceSubsystemListener extends SubsystemListener {
    // Called when the source subsystem (or version) combobox selection is changed
    override def subsystemSelected(maybeSv: Option[SubsystemWithVersion], saveHistory: Boolean): Future[Unit] = {
      val maybeTargetSv = targetSubsystem.getSubsystemWithVersion
      maybeSv
        .map { sv =>
          val icdF            = icdChooser.selectMatchingIcd(sv, maybeTargetSv)
          val componentNamesF = getComponentNames(sv)
          for {
            _     <- icdF
            names <- componentNamesF
          } yield {
            // XXX TODO Update component menu
          }
        }
        .getOrElse(Future.successful())
    }
  }

  private object TargetSubsystemListener extends SubsystemListener {
    // Called when the target subsystem or version combobox selection is changed
    override def subsystemSelected(maybeTargetSv: Option[SubsystemWithVersion], saveHistory: Boolean): Future[Unit] = {
      val maybeSv = subsystem.getSubsystemWithVersion
      maybeSv
        .map { sv =>
          val icdF            = icdChooser.selectMatchingIcd(sv, maybeTargetSv)
          val componentNamesF = maybeTargetSv.map(getComponentNames).getOrElse(Future.successful(Nil))
          for {
            _     <- icdChooser.selectMatchingIcd(sv, maybeTargetSv)
            names <- componentNamesF
          } yield {
            // XXX TODO: Update target component menu
          }
        }
        .getOrElse(Future.successful())
    }
  }

  // Swap source and target subsystems
  private def swapSubsystems(): Unit = {
    for {
      sv2 <- targetSubsystem.getSubsystemWithVersion
      sv1 <- subsystem.getSubsystemWithVersion
    } {
      icdChooser.selectMatchingIcd(sv2, Some(sv1))
      targetSubsystem.setSubsystemWithVersion(Some(sv1), notifyListener = false, saveHistory = false)
      subsystem.setSubsystemWithVersion(Some(sv2))
    }
  }

  private object IcdChooserListener extends IcdListener {
    // Called when the ICD (or ICD version) combobox selection is changed
    override def icdSelected(maybeIcdVersion: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit] = {
      maybeIcdVersion match {
        case Some(icdVersion) =>
          val sv       = SubsystemWithVersion(icdVersion.subsystem, Some(icdVersion.subsystemVersion), None)
          val targetSv = SubsystemWithVersion(icdVersion.target, Some(icdVersion.targetVersion), None)
          for {
            _ <- subsystem.setSubsystemWithVersion(Some(sv), notifyListener = false, saveHistory = false)
            _ <- targetSubsystem.setSubsystemWithVersion(Some(targetSv), notifyListener = false, saveHistory = false)
          } yield {}
        case None => Future.successful()
      }
    }
  }

  // Displays the selected API or ICD
  private def apply()(e: dom.Event): Unit = {
    val maybeSv       = subsystem.getSubsystemWithVersion
    val maybeTargetSv = targetSubsystem.getSubsystemWithVersion
    val maybeIcd      = icdChooser.getSelectedIcdVersion
    listener.subsystemsSelected(maybeSv, maybeTargetSv, maybeIcd)
  }

  override def markup(): Element = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    form(
      div(Styles.selectDialogRow, p(msg)),
      div(Styles.selectDialogRow, icdChooser.markup()),
      div(Styles.selectDialogRow, subsystem.markup()),
      div(Styles.subsystemSwapper, subsystemSwapper.markup()),
      div(Styles.selectDialogRow, targetSubsystem.markup()),
      div(Styles.selectDialogApplyButton, applyButton)
    ).render
  }
}
