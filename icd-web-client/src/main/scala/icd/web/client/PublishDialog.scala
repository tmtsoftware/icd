package icd.web.client

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.{Element, document}
import org.scalajs.dom.html.Button
import scalatags.JsDom.all._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Displays a table with the version history
 */
case class PublishDialog(mainContent: MainContent, subsystemNames: SubsystemNames) extends Displayable {

  import icd.web.shared.JsonSupport._

  // Main version div
  private val contentDiv = div(id := "publishDialog").render

  private val helpMsg = """
                          |Select a single subsystem API below, or two subsystems for an ICD.
                          |Then enter your GitHub credentials and a comment and click Publish.
                          |""".stripMargin

  // Displays the Publish button
  private def publishButton(): Button = {
    import scalatags.JsDom.all._
    button(
      title := "Publish the selected API, or ICD if two APIs are selected",
      onclick := publishHandler _
    )("Publish").render
  }

  // Publish comment box
  private val commentBox = {
    import scalatags.JsDom.all._
    textarea(
      cls := "form-control",
      name := "comments",
      rows := 10,
      cols := 80,
      placeholder := "Enter publish comment here..."
    ).render
  }

  // Publish user name field
  private val userNameBox = {
    import scalatags.JsDom.all._
    input(
      cls := "form-control",
      name := "userName",
      id := "userName",
      required,
      placeholder := "Enter your GitHub user name..."
    ).render
  }

  // Publish password field
  private val passwordBox = {
    import scalatags.JsDom.all._
    input(
      cls := "form-control",
      `type` := "password",
      name := "password",
      id := "password",
      required,
      placeholder := "Enter your GitHub password..."
    ).render
  }

  // Message to display above publish button
  private val messageItem = {
    import scalatags.JsDom.all._
    div.render
  }

  // Called when the Compare button is pressed
  private def publishHandler(e: dom.Event): Unit = {
//    val checked = document.querySelectorAll("input[name='version']:checked").toList
//    if (checked.size == 2) {
//      val versions = checked.map(elem => elem.asInstanceOf[HTMLInputElement].value).sortWith(compareVersions)
//      val route    = Routes.diff(subsystem, versions)
//      Ajax.get(route).map { r =>
//        val list = Json.fromJson[Array[DiffInfo]](Json.parse(r.responseText)).map(_.toList).getOrElse(Nil)
//        diffDiv.innerHTML = ""
//        diffDiv.appendChild(markupDiff(subsystem, list))
//      }
//    }
  }

  // Called when one of the API checkboxes is clicked to update the enabled state of the publish
  // button
  private def checkboxListener(publishButton: Button)(e: dom.Event): Unit = {
    val checked = document.querySelectorAll("input[name='version']:checked")
    // XXX TODO FIXME: Also check if selected API(s) have changed
    publishButton.disabled = checked.length != 2 && checked.length != 1
  }

  // Returns a checkbox displaying the API name
  private def makeSubsystemCheckBox(subsystem: String, publishButton: Button) = {
    div(cls := "checkbox")(
      label(
        input(
          name := "api",
          title := s"Select this API to publish",
          tpe := "checkbox",
          onchange := checkboxListener(publishButton) _,
          value := subsystem
        ),
        subsystem
      )
    )
  }

  // Returns the markup for displaying a table of subsystems
  private def markupSubsystemTable(publishInfoList: List[PublishInfo]) = {
    import scalacss.ScalatagsCss._
    val pubButton = publishButton()
    div(
      messageItem,
      div(cls := "panel panel-info")(
        div(cls := "panel-body")(
          p(helpMsg),
          table(
            Styles.componentTable,
            attr("data-toggle") := "table",
            thead(
              tr(
                th("Subsystem"),
                th("Version"),
                th("Date"),
                th("User"),
                th("Comment"),
                th("Changed?")
              )
            ),
            tbody(
              for (p <- publishInfoList) yield {
                tr(
                  td(makeSubsystemCheckBox(p.subsystem, pubButton)),
                  td(p.maybeApiVersionInfo.map(_.version)),
                  td(p.maybeApiVersionInfo.map(_.date)),
                  td(p.maybeApiVersionInfo.map(_.user)),
                  td(p.maybeApiVersionInfo.map(_.comment)),
                  td(if (p.readyToPublish) "yes" else "no")
                )
              }
            )
          ),
          div(Styles.commentBox, label("Comments")(commentBox)),
//          div(Styles.commentBox, label("Username")("*", userNameBox, userNameMissing)),
//          div(Styles.commentBox, label("Password")("*", passwordBox, passwordMissing)),
          div(Styles.commentBox, label("Username")("*", userNameBox)),
          div(Styles.commentBox, label("Password")("*", passwordBox)),
          div(
            pubButton
          )
        )
      ),
      h4("Status")(
        span(style := "margin-left:15px;"),
        span(id := "busyStatus", cls := "glyphicon glyphicon-refresh glyphicon-refresh-animate hide"),
        span(style := "margin-left:15px;"),
        span(id := "status", cls := "label", "Working...")
      ),
      p() // FIXME
    ).render
  }

  // Gets information about the published state of all of the subsystems
  private def getPublishInfo: Future[List[PublishInfo]] = {
    Ajax
      .get(Routes.getPublishInfo)
      .map { r =>
        Json.fromJson[Array[PublishInfo]](Json.parse(r.responseText)) match {
          case JsSuccess(ar: Array[PublishInfo], _: JsPath) =>
            ar.toList
          case e: JsError =>
            mainContent.displayInternalError(JsError.toJson(e).toString())
            Nil
        }
      }
      .recover {
        case ex =>
          mainContent.displayInternalError(ex)
          Nil
      }
  }

  def update(): Future[Unit] = {
    val f = getPublishInfo
    f.onComplete {
      case Success(list) =>
        contentDiv.innerHTML = ""
        contentDiv.appendChild(markupSubsystemTable(list))
      case Failure(ex) =>
        mainContent.displayInternalError(ex)
    }
    f.map(_ =>())
  }

  def markup(): Element = contentDiv
}
