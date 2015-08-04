package icd.web.client

import icd.web.shared.{ IcdVersionInfo, IcdName, VersionInfo }
import org.scalajs.dom.ext.Ajax
import upickle.default._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scalatags.JsDom.all._

/**
 * Manages the main content section
 */
case class VersionHistory(mainContent: MainContent) extends Displayable {
  private val contentDiv = div(id := "versionHistory").render

  // Returns the markup for displaying a table of version information for a subsystem
  private def markupSubsystemVersionInfo(subsystem: String, list: List[VersionInfo]) = {
    import scalacss.ScalatagsCss._
    if (list.isEmpty) div().render
    else div(
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th(subsystem, br, "Version"),
            th("User"),
            th("Date"),
            th("Comment"))),
        tbody(
          for (v ← list) yield {
            tr(
              td(v.version),
              td(v.user),
              td(Styles.noWrapTableColumn, v.date),
              td(v.comment))
          }))).render

  }

  // Returns the markup for displaying a table of version information for an ICD
  private def markupIcdVersionInfo(icdName: IcdName, list: List[IcdVersionInfo]) = {
    import scalacss.ScalatagsCss._
    if (list.isEmpty) div().render
    else div(
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("ICD", br, "Version"),
            th(icdName.subsystem, br, "Version"),
            th(icdName.target, br, "Version"),
            th("User"),
            th("Date"),
            th("Comment"))),
        tbody(
          for (v ← list) yield {
            val icdVersion = v.icdVersion
            tr(
              td(icdVersion.icdVersion),
              td(icdVersion.subsystemVersion),
              td(icdVersion.targetVersion),
              td(v.user),
              td(Styles.noWrapTableColumn, v.date),
              td(v.comment))
          }))).render

  }

  // Gets the subsystem version info from the server
  private def getSubsystemVersionInfo(subsystem: String): Future[List[VersionInfo]] =
    Ajax.get(Routes.versions(subsystem)).map { r ⇒
      read[List[VersionInfo]](r.responseText)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
        Nil
    }

  // Gets the ICD version info from the server
  private def getIcdVersionInfo(icdName: IcdName): Future[List[IcdVersionInfo]] = {
    import upickle.default._
    Ajax.get(Routes.icdVersions(icdName)).map { r ⇒
      read[List[IcdVersionInfo]](r.responseText)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
        Nil
    }
  }

  def setSubsystem(subsystem: String): Unit = {
    getSubsystemVersionInfo(subsystem).foreach { list ⇒
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupSubsystemVersionInfo(subsystem, list))
    }
  }

  def setIcd(icdName: IcdName): Unit = {
    getIcdVersionInfo(icdName).foreach { list ⇒
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupIcdVersionInfo(icdName, list))
    }
  }

  def markup() = contentDiv
}
