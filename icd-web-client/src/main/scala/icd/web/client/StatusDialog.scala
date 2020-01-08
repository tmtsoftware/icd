package icd.web.client

import icd.web.shared.{IcdVersionInfo, PublishInfo, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Div, Table}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scalatags.JsDom.all._
import scalacss.ScalatagsCss._
import scalatags.JsDom

object StatusDialog {
  val msg                    = "Select a subsystem to see the current status."
  val placeholderMsg: String = "Select subsystem"
}

/**
 * Displays the current published status of a selected subsystem.
 * @param mainContent used to display errors
 * @param selectDialog used to update the subsystem choice in the select dialog to match this dialog
 */
case class StatusDialog(mainContent: MainContent, selectDialog: SelectDialog) extends Displayable {
  import StatusDialog._

  // The subsystem combobox
  private val subsystemItem = {
    select(cls := "form-control", onchange := onSubsystemSelected _)(
      option(value := placeholderMsg, disabled := true, selected := true)(placeholderMsg)
    ).render
  }

  private val detailsDiv = {
    div().render
  }

  // called when a subsystem is selected
  private def onSubsystemSelected(e: dom.Event): Unit = {
    subsystemSelected(getSelectedSubsystem)
  }

  // called when a subsystem is selected
  private def subsystemSelected(maybeSubsystem: Option[String]): Unit = {
    detailsDiv.innerHTML = ""
    maybeSubsystem.foreach { subsystem =>
      detailsDiv.appendChild(p(em(s"Getting $subsystem related information...")).render)
      IcdUtil.getPublishInfo(maybeSubsystem, mainContent).onComplete {
        case Success(pubInfoList) =>
          detailsDiv.innerHTML = ""
          detailsDiv.appendChild(detailsMarkup(pubInfoList.head))
        case Failure(ex) =>
          mainContent.displayInternalError(ex)
      }
    }
  }

  // Input string looks like "2019-04-12T09:11:49.074Z", simplify by showing only the date
  private def formatDate(dateStr: String): String = {
    dateStr.substring(0, dateStr.indexOf('T'))
  }

  private def onlyLatestIcdVersions(list: List[IcdVersionInfo]): List[IcdVersionInfo] = {
    var result: List[IcdVersionInfo] = List()
    list.foreach { iv =>
      if (!result.exists(i => i.icdVersion.subsystem == iv.icdVersion.subsystem && i.icdVersion.target == iv.icdVersion.target))
        result = iv :: result
    }
    result.reverse
  }

  private def apiTable(pubInfo: PublishInfo): JsDom.TypedTag[Table] = {
    val apiVersionInfo = pubInfo.apiVersions.head
    // Make the subsystem selection in the select dialog match this one
    val sv = SubsystemWithVersion(apiVersionInfo.subsystem, Some(apiVersionInfo.version), None)
    selectDialog.subsystem.setSubsystemWithVersion(Some(sv))

    table(
      Styles.componentTable,
      attr("data-toggle") := "table",
      thead(
        tr(
          th("Subsystem"),
          th("API Version"),
          th("Date"),
          th("User"),
          th("Comment"),
          th("Status")
        )
      ),
      tbody(
        tr(
          td(apiVersionInfo.subsystem),
          td(apiVersionInfo.version),
          td(formatDate(apiVersionInfo.date)),
          td(apiVersionInfo.user),
          td(apiVersionInfo.comment),
          td(if (pubInfo.readyToPublish) "Ready to publish" else "No unpublished changes")
        )
      )
    )
  }

  private def icdTable(pubInfo: PublishInfo): JsDom.TypedTag[Table] = {
    table(
      Styles.componentTable,
      attr("data-toggle") := "table",
      thead(
        tr(
          th("Subsystem 1"),
          th("Subsystem 2"),
          th("ICD Version"),
          th("Date"),
          th("User"),
          th("Comment")
        )
      ),
      tbody(
        onlyLatestIcdVersions(pubInfo.icdVersions).map { icdVersionInfo =>
          val iv = icdVersionInfo.icdVersion
          tr(
            td(s"${iv.subsystem}-${iv.subsystemVersion}"),
            td(s"${iv.target}-${iv.targetVersion}"),
            td(iv.icdVersion),
            td(formatDate(icdVersionInfo.date)),
            td(icdVersionInfo.user),
            td(icdVersionInfo.comment)
          )
        }
      )
    )
  }

  // Returns the detailed information markup for the selected subsystem
  private def detailsMarkup(pubInfo: PublishInfo): Div = {
    div(cls := "panel panel-info")(
      div(cls := "panel-body")(
        div(
          h3(s"Current ${pubInfo.subsystem} API Status"),
          apiTable(pubInfo)
        ),
        if (pubInfo.icdVersions.nonEmpty) {
          div(
            h3(s"Current ICDs involving ${pubInfo.subsystem}"),
            icdTable(pubInfo)
          )
        } else {
          h3(s"No published ICDs involving ${pubInfo.subsystem} were found.")
        }
      )
    ).render
  }

  // Gets the currently selected subsystem name
  private def getSelectedSubsystem: Option[String] =
    subsystemItem.value match {
      case `placeholderMsg` => None
      case subsystemName    => Some(subsystemName)
    }

  /**
   * Sets (or clears) the selected subsystem and version.
   *
   * @param maybeSubsystem        optional subsystem name
   * @param saveHistory    if true, save the current state to the browser history
   * @return a future indicating when any event handlers have completed
   */
  def setSubsystem(
      maybeSubsystem: Option[String],
      saveHistory: Boolean = true
  ): Unit = {
    if (maybeSubsystem != getSelectedSubsystem) {
      maybeSubsystem match {
        case Some(subsystem) =>
          subsystemItem.value = subsystem
        case None =>
          subsystemItem.value = placeholderMsg
      }
      subsystemSelected(maybeSubsystem)
    }
  }

  /**
   * Gets the list of subsystems being displayed
   */
  def getSubsystems: List[String] = {
    val items = subsystemItem.options.toList
    items.drop(1).map(_.value)
  }

  /**
   * Update the Subsystem combobox options
   */
  def updateSubsystemOptions(items: List[String]): Unit = {
    val currentSubsystems = getSubsystems
    items.foreach { subsystem =>
      if (!currentSubsystems.contains(subsystem))
        subsystemItem.add(option(value := subsystem)(subsystem).render)
    }
  }

  override def markup(): Element = {
    div(
      cls := "container",
      div(Styles.statusDialogSubsystemRow, p(msg)),
      div(cls := "row")(
        div(Styles.statusDialogLabel)(label("Subsystem")),
        div(Styles.statusDialogSubsystem)(subsystemItem)
      ),
      p(""),
      detailsDiv
    ).render
  }
}
