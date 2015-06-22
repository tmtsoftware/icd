package icd.web.client

import org.scalajs.dom.ext.Ajax
import shared.VersionInfo
import upickle._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import scalatags.JsDom.all._

/**
 * Manages the main content section
 */
case class VersionHistory(mainContent: MainContent) extends Displayable {
  private val contentDiv = div(id := "versionHistory").render

  // Returns the markup for displaying a table of version information
  private def markupVersionInfo(name: String, list: List[VersionInfo]) = {
    import scalacss.ScalatagsCss._
    if (list.isEmpty) div().render
    else div(
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Version"),
            th("User"),
            th("Comment"),
            th("Date"))),
        tbody(
          for (v ← list) yield {
            tr(
              td(v.version),
              td(v.user),
              td(v.comment),
              td(v.date))
          }))).render

  }

  // Gets the version info from the server
  private def getVersionInfo(subsystem: String): Future[List[VersionInfo]] =
    Ajax.get(Routes.versions(subsystem)).map { r ⇒
      read[List[VersionInfo]](r.responseText)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
        Nil
    }

  def setSubsystem(subsystem: String): Unit = {
    getVersionInfo(subsystem).foreach { list ⇒
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupVersionInfo(subsystem, list))
    }
  }

  def markup() = contentDiv
}
