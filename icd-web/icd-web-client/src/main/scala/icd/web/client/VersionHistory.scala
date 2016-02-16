package icd.web.client

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{HTMLInputElement, HTMLElement}
import upickle.default._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.querki.jquery._

import scalatags.JsDom.all._

object VersionHistory {
  // Split a version string into (maj.min)
  private def splitVersion(v: String): (String, String) = {
    val ar = v.split('.')
    if (ar.length == 2)
      (ar(0), ar(1))
    else
      ("", "")
  }

  // Compare two version strings (maj.min)
  private def compareVersions(v1: String, v2: String): Boolean = {
    val (maj1, min1) = splitVersion(v1)
    val (maj2, min2) = splitVersion(v2)
    maj1 < maj2 || maj1 == maj2 && min1 < min2
  }
}

/**
 * Manages the main content section
 */
case class VersionHistory(mainContent: MainContent) extends Displayable {
  import VersionHistory._

  // Main version div
  private val contentDiv = div(id := "versionHistory").render

  // Displays the Compare button
  private def compareButton(subsystem: String) = {
    import scalatags.JsDom.all._
    button(
      title := "Compare two selected subsystem versions",
      onclick := compareHandler(subsystem) _
    )("Compare").render
  }

  // Displays the diff of two versions
  private val diffDiv = div(id := "versionDiff").render

  // Called when the Compare button is pressed
  private def compareHandler(subsystem: String)(e: dom.Event): Unit = {
    val checked = $("input[name='version']:checked")
    if (checked.length == 2) {
      val versions = checked.mapElems(elem ⇒ elem.asInstanceOf[HTMLInputElement].value).sortWith(compareVersions).toList
      val route = Routes.diff(subsystem, versions)
      Ajax.get(route).map { r ⇒
        val list = read[List[DiffInfo]](r.responseText)
        diffDiv.innerHTML = ""
        diffDiv.appendChild(markupDiff(subsystem, list))
      }
    }
  }

  // Display the results of comparing two versions
  private def markupDiff(subsystem: String, list: List[DiffInfo]) = {

    def diffItemMarkup(diffItem: DiffItem) = {
      val headings = diffItem.changes.map(_.key)
      val rows = List(diffItem.changes.map(_.value))
      Components.mkTable(headings, rows)
    }

    def formatPath(path: String): String = {
      val l = path.split('.').tail
      if (l.length == 2) {
        val (component, section) = (l(0), l(1))
        s"Changes to component $component in the $section section"
      } else path
    }

    def diffInfoMarkup(diffInfo: DiffInfo) = {
      div(
        h3(formatPath(diffInfo.path)),
        diffInfo.items.map(diffItemMarkup)
      )
    }

    div(
      h2(s"Changes to $subsystem"),
      list.map(diffInfoMarkup)
    ).render
  }

  // Called when one of the version checkboxes is clicked to update the enabled state of the compare
  // button when exactly two items are selected
  private def checkboxListener(compButton: HTMLElement)(e: dom.Event): Unit = {
    val checked = $("input[name='version']:checked")
    compButton.disabled = checked.length != 2
  }

  // Returns a checkbox displaying the version (select two to compare versions)
  private def makeVersionCheckBox(version: Option[String], compButton: HTMLElement) = {
    div(cls := "checkbox")(
      label(
        input(
          name := "version",
          title := s"Select this version for comparison",
          tpe := "checkbox",
          onchange := checkboxListener(compButton) _,
          value := version.getOrElse("")
        ),
        version
      )
    )
  }

  // Returns the markup for displaying a table of version information for a subsystem
  private def markupSubsystemVersionInfo(subsystem: String, list: List[VersionInfo]) = {
    import scalacss.ScalatagsCss._
    if (list.isEmpty) div().render
    else {
      val compButton = compareButton(subsystem)
      div(
        table(Styles.componentTable, "data-toggle".attr := "table",
          thead(
            tr(
              th(subsystem, br, "Version"),
              th("User"),
              th("Date"),
              th("Comment")
            )
          ),
          tbody(
            for (v ← list) yield {
              tr(
                td(makeVersionCheckBox(v.version, compButton)),
                td(v.user),
                td(Styles.noWrapTableColumn, v.date),
                td(v.comment)
              )
            }
          )), compButton, diffDiv
      ).render
    }

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
            th("Comment")
          )
        ),
        tbody(
          for (v ← list) yield {
            val icdVersion = v.icdVersion
            tr(
              td(icdVersion.icdVersion),
              td(icdVersion.subsystemVersion),
              td(icdVersion.targetVersion),
              td(v.user),
              td(Styles.noWrapTableColumn, v.date),
              td(v.comment)
            )
          }
        ))
    ).render

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
      diffDiv.innerHTML = ""
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupSubsystemVersionInfo(subsystem, list))
    }
  }

  def setIcd(icdName: IcdName): Unit = {
    getIcdVersionInfo(icdName).foreach { list ⇒
      diffDiv.innerHTML = ""
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupIcdVersionInfo(icdName, list))
    }
  }

  def markup() = contentDiv
}
