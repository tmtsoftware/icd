package icd.web.client

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.{Ajax, AjaxException}
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom.{Element, document}
import org.scalajs.dom.html.{Button, Div, Input}
import org.scalajs.dom.raw.HTMLInputElement
import scalatags.JsDom
import scalatags.JsDom.all._

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Displays a table with the version history
 */
case class PublishDialog(mainContent: MainContent) extends Displayable {

  import icd.web.shared.JsonSupport._

  // Main version div
  private val contentDiv = div(id := "publishDialog").render

  private val helpMsg =
    """
      |Select a single subsystem API below, or two subsystems for an ICD.
      |Then enter your GitHub credentials and a comment and click Publish.
      |""".stripMargin

  private val publishLabelMsg = "To publish an API or ICD, first select one or two subsystems above."

  private val upToDate = "Up to date"

  // Used to show busy cursor only after enterring GitHub credentials, while still updating the GUI in the background
  private var updateFuture: Future[Unit] = Future.successful()

  // Displays the Publish (Unpublish) button (at the bottom of the dialog)
  private def makePublishButton(unpublish: Boolean): Button = {
    val buttonClass = if (unpublish) "btn" else "btn btn-primary"
    val buttonId    = if (unpublish) "unpublishButton" else "publishButton"
    val s           = if (unpublish) "Unpublish" else "Publish"
    val buttonTitle = s"$s the selected API, or ICD if two APIs are selected"
    button(
      `type` := "submit",
      cls := buttonClass,
      id := buttonId,
      title := buttonTitle,
      disabled := true,
      onclick := publishButtonClicked(unpublish) _,
      attr("data-toggle") := "modal",
      attr("data-target") := "#basicModal"
    )(s).render
  }

  // Makes the popup confirmation for publish (unpublish)
  private def makePublishModal(): JsDom.TypedTag[Div] = {
    div(cls := "modal fade", id := "basicModal", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-dismiss") := "modal")(raw("&times;")),
            h4(cls := "modal-title")("Confirm Publish")
          ),
          div(cls := "modal-body")(
            h3(id := "confirmPublishMessage")(s"Are you sure you want to ...")
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "btn btn-default", attr("data-dismiss") := "modal")("Cancel"),
            button(id := "confirmPublishButton", `type` := "button", cls := "btn btn-primary", attr("data-dismiss") := "modal")
          )
        )
      )
    )
  }

  // Returns the incremented version that the next publish will generate
  private def nextVersion(version: String): String = {
    val majorVersion    = majorVersionCheckBox.checked
    val Array(maj, min) = version.split("\\.")
    if (majorVersion) s"${maj.toInt + 1}.0" else s"$maj.${min.toInt + 1}"
  }

  // Returns names of ICDs containing the latest version of the subsystem
  private def relatedIcds(publishInfo: PublishInfo): List[String] = {
    val subsystem = publishInfo.subsystem
    val version = publishInfo.apiVersions.head.version
    publishInfo.icdVersions.filter { i =>
      val iv = i.icdVersion
      (iv.subsystem == subsystem && iv.subsystemVersion == version) || (iv.target == subsystem && iv.targetVersion == version)
    }.map { i =>
      val iv = i.icdVersion
      s"${iv.subsystem}-${iv.target}-${iv.icdVersion}"
    }
  }

  // Updates the message for the (un)publish API confirmation modal dialog, displaying the version of teh API that will be (un)published.
  private def setConfirmPublishApi(unpublish: Boolean, publishInfo: PublishInfo): Unit = {
    val subsystem = publishInfo.subsystem
    val version = publishInfo.apiVersions.head.version

    if (unpublish) {
      val icds = relatedIcds(publishInfo).mkString(",")
      val icdMsg = if (icds.isEmpty) "" else " and related ICDs: $icds"
      $id("confirmPublishMessage").innerHTML =
        span("Are you sure you want to unpublish the API ")(strong(s"$subsystem-$version$icdMsg?")).render.innerHTML
      $id("confirmPublishButton").innerHTML = "Unpublish"
      $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish = true) _
    } else {
      val v = nextVersion(version)
      $id("confirmPublishMessage").innerHTML =
        span("Are you sure you want to publish the API ")(strong(s"$subsystem-$v?")).render.innerHTML
      $id("confirmPublishButton").innerHTML = "Publish"
      $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish = false) _
    }
  }

  // Updates the message for the (un)publish ICD confirmation modal dialog, , displaying the version of the ICD that will be (un)published.
  private def setConfirmPublishIcd(unpublish: Boolean, subsysStr: String, publishInfoList: List[PublishInfo]): Unit = {
    val p1 = publishInfoList.head
    val p2 = publishInfoList.tail.head
    p1.icdVersions
      .find { icdVersionInfo =>
        val i = icdVersionInfo.icdVersion
        i.subsystem == p1.subsystem && i.target == p2.subsystem
      }
      .foreach { icdVersionInfo =>
        val iv = icdVersionInfo.icdVersion
        val v  = if (unpublish) iv.icdVersion else nextVersion(iv.icdVersion)
        val s  = if (unpublish) "unpublish" else "publish"
        $id("confirmPublishMessage").innerHTML = span(s"Are you sure you want to $s the ICD: ")(
          strong(s"${iv.subsystem}-${iv.target}-$v"),
          " between ",
          s"$subsysStr?"
        ).render.innerHTML

        $id("confirmPublishButton").innerHTML = if (unpublish) "Unpublish" else "Publish"
        $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish) _
      }
  }

  // Displays a Publish API button in the table that just jumps to the main publish button
  private def readyToPublishButton(publishInfo: PublishInfo): Button = {
    button(
      title := s"Enter your GitHub credentials to Publish the API for ${publishInfo.subsystem} ...",
      onclick := readyToPublishHandler(publishInfo) _
    )("Ready to Publish...").render
  }

  // Publish comment box
  private val commentBox = {
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
    div(id := "commentMissing", cls := "has-error", label(cls := "control-label", "Comment is required!")).render
  }

  private def commentChanged(): Unit = {
    val comment = commentBox.value
    if (comment.isEmpty)
      commentMissing.classList.remove("hide")
    else
      commentMissing.classList.add("hide")
  }

  // Publish user name field
  private val usernameBox = {
    input(
      cls := "form-control",
      name := "username",
      id := "username",
      required,
      onkeyup := usernameChanged _,
      placeholder := "Enter your GitHub user name..."
    ).render
  }

  private val majorVersionCheckBox = {
    input(tpe := "checkbox", onchange := checkboxListener() _).render
  }

  // Message about missing username
  private val usernameMissing = {
    div(id := "usernameMissing", cls := "has-error", label(cls := "control-label", "Username is required!")).render
  }

  private def usernameChanged(): Unit = {
    val username = usernameBox.value
    if (username.isEmpty)
      usernameMissing.classList.remove("hide")
    else
      usernameMissing.classList.add("hide")
  }

  // Publish password field
  private val passwordBox = {
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
    div(id := "passwordMissing", cls := "has-error", label(cls := "control-label", "Password is required!")).render
  }

  // Message about incorrect password
  private val passwordIncorrect = {
    div(id := "passwordIncorrect", cls := "has-error hide", label(cls := "control-label", "Password or username is incorrect!")).render
  }

  private def passwordChanged(): Unit = {
    val password = passwordBox.value
    if (password.isEmpty)
      passwordMissing.classList.remove("hide")
    else
      passwordMissing.classList.add("hide")
    passwordIncorrect.classList.add("hide")
  }

  // Message to display above publish button
  private val messageItem = {
    div.render
  }

  // Updates the publishStatus label
  private def setPublishStatus(status: String): Unit = {
    $id("publishStatus").innerHTML = status
    $id("publishLabel").innerHTML = ""
  }

  // Updates the row for the newly published API and returns a future indicating when done
  private def updateTableRow(subsystem: String, maybeApiVersionInfo: Option[ApiVersionInfo]): Future[Unit] = {
    IcdUtil.getPublishInfo(Some(subsystem), mainContent).map { pubInfoList =>
      val publishInfo = pubInfoList.head
      val cb          = $id(s"${subsystem}Checkbox")
      cb.asInstanceOf[HTMLInputElement].value = Json.toJson(publishInfo).toString()
      $id(s"${subsystem}Version").innerHTML = maybeApiVersionInfo.map(_.version).getOrElse("")
      $id(s"${subsystem}Date").innerHTML = maybeApiVersionInfo.map(_.date).getOrElse("")
      $id(s"${subsystem}User").innerHTML = maybeApiVersionInfo.map(_.user).getOrElse("")
      $id(s"${subsystem}Comment").innerHTML = maybeApiVersionInfo.map(_.comment).getOrElse("")
      $id(s"${subsystem}Status").innerHTML = upToDate
      setPublishButtonDisabled(true)
    }
  }

  private def displayAjaxErrors(f: Future[Unit]): Unit = {
    f.onComplete {
      case Failure(ex: AjaxException) =>
        ex.xhr.status match {
          case 400 => // BadRequest
            setPublishStatus(ex.xhr.responseText)
          case 401 => // Unauthorized
            passwordIncorrect.classList.remove("hide")
          case 406 => // NotAcceptable
            setPublishStatus(ex.xhr.responseText)
        }
      case _ =>
    }
  }

  // Publish (Unpublish) an API on GitHub
  private def publishApi(unpublish: Boolean, publishInfo: PublishInfo): Unit = {
    val majorVersion = majorVersionCheckBox.checked
    val user         = usernameBox.value
    val password     = passwordBox.value
    val comment      = commentBox.value
    val headers      = Map("Content-Type" -> "application/json")

    val f = if (unpublish) {
      val version          = publishInfo.apiVersions.head.version
      val unpublishApiInfo = UnpublishApiInfo(publishInfo.subsystem, version, user, password, comment)
      Ajax.post(url = Routes.unpublishApi, data = Json.toJson(unpublishApiInfo).toString(), headers = headers).flatMap { r =>
        r.status match {
          case 200 => // OK
            val apiVersionInfo = Json.fromJson[ApiVersionInfo](Json.parse(r.responseText)).get
            setPublishStatus(s"Unpublished ${apiVersionInfo.subsystem}-${apiVersionInfo.version}")
            val maybeApiVersionInfo = publishInfo.apiVersions.tail.headOption
            updateTableRow(publishInfo.subsystem, maybeApiVersionInfo).map(_ => ())
        }
      }
    } else {
      val publishApiInfo = PublishApiInfo(publishInfo.subsystem, majorVersion, user, password, comment)
      Ajax.post(url = Routes.publishApi, data = Json.toJson(publishApiInfo).toString(), headers = headers).flatMap { r =>
        r.status match {
          case 200 => // OK
            val apiVersionInfo = Json.fromJson[ApiVersionInfo](Json.parse(r.responseText)).get
            setPublishStatus(s"Published ${apiVersionInfo.subsystem}-${apiVersionInfo.version}")
            updateTableRow(publishInfo.subsystem, Some(apiVersionInfo)).map(_ => ())
        }
      }
    }
    displayAjaxErrors(f)
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Publish (Unpublish) an ICD on GitHub
  private def publishIcd(unpublish: Boolean, publishInfo1: PublishInfo, publishInfo2: PublishInfo): Unit = {
    val majorVersion = majorVersionCheckBox.checked
    val user         = usernameBox.value
    val password     = passwordBox.value
    val comment      = commentBox.value
    val headers      = Map("Content-Type" -> "application/json")
    val icdVersion = publishInfo1.icdVersions.find { icdVersionInfo =>
      val i = icdVersionInfo.icdVersion
      i.subsystem == publishInfo1.subsystem && i.target == publishInfo2.subsystem
    }
    val f = if (unpublish) {
      val unpublishIcdInfo = UnpublishIcdInfo(
        icdVersion.get.icdVersion.icdVersion,
        publishInfo1.subsystem,
        publishInfo2.subsystem,
        user,
        password,
        comment
      )
      Ajax.post(url = Routes.unpublishIcd, data = Json.toJson(unpublishIcdInfo).toString(), headers = headers).map { r =>
        r.status match {
          case 200 => // OK
            val icdVersionInfo = Json.fromJson[IcdVersionInfo](Json.parse(r.responseText)).get
            val v              = icdVersionInfo.icdVersion
            setPublishStatus(
              s"Unpublished ICD-${v.subsystem}-${v.target}-${v.icdVersion} between ${v.subsystem}-${v.subsystemVersion} and ${v.target}-${v.targetVersion}"
            )
            setPublishButtonDisabled(false) // XXX TODO FIXME
        }
      }
    } else {
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
      Ajax.post(url = Routes.publishIcd, data = Json.toJson(publishIcdInfo).toString(), headers = headers).map { r =>
        r.status match {
          case 200 => // OK
            val icdVersionInfo = Json.fromJson[IcdVersionInfo](Json.parse(r.responseText)).get
            val v              = icdVersionInfo.icdVersion
            setPublishStatus(
              s"Published ICD-${v.subsystem}-${v.target}-${v.icdVersion} between ${v.subsystem}-${v.subsystemVersion} and ${v.target}-${v.targetVersion}"
            )
            setPublishButtonDisabled(true)
        }
      }
    }
    displayAjaxErrors(f)
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Called when the Publish button is pressed.
  // Here we just update some labels. The actual publishing is done after confirmation.
  private def publishButtonClicked(unpublish: Boolean)(e: dom.Event): Unit = {
    val checked = document.querySelectorAll("input[name='api']:checked")
    val enabled = checked.length == 1 || checked.length == 2
    if (enabled) {
      val publishInfoList = checked
        .map(elem => elem.asInstanceOf[HTMLInputElement].value)
        .toList
        .map(s => Json.fromJson[PublishInfo](Json.parse(s)).get)
      if (checked.length == 1) {
        // API
        val publishInfo = publishInfoList.head
        if (unpublish || publishInfo.readyToPublish) {
          setConfirmPublishApi(unpublish, publishInfo)
        }
      } else {
        // ICD
        val subsystems = publishInfoList.map(getSubsystemVersionStr)
        val subsysStr  = s"${subsystems.mkString(" and ")}"
        if (unpublish || !icdExists(publishInfoList)) {
          setConfirmPublishIcd(unpublish, subsysStr, publishInfoList)
        }
      }
    }
  }

  // Called when the confirmation modal popup's Publish (Unpublish) button is pressed
  private def publishHandler(unpublish: Boolean)(e: dom.Event): Unit = {
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
        if (unpublish || publishInfo.readyToPublish) {
          publishApi(unpublish, publishInfo)
        }
      } else {
        // ICD
        if (unpublish || !icdExists(publishInfoList)) {
          publishIcd(unpublish, publishInfoList.head, publishInfoList.tail.head)
        }
      }
    }
  }

  // Called when the PublishApi button is pressed in the table for a given subsystem
  private def readyToPublishHandler(publishInfo: PublishInfo)(e: dom.Event): Unit = {
    // Check the subsystem checkbox, if not already
    val checkbox = document.querySelector(s"#${publishInfo.subsystem}Checkbox").asInstanceOf[Input]
    checkbox.checked = true
    checkboxListener()(e)

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

  def setPublishButtonDisabled(disabled: Boolean): Unit = {
    $id("publishButton").asInstanceOf[Button].disabled = disabled
  }

  def setUnpublishButtonDisabled(disabled: Boolean): Unit = {
    $id("unpublishButton").asInstanceOf[Button].disabled = disabled
  }

  // Called when one of the API checkboxes is clicked to update the enabled state of the publish
  // button
  private def checkboxListener()(e: dom.Event): Unit = {
    setPublishStatus("")
    val checked = document.querySelectorAll("input[name='api']:checked")

    val enabled = checked.length == 1 || checked.length == 2
    setPublishButtonDisabled(!enabled)
    setUnpublishButtonDisabled(!enabled)
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
          setPublishButtonDisabled(true)
          s"${publishInfo.subsystem} has no new changes to publish"
        }
      } else {
        // ICD
        val subsystems     = publishInfoList.map(getSubsystemVersionStr)
        val subsysStr      = s"${subsystems.mkString(" and ")}"
        val noApiPublished = publishInfoList.exists(_.apiVersions.isEmpty)
        if (noApiPublished) {
          setPublishButtonDisabled(true)
          setUnpublishButtonDisabled(true)
        }
        e.innerHTML =
          if (noApiPublished)
            "The APIs for both subsystems in an ICD must already be published."
          else if (icdExists(publishInfoList)) {
            setPublishButtonDisabled(true)
            setUnpublishButtonDisabled(false)
            s"The ICD between $subsysStr already exists"
          } else {
            s"Click below to publish the ICD between $subsysStr:"
          }
      }
    } else {
      e.innerHTML = publishLabelMsg
    }
  }

  // Returns a checkbox displaying the API name
  private def makeSubsystemCheckBox(publishInfo: PublishInfo) = {
    div(cls := "checkbox")(
      label(
        input(
          id := s"${publishInfo.subsystem}Checkbox",
          name := "api",
          title := s"Select this API to publish",
          tpe := "checkbox",
          onchange := checkboxListener() _,
          value := Json.toJson(publishInfo).toString()
        ),
        publishInfo.subsystem
      )
    )
  }

  private def checkGitHubCredentials(e: dom.Event): Unit = {
    val gitHubCredentials = GitHubCredentials(usernameBox.value, passwordBox.value)
    val headers           = Map("Content-Type" -> "application/json")
    val f =
      Ajax.post(url = Routes.checkGitHubCredentials, data = Json.toJson(gitHubCredentials).toString(), headers = headers).map {
        r =>
          if (r.status == 200) {
            $id("gitHubCredentials").classList.add("hide")
            $id("contentDivPlaceholder").innerHTML = ""
            $id("contentDivPlaceholder").appendChild(contentDiv)
          }
          ()
      }
    displayAjaxErrors(f)
    showBusyCursorWhile(f)
    f.foreach(_ => showBusyCursorWhile(updateFuture))
  }

  // Returns the markup for getting the GitHub credentials
  private def markupGitHubCredentials(): Div = {
    import scalacss.ScalatagsCss._
    usernameChanged()
    passwordChanged()
    div(
      div(
        id := "gitHubCredentials",
        div(Styles.commentBox, label("GitHub Username")("*", usernameBox, usernameMissing)),
        div(Styles.commentBox, label("GitHub Password")("*", passwordBox, passwordMissing, passwordIncorrect)),
        button(
          `type` := "submit",
          cls := "btn btn-primary",
          id := "applyButton",
          title := s"Use the given GitHub credentials...",
          onclick := checkGitHubCredentials _
          //        disabled := true
        )("Apply")
      ),
      div(id := "contentDivPlaceholder")
    ).render
  }

  // Returns the markup for displaying a table of subsystems
  private def markupSubsystemTable(publishInfoList: List[PublishInfo]): Div = {
    import scalacss.ScalatagsCss._
    val publishLabel  = label(id := "publishLabel", publishLabelMsg)
    val publishStatus = label(id := "publishStatus", "")
    div(
      makePublishModal(),
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
                // XXX TODO: Display menu of versions?
                val checkBox   = makeSubsystemCheckBox(publishInfo).render
                val apiVersion = publishInfo.apiVersions.headOption
                val publishItem =
                  if (publishInfo.readyToPublish)
                    div(readyToPublishButton(publishInfo))
                  else if (apiVersion.nonEmpty)
                    div(upToDate)
                  else div()
                val subsystem = publishInfo.subsystem
                tr(
                  td(checkBox),
                  td(id := s"${subsystem}Version", apiVersion.map(_.version)),
                  td(id := s"${subsystem}Date", apiVersion.map(_.date)),
                  td(id := s"${subsystem}User", apiVersion.map(_.user)),
                  td(id := s"${subsystem}Comment", apiVersion.map(_.comment)),
                  td(id := s"${subsystem}Status", publishItem)
                )
              }
            )
          ),
          div(Styles.commentBox, label("Comments")("*", commentBox, commentMissing)),
          div(cls := "checkbox")(label(majorVersionCheckBox, "Increment major version")),
          div(
            publishLabel,
            br,
            makePublishButton(unpublish = false),
            " ",
            makePublishButton(unpublish = true),
            br,
            publishStatus,
          )
        )
      ),
      p(" ")
    ).render
  }

  /**
   * Updates the dialog with the current state of the subsystem releases on GitHub
   */
  def update(): Future[Unit] = {
    contentDiv.innerHTML = ""
    contentDiv.appendChild(p(em("Getting the current release status from GitHub...")).render)

    val f = IcdUtil.getPublishInfo(None, mainContent)
    f.onComplete {
      case Success(list) =>
        contentDiv.innerHTML = ""
        contentDiv.appendChild(markupSubsystemTable(list))
      case Failure(ex) =>
        mainContent.displayInternalError(ex)
    }
    updateFuture = f.map(_ => ())
    updateFuture
  }

  //  def markup(): Element = contentDiv
  def markup(): Element = markupGitHubCredentials()
}
