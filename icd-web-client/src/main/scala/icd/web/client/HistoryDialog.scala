package icd.web.client

import icd.web.shared.*
import org.scalajs.dom
import org.scalajs.dom.HTMLInputElement
import play.api.libs.json.*

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import org.scalajs.dom.{Element, document}
import org.scalajs.dom.html.Button
import scalatags.JsDom.all.*

object HistoryDialog {
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
    if (v1 == uploadedVersion) false
    else if (v2 == uploadedVersion) true
    else if (v1 == masterVersion) false
    else if (v2 == masterVersion) true
    else {
      val (maj1, min1) = splitVersion(v1)
      val (maj2, min2) = splitVersion(v2)
      maj1 < maj2 || maj1 == maj2 && min1 < min2
    }
  }
}

/**
 * Displays a table with the version history
 */
case class HistoryDialog(mainContent: MainContent) extends Displayable {

  import HistoryDialog.*
  import icd.web.shared.JsonSupport.*

  // Main version div
  private val contentDiv = div(id := "versionHistory").render

  // Displays the Compare button
  private def compareButton(subsystem: String): Button = {
    import scalatags.JsDom.all.*
    button(
      disabled := true,
      `type` := "submit",
      cls := "btn btn-primary",
      title := "Compare two selected subsystem versions",
      onclick := compareHandler(subsystem)
    )("Compare").render
  }

  // Displays the diff of two versions
  private val diffDiv = div(id := "versionDiff", style := "padding-bottom: 20px").render

  //noinspection ScalaUnusedSymbol
  // Called when the Compare button is pressed
  private def compareHandler(subsystem: String)(e: dom.Event): Unit = {
    val checked = document.querySelectorAll("input[name='version']:checked").toList
    if (checked.size == 2) {
      val versions = checked.map(elem => elem.asInstanceOf[HTMLInputElement].value).sortWith(compareVersions)
      val route    = ClientRoutes.diff(subsystem, versions)
      Fetch.get(route).map { text =>
        val list = Json.fromJson[Array[DiffInfo]](Json.parse(text)).map(_.toList).getOrElse(Nil)
        diffDiv.innerHTML = ""
        diffDiv.appendChild(markupDiff(subsystem, list))
      }
    }
  }

  // Display the results of comparing two versions
  private def markupDiff(subsystem: String, list: List[DiffInfo]) = {

    def jsonDiffMarkup(infoList: List[JsonDiff]) = {


      val headings = List("Operation", "Path", "Old Value", "New Value")

      // Display quoted strings as just the text, but display json objects as objects
      def displayJson(json: String) = {
        if (json.startsWith("\"")) {
          div(json.substring(1, json.length - 1).replace("\\n", "\n").trim.split("\n").map(s => p(s)))
        }
        else {
          pre(code(cls := "unstyledPre", Json.prettyPrint(Json.parse(json))))
        }
      }

      table(
        attr("data-bs-toggle") := "table",
        thead(
          tr(headings.map(th(_)))
        ),
        tbody(
          for (i <- infoList) yield {
            tr(
              td(p(i.op)),
              td(p(i.path)),
              td(div(cls := "scrollableDiv", displayJson(i.old))),
              td(div(cls := "scrollableDiv", displayJson(i.value)))
            )
          }
        )
      )

    }

    def formatPath(path: String): String = {
      val l = path.split('.').tail
      if (l.length == 2) {
        val (component, section) = (l(0), l(1))
        s"Changes to component $component in the $section section"
      }
      else path
    }

    def diffInfoMarkup(diffInfo: DiffInfo) = {
      val jsValue = Json.parse(diffInfo.jsonDiff)
      val infoList = Json.fromJson[Array[JsonDiff]](jsValue) match {
        case JsSuccess(ar, _: JsPath) =>
          ar.toList
        case e: JsError =>
          println(s"${JsError.toJson(e).toString()}")
          Nil
      }
      div(
        h3(formatPath(diffInfo.path)),
        jsonDiffMarkup(infoList)
      )
    }

    div(
      h2(s"Changes to $subsystem"),
      p("""
          |Note: The paths in the tables below indicate the relative location of the change.
          |A number in a path is the zero based index of the changed item.
          |For example: "/publish/alarms/3/description" indicates that the change is in the fourth alarm's description.
          |""".stripMargin),
      list.map(diffInfoMarkup),
      p(" ")
    ).render
  }

  //noinspection ScalaUnusedSymbol
  // Called when one of the version checkboxes is clicked to update the enabled state of the compare
  // button when exactly two items are selected
  private def checkboxListener(compButton: Button)(e: dom.Event): Unit = {
    val checked = document.querySelectorAll("input[name='version']:checked")
    compButton.disabled = checked.length != 2
  }

  // Returns a checkbox displaying the version (select two to compare versions)
  private def makeVersionCheckBox(version: Option[String], compButton: Button) = {
    div(cls := "form-check")(
      input(
        name := "version",
        cls := "form-check-input",
        title := s"Select this version for comparison",
        tpe := "checkbox",
        onchange := checkboxListener(compButton),
        value := version.getOrElse("")
      ),
      label(cls := "form-check-label", version)
    )
  }

  // Returns the markup for displaying a table of version information for a subsystem
  private def markupSubsystemVersionInfo(subsystem: String, list: List[VersionInfo]) = {

    if (list.isEmpty) div(p(em(s"No published versions found for $subsystem."))).render
    else {
      val compButton = compareButton(subsystem)
      div(
        table(
          cls := "componentTable",
          attr("data-bs-toggle") := "table",
          thead(
            tr(
              th(subsystem, br, "Version"),
              th("User"),
              th("Date"),
              th("Comment")
            )
          ),
          tbody(
            for (v <- list) yield {
              tr(
                td(makeVersionCheckBox(v.version, compButton)),
                td(v.user),
                td(cls := "noWrapTableColumn", v.date),
                td(v.comment)
              )
            }
          )
        ),
        compButton,
        diffDiv
      ).render
    }

  }

  // Returns the markup for displaying a table of version information for an ICD
  private def markupIcdVersionInfo(icdName: IcdName, list: List[IcdVersionInfo]) = {

    if (list.isEmpty) div().render
    else
      div(
        table(
          cls := "componentTable",
          attr("data-bs-toggle") := "table",
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
            for (v <- list) yield {
              val icdVersion = v.icdVersion
              tr(
                td(icdVersion.icdVersion),
                td(icdVersion.subsystemVersion),
                td(icdVersion.targetVersion),
                td(v.user),
                td(cls := "noWrapTableColumn", v.date),
                td(v.comment)
              )
            }
          )
        )
      ).render

  }

  // Gets the subsystem version info from the server
  private def getSubsystemVersionInfo(subsystem: String): Future[List[VersionInfo]] =
    Fetch
      .get(ClientRoutes.versions(subsystem))
      .map { text =>
        Json.fromJson[Array[VersionInfo]](Json.parse(text)) match {
          case JsSuccess(ar: Array[VersionInfo], _: JsPath) =>
            ar.toList
          case e: JsError =>
            mainContent.displayInternalError(JsError.toJson(e).toString())
            Nil
        }
      }
