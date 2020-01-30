package icd.web.client

import icd.web.client.StatusDialog.StatusDialogListener
import icd.web.shared.{ApiVersionInfo, IcdVersion, IcdVersionInfo, PublishInfo, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Div, Table}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scalatags.JsDom.all._
import scalacss.ScalatagsCss._
import scalatags.JsDom

object StatusDialog {

  /**
   * Type of a listener for actions in the Status dialog
   */
  trait StatusDialogListener {
    def apiSelected(sv: SubsystemWithVersion): Unit
    def icdSelected(icdVersion: IcdVersion): Unit
  }

  val msg                    = "Select a subsystem to see the current status."
  val placeholderMsg: String = "Select subsystem"
}

/**
 * Displays the current published status of a selected subsystem.
 * @param mainContent used to display errors
 */
case class StatusDialog(mainContent: MainContent, listener: StatusDialogListener) extends Displayable {
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
      val f = IcdUtil.getPublishInfo(maybeSubsystem, mainContent)
      f.onComplete {
        case Success(pubInfoList) =>
          detailsDiv.innerHTML = ""
          detailsDiv.appendChild(detailsMarkup(pubInfoList.head))
        case Failure(ex) =>
          mainContent.displayInternalError(ex)
      }
      showBusyCursorWhile(f.map(_ => ()))
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

  // Action when user clicks on an API version to view the API
  private def clickedOnApi(apiVersionInfo: ApiVersionInfo)(e: dom.Event): Unit = {
    e.preventDefault()
    listener.apiSelected(SubsystemWithVersion(apiVersionInfo.subsystem, Some(apiVersionInfo.version), None))
  }

  // Action when user clicks on an ICD version to view the ICD
  private def clickedOnIcd(iv: IcdVersion)(e: dom.Event): Unit = {
    e.preventDefault()
    listener.icdSelected(iv)
  }

  private def apiTable(pubInfo: PublishInfo): JsDom.TypedTag[Table] = {
    val apiVersionInfo = pubInfo.apiVersions.head
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
          td(
            a(
              title := s"Select this API for viewing",
              apiVersionInfo.version,
              href := "#",
              onclick := clickedOnApi(apiVersionInfo) _
            )
          ),
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
            td(
              a(
                title := s"Select this ICD for viewing",
                iv.icdVersion,
                href := "#",
                onclick := clickedOnIcd(iv) _
              )
            ),
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
        if (pubInfo.apiVersions.nonEmpty) {
          div(
            h3(s"Current ${pubInfo.subsystem} API Status"),
            apiTable(pubInfo)
          )
        } else {
          h3(s"No published APIs for ${pubInfo.subsystem} were found.")
        },
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
  def getSelectedSubsystem: Option[String] =
    subsystemItem.value match {
      case `placeholderMsg` => None
      case subsystemName    => Some(subsystemName)
    }

  // Gets the currently selected subsystem with version
  def getSubsystemWithVersion: Option[SubsystemWithVersion] =
    subsystemItem.value match {
      case `placeholderMsg` => None
      case subsystemName    =>
        Some(SubsystemWithVersion(subsystemName, None, None))
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
