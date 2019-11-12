package icd.web.client

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.{Element, document}
import org.scalajs.dom.html.{Button, Input}
import org.scalajs.dom.raw.HTMLInputElement
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

  private val pubLabelMsg = "To publish an API or ICD, first select one or two subsystems above."

  // Displays the Publish button (at the bottom of the dialog)
  private def publishButton(): Button = {
    import scalatags.JsDom.all._
    button(
      id := "publishButton",
      title := "Publish the selected API, or ICD if two APIs are selected",
      onclick := publishHandler _,
      disabled := true
    )("Publish").render
  }

  // Displays a Publish API button in the table that just jumps to the main publish button
  private def readyToPublishButton(publishInfo: PublishInfo, publishButton: Button): Button = {
    import scalatags.JsDom.all._
    button(
      title := s"Enter your GitHub credentials to Publish the API for ${publishInfo.subsystem} ...",
      onclick := readyToPublishHandler(publishInfo, publishButton) _
    )("Ready to Publish...").render
  }

  // Publish comment box
  private val commentBox = {
    import scalatags.JsDom.all._
    textarea(
      cls := "form-control",
      name := "comments",
      rows := 10,
      cols := 80,
      placeholder := "Enter publish comment here...",
      onkeyup := commentChanged _
    ).render
  }

  // Message about missing comment
  private val commentMissing = {
    import scalatags.JsDom.all._
    div(id := "commentMissing", cls := "has-error hide", label(cls := "control-label", "Comment is required!"))
  }

  private def commentChanged(): Unit = {
    val comment = commentBox.value
    val elem    = $id("commentMissing")
    if (comment.isEmpty)
      elem.classList.remove("hide")
    else
      elem.classList.add("hide")
  }

  // Publish user name field
  private val userNameBox = {
    import scalatags.JsDom.all._
    input(
      cls := "form-control",
      name := "userName",
      id := "userName",
      required,
      onkeyup := userNameChanged _,
      placeholder := "Enter your GitHub user name..."
    ).render
  }

  // Message about missing username
  private val userNameMissing = {
    import scalatags.JsDom.all._
    div(id := "userNameMissing", cls := "has-error hide", label(cls := "control-label", "Username is required!"))
  }

  private def userNameChanged(): Unit = {
    val userName = userNameBox.value
    val elem     = $id("userNameMissing")
    if (userName.isEmpty)
      elem.classList.remove("hide")
    else
      elem.classList.add("hide")
  }

  // Publish password field
  private val passwordBox = {
    import scalatags.JsDom.all._
    input(
      cls := "form-control",
      `type` := "password",
      name := "password",
      id := "password",
      onkeyup := passwordChanged _,
      required,
      placeholder := "Enter your GitHub password..."
    ).render
  }

  // Message about missing password
  private val passwordMissing = {
    import scalatags.JsDom.all._
    div(id := "passwordMissing", cls := "has-error hide", label(cls := "control-label", "Password is required!"))
  }

  private def passwordChanged(): Unit = {
    val password = passwordBox.value
    val elem     = $id("passwordMissing")
    if (password.isEmpty)
      elem.classList.remove("hide")
    else
      elem.classList.add("hide")
  }

  // Message to display above publish button
  private val messageItem = {
    import scalatags.JsDom.all._
    div.render
  }

  // Updates the publishStatus label
  private def setPublishStatus(status: String): Unit = {
    val elem = $id("publishStatus")
    elem.innerHTML = status

  }

  private def publishApi(publishInfo: PublishInfo): Unit = {
    // XXX TODO FIXME: Add a Major Version checkbox
    val majorVersion   = false
    val user           = userNameBox.value
    val password       = passwordBox.value
    val comment        = commentBox.value
    val publishApiInfo = PublishApiInfo(publishInfo.subsystem, majorVersion, user, password, comment)
    val headers        = Map("Content-Type" -> "application/json")
    val f = Ajax.post(url = Routes.publishApi, data = Json.toJson(publishApiInfo).toString(), headers = headers).map { r =>
      val apiVersionInfo = Json.fromJson[ApiVersionInfo](Json.parse(r.responseText)).get
      setPublishStatus(s"Published ${apiVersionInfo.subsystem}-${apiVersionInfo.version}")
    }
    showBusyCursorWhile(f.map(_ => ()))
  }

  private def publishIcd(publishInfo1: PublishInfo, publishInfo2: PublishInfo): Unit = {
    // XXX TODO FIXME: Add a Major Version checkbox
    val majorVersion = false
    val user         = userNameBox.value
    val password     = passwordBox.value
    val comment      = commentBox.value
    val publishIcdInfo = PublishIcdInfo(
      publishInfo1.subsystem,
      publishInfo1.apiVersions.head.version,
      publishInfo2.subsystem,
      publishInfo2.apiVersions.head.version,
      majorVersion,
      user,
      password,
      comment
    )
    val headers = Map("Content-Type" -> "application/json")
    val f = Ajax.post(url = Routes.publishIcd, data = Json.toJson(publishIcdInfo).toString(), headers = headers).map { r =>
      val icdVersionInfo = Json.fromJson[IcdVersionInfo](Json.parse(r.responseText)).get
      val v              = icdVersionInfo.icdVersion
      setPublishStatus(
        s"Published ICD-${v.subsystem}-${v.target}-${v.icdVersion} between ${v.subsystem}-${v.subsystemVersion} and ${v.target}-${v.targetVersion}"
      )
    }
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Called when the Publish button is pressed
  private def publishHandler(e: dom.Event): Unit = {
    setPublishStatus("")
    val checked = document.querySelectorAll("input[name='api']:checked")
    if (checked.length == 1 || checked.length == 2) {
      val publishInfoList = checked
        .map(elem => elem.asInstanceOf[HTMLInputElement].value)
        .toList
        .map(s => Json.fromJson[PublishInfo](Json.parse(s)).get)
      if (checked.length == 1) {
        // API
        val publishInfo = publishInfoList.head
        if (publishInfo.readyToPublish) {
          publishApi(publishInfo)
        }
      } else {
        // ICD
        if (!icdExists(publishInfoList)) {
          publishIcd(publishInfoList.head, publishInfoList.tail.head)
        }
      }
    }
  }

  // Called when the PublishApi button is pressed in the table for a given subsystem
  private def readyToPublishHandler(publishInfo: PublishInfo, publishButton: Button)(e: dom.Event): Unit = {
    // Check the subsystem checkbox, if not already
    val checkbox = document.querySelector(s"#${publishInfo.subsystem}Checkbox").asInstanceOf[Input]
    checkbox.checked = true
    checkboxListener(publishButton)(e)

    $id("publishButton").scrollIntoView()
  }

  // Check that the icd for the given subsystem versions does not yet exist
  private def icdExists(publishInfoList: List[PublishInfo]): Boolean = {
    val p1     = publishInfoList.head
    val p2     = publishInfoList.tail.head
    val sv     = SubsystemWithVersion(p1.subsystem, p1.apiVersions.headOption.map(_.version), None)
    val target = SubsystemWithVersion(p2.subsystem, p2.apiVersions.headOption.map(_.version), None)
    if (sv.maybeVersion.isDefined && target.maybeVersion.isDefined) {
      p1.icdVersions.exists { icdVersionInfo =>
        val i = icdVersionInfo.icdVersion
        i.subsystem == sv.subsystem &&
        i.subsystemVersion == sv.maybeVersion.get &&
        i.target == target.subsystem &&
        i.targetVersion == target.maybeVersion.get
      }
    } else false
  }

  // Returns "$subsystem-$version", or just "$subsystem", if no version exists
  private def getSubsystemVersionStr(p: PublishInfo): String = {
    val versionStr = p.apiVersions.headOption.map(v => s"-${v.version}").getOrElse("")
    s"${p.subsystem}$versionStr"
  }

  // Called when one of the API checkboxes is clicked to update the enabled state of the publish
  // button
  private def checkboxListener(publishButton: Button)(e: dom.Event): Unit = {
    setPublishStatus("")
    val checked = document.querySelectorAll("input[name='api']:checked")

    val enabled = checked.length == 1 || checked.length == 2
    publishButton.disabled = !enabled

    // Set the label next to the publish button
    val e = $id("publishLabel")
    if (enabled) {

      val publishInfoList = checked
        .map(elem => elem.asInstanceOf[HTMLInputElement].value)
        .toList
        .map(s => Json.fromJson[PublishInfo](Json.parse(s)).get)
      if (checked.length == 1) {
        // API
        val publishInfo = publishInfoList.head
        e.innerHTML = if (publishInfo.readyToPublish) {
          s"Click below to publish the API for ${publishInfo.subsystem}:"
        } else {
          publishButton.disabled = true
          s"${publishInfo.subsystem} has no new changes to publish"
        }
      } else {
        // ICD
        val subsystems = publishInfoList.map(getSubsystemVersionStr)
        val subsysStr  = s"${subsystems.mkString(" and ")}"
        e.innerHTML = if (icdExists(publishInfoList)) {
          publishButton.disabled = true
          s"The ICD between $subsysStr already exists"
        } else {
          s"Click below to publish the ICD between $subsysStr:"
        }
      }
    } else {
      e.innerHTML = pubLabelMsg
    }
  }

  // Returns a checkbox displaying the API name
  private def makeSubsystemCheckBox(publishInfo: PublishInfo, publishButton: Button) = {
    div(cls := "checkbox")(
      label(
        input(
          id := s"${publishInfo.subsystem}Checkbox",
          name := "api",
          title := s"Select this API to publish",
          tpe := "checkbox",
          onchange := checkboxListener(publishButton) _,
          value := Json.toJson(publishInfo).toString()
        ),
        publishInfo.subsystem
      )
    )
  }

  // Returns the markup for displaying a table of subsystems
  private def markupSubsystemTable(publishInfoList: List[PublishInfo]) = {
    import scalacss.ScalatagsCss._
    val pubButton = publishButton()
    val pubLabel  = label(id := "publishLabel", pubLabelMsg)
    val pubStatus = label(id := "publishStatus", "")
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
                th("Status")
              )
            ),
            tbody(
              for (publishInfo <- publishInfoList) yield {
                // XXX TODO: Display menu of versions
                val checkBox   = makeSubsystemCheckBox(publishInfo, pubButton).render
                val apiVersion = publishInfo.apiVersions.headOption
                val publishItem =
                  if (publishInfo.readyToPublish)
                    div(readyToPublishButton(publishInfo, pubButton))
                  else if (apiVersion.nonEmpty)
                    div("Up to date")
                  else div()
                tr(
                  td(checkBox),
                  td(apiVersion.map(_.version)),
                  td(apiVersion.map(_.date)),
                  td(apiVersion.map(_.user)),
                  td(apiVersion.map(_.comment)),
                  td(publishItem)
                )
              }
            )
          ),
          div(Styles.commentBox, label("Comments")("*", commentBox, commentMissing)),
          div(Styles.commentBox, label("Username")("*", userNameBox, userNameMissing)),
          div(Styles.commentBox, label("Password")("*", passwordBox, passwordMissing)),
          div(
            pubLabel,
            br,
            pubButton,
            " ",
            pubStatus
          )
        )
      ),
      p(" ")
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

  /**
   * Updates the dialog with the current state of the subsystem releases on GitHub
   */
  def update(): Future[Unit] = {
    contentDiv.innerHTML = ""
    contentDiv.appendChild(p(em("Getting the current release status from GitHub...")).render)

    val f = getPublishInfo
    f.onComplete {
      case Success(list) =>
        contentDiv.innerHTML = ""
        contentDiv.appendChild(markupSubsystemTable(list))
        // Update the warning messages
        userNameChanged()
        passwordChanged()
        commentChanged()
      case Failure(ex) =>
        mainContent.displayInternalError(ex)
    }
    f.map(_ => ())
  }

  def markup(): Element = contentDiv
}