//      .recover {
//        case ex =>
//          mainContent.displayInternalError(ex)
//          Nil
//      }

  // Gets the ICD version info from the server
  private def getIcdVersionInfo(icdName: IcdName): Future[List[IcdVersionInfo]] = {
    import play.api.libs.json.*
    Fetch
      .get(ClientRoutes.icdVersions(icdName))
      .map { text =>
        Json.fromJson[Array[IcdVersionInfo]](Json.parse(text)) match {
          case JsSuccess(ar: Array[IcdVersionInfo], _: JsPath) =>
            ar.toList
          case e: JsError =>
            mainContent.displayInternalError(JsError.toJson(e).toString())
            Nil
        }
      }
//      .recover {
//        case ex =>
//          mainContent.displayInternalError(ex)
//          Nil
//      }
  }

  def setSubsystem(subsystem: String): Unit = {
    getSubsystemVersionInfo(subsystem).foreach { list =>
      diffDiv.innerHTML = ""
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupSubsystemVersionInfo(subsystem, list))
    }
  }

  def setIcd(icdName: IcdName): Unit = {
    getIcdVersionInfo(icdName).foreach { list =>
      diffDiv.innerHTML = ""
      contentDiv.innerHTML = ""
      contentDiv.appendChild(markupIcdVersionInfo(icdName, list))
    }
  }

  def markup(): Element = contentDiv
}
