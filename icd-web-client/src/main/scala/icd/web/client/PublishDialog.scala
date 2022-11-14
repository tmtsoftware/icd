package icd.web.client

import icd.web.client.PublishDialog.PublishDialogListener
import icd.web.shared._
import org.scalajs.dom
import play.api.libs.json._

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import org.scalajs.dom.{Element, document}
import org.scalajs.dom.html.{Button, Div, Input}
import org.scalajs.dom.HTMLInputElement
import scalatags.JsDom
import scalatags.JsDom.all._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object PublishDialog {
  trait PublishDialogListener {

    /**
     * Called when an API or ICD was published or unpublished
     */
    def publishChange(): Future[Unit]
  }
}

/**
 * Displays a table with the version history
 */
case class PublishDialog(mainContent: MainContent, publishChangeListener: PublishDialogListener) extends Displayable {

  import icd.web.shared.JsonSupport._

  // Main version div
  private val contentDiv = div(id := "publishDialog").render

  private val helpMsg = p(
    "Select a single subsystem API below, or two subsystems for an ICD. Then enter a comment and click Publish.",
    br,
    "If you discover that you need to change something after publishing, use the Unpublish button to unpublish ",
    br,
    "the API or ICD and then republish again later.",
    br,
    br
  )
  private val publishLabelMsg = "To publish an API or ICD, first select one or two subsystems above."

  private val upToDate = "Up to date"

  // Displays the Publish (Unpublish) button (at the bottom of the dialog)
  private def makePublishButton(unpublish: Boolean): Button = {
    val buttonClass = if (unpublish) "btn btn-secondary" else "btn btn-primary"
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
      attr("data-bs-toggle") := "modal",
      attr("data-bs-target") := "#publishModal"
    )(s).render
  }

  // Makes the popup confirmation for publish (unpublish)
  private def makePublishModal(): JsDom.TypedTag[Div] = {
    div(cls := "modal fade", id := "publishModal", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-bs-dismiss") := "modal")(raw("&times;")),
            h4(cls := "modal-title")("Confirm Publish")
          ),
          div(cls := "modal-body")(
            h3(id := "confirmPublishMessage")(s"Are you sure you want to ...")
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "btn btn-secondary", attr("data-bs-dismiss") := "modal")("Cancel"),
            button(id := "confirmPublishButton", `type` := "button", cls := "btn btn-primary", attr("data-bs-dismiss") := "modal")
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
  private def relatedIcds(publishInfo: PublishInfo): List[IcdVersion] = {
    val subsystem = publishInfo.subsystem
    val version   = publishInfo.apiVersions.head.version
    publishInfo.icdVersions
      .filter { i =>
        val iv = i.icdVersion
        (iv.subsystem == subsystem && iv.subsystemVersion == version) || (iv.target == subsystem && iv.targetVersion == version)
      }
      .map(_.icdVersion)
  }

  // Updates the message for the (un)publish API confirmation modal dialog, displaying the version of teh API that will be (un)published.
  private def setConfirmPublishApi(unpublish: Boolean, publishInfo: PublishInfo): Unit = {
    val subsystem    = publishInfo.subsystem
    val maybeVersion = publishInfo.apiVersions.headOption.map(_.version)

    if (unpublish) {
      if (maybeVersion.nonEmpty) {
        val icds   = relatedIcds(publishInfo).map(iv => s"${iv.subsystem}-${iv.target}-${iv.icdVersion}").mkString(",")
        val icdMsg = if (icds.isEmpty) "" else s" and related ICDs: $icds"
        $id("confirmPublishMessage").innerHTML =
          span("Are you sure you want to unpublish the API ")(strong(s"$subsystem-${maybeVersion.get}$icdMsg?")).render.innerHTML
        $id("confirmPublishButton").innerHTML = "Unpublish"
        $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish = true) _
      }
    }
    else {
      val v = if (maybeVersion.isEmpty) "1.0" else nextVersion(maybeVersion.get)
      $id("confirmPublishMessage").innerHTML =
        span("Are you sure you want to publish the API ")(strong(s"$subsystem-$v?")).render.innerHTML
      $id("confirmPublishButton").innerHTML = "Publish"
      $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish = false) _
    }
  }

  // Updates the message for the (un)publish ICD confirmation modal dialog, , displaying the version of the ICD that will be (un)published.
  private def setConfirmPublishIcd(unpublish: Boolean, subsysStr: String, publishInfoList: List[PublishInfo]): Unit = {
    val s  = if (unpublish) "unpublish" else "publish"
    val p1 = publishInfoList.head
    val p2 = publishInfoList.tail.head
    val maybeIcdVersionInfo = p1.icdVersions
      .find { icdVersionInfo =>
        val i = icdVersionInfo.icdVersion
        i.subsystem == p1.subsystem && i.target == p2.subsystem
      }
    // Assume we only get here on unpublish if the icd exists
    val v =
      if (maybeIcdVersionInfo.isEmpty) "1.0"
      else {
        val v = maybeIcdVersionInfo.get.icdVersion.icdVersion
        if (unpublish) v else nextVersion(v)
      }
    $id("confirmPublishMessage").innerHTML = span(s"Are you sure you want to $s the ICD: ")(
      strong(s"${p1.subsystem}-${p2.subsystem}-$v"),
      " between ",
      s"$subsysStr?"
    ).render.innerHTML

    $id("confirmPublishButton").innerHTML = if (unpublish) "Unpublish" else "Publish"
    $id("confirmPublishButton").asInstanceOf[Button].onclick = publishHandler(unpublish) _
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
      commentMissing.classList.remove("d-none")
    else
      commentMissing.classList.add("d-none")
    // Update the enabled states of the publish/unpublish buttons
    changeListener()
  }

  // Publish user name field
  private val usernameBox = {
    input(
      cls := "form-control",
      name := "github-username",
      id := "github-username",
      required,
      onkeyup := usernameChanged _,
      placeholder := "Enter your GitHub user name..."
    ).render
  }

  private val majorVersionCheckBox = {
    input(tpe := "checkbox", cls := "form-check-input", onchange := changeListener _).render
  }

  // Message about missing username
  private val usernameMissing = {
    div(id := "usernameMissing", cls := "has-error", label(cls := "control-label", "Username is required!")).render
  }

  private def usernameChanged(): Unit = {
    val username = usernameBox.value
    if (username.isEmpty)
      usernameMissing.classList.remove("d-none")
    else
      usernameMissing.classList.add("d-none")
  }

  // Publish password field
  private val passwordBox = {
    input(
      cls := "form-control",
      `type` := "password",
      name := "github-password",
      id := "github-password",
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
    div(
      id := "passwordIncorrect",
      cls := "has-error d-none",
      label(cls := "control-label", "Password or username is incorrect!")
    ).render
  }

  private def passwordChanged(): Unit = {
    val password = passwordBox.value
    if (password.isEmpty)
      passwordMissing.classList.remove("d-none")
    else
      passwordMissing.classList.add("d-none")
    passwordIncorrect.classList.add("d-none")
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
  private def updateTableRow(
      unpublish: Boolean,
      api: Boolean,
      subsystem: String,
      maybeApiVersionInfo: Option[ApiVersionInfo]
  ): Future[Unit] = {
    IcdUtil.getPublishInfo(Some(subsystem), mainContent).map { pubInfoList =>
      val publishInfo = pubInfoList.head
      val cb          = $id(s"${subsystem}Checkbox")
      cb.asInstanceOf[HTMLInputElement].value = Json.toJson(publishInfo).toString()
      if (api) {
        $id(s"${subsystem}Version").innerHTML = maybeApiVersionInfo.map(_.version).getOrElse("")
        $id(s"${subsystem}Date").innerHTML = maybeApiVersionInfo.map(_.date).getOrElse("")
        $id(s"${subsystem}User").innerHTML = maybeApiVersionInfo.map(_.user).getOrElse("")
        $id(s"${subsystem}Comment").innerHTML = maybeApiVersionInfo.map(_.comment).getOrElse("")
        $id(s"${subsystem}Status").innerHTML =
          if (unpublish)
            div(readyToPublishButton(publishInfo)).render.innerHTML
          else
            div(upToDate).render.innerHTML
        setPublishButtonDisabled(true)
      }
    }
  }

  private def displayFetchErrors(f: Future[Unit]): Unit = {
    f.onComplete {
      case Failure(ex: Exception) =>
        ex.printStackTrace()
        setPublishStatus(ex.getMessage)
        passwordIncorrect.classList.remove("d-none")
      case _ =>
    }
  }

  private def unpublishApi(publishInfo: PublishInfo): Future[Unit] = {
    val user             = usernameBox.value
    val password         = passwordBox.value
    val comment          = commentBox.value
    val version          = publishInfo.apiVersions.head.version
    val unpublishApiInfo = UnpublishApiInfo(publishInfo.subsystem, version, user, password, comment)
    val fetchFuture      = Fetch.post(url = ClientRoutes.unpublishApi, data = Json.toJson(unpublishApiInfo).toString())
    fetchFuture.flatMap { p =>
      p._1 match {
        case 200 => // OK
          val apiVersionInfo      = Json.fromJson[ApiVersionInfo](Json.parse(p._2)).get
          val maybeApiVersionInfo = publishInfo.apiVersions.tail.headOption
          for {
            _ <- publishChangeListener.publishChange()
            _ <- updateTableRow(unpublish = true, api = true, publishInfo.subsystem, maybeApiVersionInfo).map(_ => ())
          } yield {
            updateEnabledStates()
            setPublishStatus(s"Unpublished ${apiVersionInfo.subsystem}-${apiVersionInfo.version}")
          }
        case _ =>
          setPublishStatus(p._2)
          passwordIncorrect.classList.remove("d-none")
          Future.successful(())
      }
    }
  }

  private def publishApi(publishInfo: PublishInfo): Future[Unit] = {
    val majorVersion   = majorVersionCheckBox.checked
    val user           = usernameBox.value
    val password       = passwordBox.value
    val comment        = commentBox.value
    val publishApiInfo = PublishApiInfo(publishInfo.subsystem, majorVersion, user, password, comment)
    val fetchFuture    = Fetch.post(url = ClientRoutes.publishApi, data = Json.toJson(publishApiInfo).toString())
    fetchFuture.flatMap { p =>
      p._1 match {
        case 200 => // OK
          val apiVersionInfo = Json.fromJson[ApiVersionInfo](Json.parse(p._2)).get
          for {
            _ <- publishChangeListener.publishChange()
            _ <- updateTableRow(unpublish = false, api = true, publishInfo.subsystem, Some(apiVersionInfo)).map(_ => ())
          } yield {
            updateEnabledStates()
            setPublishStatus(s"Published ${apiVersionInfo.subsystem}-${apiVersionInfo.version}")
          }
        case _ =>
          setPublishStatus(p._2)
          passwordIncorrect.classList.remove("d-none")
          Future.successful(())
      }
    }
  }

  // Publish (Unpublish) an API on GitHub
  private def publishApi(unpublish: Boolean, publishInfo: PublishInfo): Unit = {
    setPublishButtonDisabled(true)
    setUnpublishButtonDisabled(true)
    val f = if (unpublish) unpublishApi(publishInfo) else publishApi(publishInfo)
    displayFetchErrors(f)
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Publish (Unpublish) an ICD on GitHub
  private def publishIcd(unpublish: Boolean, publishInfo1: PublishInfo, publishInfo2: PublishInfo): Unit = {
    val majorVersion = majorVersionCheckBox.checked
    val user         = usernameBox.value
    val password     = passwordBox.value
    val comment      = commentBox.value
    val icdVersion = publishInfo1.icdVersions.find { icdVersionInfo =>
      val i = icdVersionInfo.icdVersion
      i.subsystem == publishInfo1.subsystem && i.target == publishInfo2.subsystem
    }
    setPublishButtonDisabled(true)
    setUnpublishButtonDisabled(true)
    val f = if (unpublish) {
      val unpublishIcdInfo = UnpublishIcdInfo(
        icdVersion.get.icdVersion.icdVersion,
        publishInfo1.subsystem,
        publishInfo2.subsystem,
        user,
        password,
        comment
      )
      Fetch.post(url = ClientRoutes.unpublishIcd, data = Json.toJson(unpublishIcdInfo).toString()).flatMap { p =>
        p._1 match {
          case 200 => // OK
            val icdVersionInfo = Json.fromJson[IcdVersionInfo](Json.parse(p._2)).get
            val v              = icdVersionInfo.icdVersion
            for {
              _ <- publishChangeListener.publishChange()
              _ <- updateTableRow(unpublish, api = false, publishInfo1.subsystem, publishInfo1.apiVersions.headOption)
                .map(_ => ())
              _ <- updateTableRow(unpublish, api = false, publishInfo2.subsystem, publishInfo2.apiVersions.headOption)
                .map(_ => ())
            } yield {
              updateEnabledStates()
              setPublishStatus(
                s"Unpublished ICD-${v.subsystem}-${v.target}-${v.icdVersion} between ${v.subsystem}-${v.subsystemVersion} and ${v.target}-${v.targetVersion}"
              )
            }
        }
      }
    }
    else {
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
      Fetch.post(url = ClientRoutes.publishIcd, data = Json.toJson(publishIcdInfo).toString()).flatMap { p =>
        p._1 match {
          case 200 => // OK
            val icdVersionInfo = Json.fromJson[IcdVersionInfo](Json.parse(p._2)).get
            val v              = icdVersionInfo.icdVersion
            for {
              _ <- publishChangeListener.publishChange()
              _ <- updateTableRow(unpublish, api = false, publishInfo1.subsystem, publishInfo1.apiVersions.headOption)
                .map(_ => ())
              _ <- updateTableRow(unpublish, api = false, publishInfo2.subsystem, publishInfo2.apiVersions.headOption)
                .map(_ => ())
            } yield {
              updateEnabledStates()
              setPublishStatus(
                s"Published ICD-${v.subsystem}-${v.target}-${v.icdVersion} between ${v.subsystem}-${v.subsystemVersion} and ${v.target}-${v.targetVersion}"
              )
            }
        }
      }
    }
    displayFetchErrors(f)
    showBusyCursorWhile(f.map(_ => ()))
  }

  // Called when the Publish (or Unpublish) button is pressed.
  // Here we just update some labels. The actual publishing is done after confirmation.
  //noinspection ScalaUnusedSymbol
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
        setConfirmPublishApi(unpublish, publishInfo)
      }
      else {
        // ICD
        val subsystems = publishInfoList.map(getSubsystemVersionStr)
        val subsysStr  = s"${subsystems.mkString(" and ")}"
        setConfirmPublishIcd(unpublish, subsysStr, publishInfoList)
      }
    }
  }

  // Called when the confirmation modal popup's Publish (Unpublish) button is pressed
  //noinspection ScalaUnusedSymbol
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
      }
      else {
        // ICD
        if (unpublish || !icdExists(publishInfoList)) {
          publishIcd(unpublish, publishInfoList.head, publishInfoList.tail.head)
        }
      }
    }
  }

  // Called when the PublishApi button is pressed in the table for a given subsystem
  //noinspection ScalaUnusedSymbol
  private def readyToPublishHandler(publishInfo: PublishInfo)(e: dom.Event): Unit = {
    // Check the subsystem checkbox, if not already
    val checkbox = document.querySelector(s"#${publishInfo.subsystem}Checkbox").asInstanceOf[Input]
    checkbox.checked = true
    changeListener()

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
    }
    else false
  }

  // Returns "$subsystem-$version", or just "$subsystem", if no version exists
  private def getSubsystemVersionStr(p: PublishInfo): String = {
    val versionStr = p.apiVersions.headOption.map(v => s"-${v.version}").getOrElse("")
    s"${p.subsystem}$versionStr"
  }

  def setPublishButtonDisabled(disabled: Boolean): Unit = {
    $id("publishButton").asInstanceOf[Button].disabled = disabled || commentBox.value.isEmpty
  }

  def setUnpublishButtonDisabled(disabled: Boolean): Unit = {
    $id("unpublishButton").asInstanceOf[Button].disabled = disabled || commentBox.value.isEmpty
  }

  // Called when one of the API checkboxes is clicked, the comment is changed, etc. to update the labels and enabled states
  private def changeListener(): Unit = {
    setPublishStatus("")
    updateEnabledStates()
  }

  // Called when one of the API checkboxes is clicked to update the enabled state of the publish
  // button
  private def updateEnabledStates(): Unit = {
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
          setUnpublishButtonDisabled(true)
          s"Click below to publish the API for ${publishInfo.subsystem}:"
        }
        else {
          setPublishButtonDisabled(true)
          s"${publishInfo.subsystem} has no new changes to publish"
        }
      }
      else {
        // ICD
        val subsystems     = publishInfoList.map(getSubsystemVersionStr)
        val subsysStr      = s"${subsystems.mkString(" and ")}"
        val noApiPublished = publishInfoList.exists(_.apiVersions.isEmpty)
        if (noApiPublished) {
          setPublishButtonDisabled(true)
          setUnpublishButtonDisabled(true)
        }
        val unpublishedChanges = publishInfoList.exists(_.readyToPublish)
        e.innerHTML =
          if (noApiPublished)
            "The APIs for both subsystems in an ICD must already be published."
          else if (icdExists(publishInfoList)) {
            setPublishButtonDisabled(true)
            setUnpublishButtonDisabled(unpublishedChanges)
            s"The ICD between $subsysStr already exists"
          }
          else {
            setUnpublishButtonDisabled(true)
            if (unpublishedChanges) {
              setPublishButtonDisabled(true)
              "Both APIs must be published and up to date before publishing an ICD."
            }
            else {
              s"Click below to publish the ICD between $subsysStr:"
            }
          }
      }
    }
    else {
      e.innerHTML = publishLabelMsg
    }
  }

  // Returns a checkbox displaying the API name
  private def makeSubsystemCheckBox(publishInfo: PublishInfo) = {
    div(
      cls := "form-check",
      input(
        id := s"${publishInfo.subsystem}Checkbox",
        cls := "form-check-input",
        name := "api",
        title := s"Select this API to publish",
        tpe := "checkbox",
        onchange := changeListener _,
        value := Json.toJson(publishInfo).toString()
      ),
      label(cls := "form-check-label", publishInfo.subsystem)
    )
  }

  //noinspection ScalaUnusedSymbol
  private def checkGitHubCredentials(e: dom.Event): Unit = {
    val gitHubCredentials = GitHubCredentials(usernameBox.value, passwordBox.value)
    val f =
      Fetch
        .post(url = ClientRoutes.checkGitHubCredentials, data = Json.toJson(gitHubCredentials).toString())
        .map { p =>
          if (p._1 == 200) {
            $id("gitHubCredentials").classList.add("d-none")
            $id("contentDivPlaceholder").innerHTML = ""
            $id("contentDivPlaceholder").appendChild(contentDiv)
          }
          ()
        }
    displayFetchErrors(f)
    showBusyCursorWhile(f)
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
      div(cls := "card")(
        div(cls := "card-body")(
          helpMsg,
          table(
            Styles.componentTable,
            attr("data-bs-toggle") := "table",
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
          div(cls := "form-check", majorVersionCheckBox, label(cls := "form-check-label", "Increment major version")),
          div(
            br,
            p(strong(publishLabel)),
            makePublishButton(unpublish = false),
            " ",
            makePublishButton(unpublish = true),
            br,
            publishStatus
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
    val loaderDiv =
      div(
        p(style := "cursor:progress", em("Getting the current release status from GitHub...")),
        div(cls := "loader")
      ).render
    contentDiv.appendChild(loaderDiv)

    val f = IcdUtil.getPublishInfo(None, mainContent)
    f.onComplete {
      case Success(list) =>
        contentDiv.innerHTML = ""
        contentDiv.appendChild(markupSubsystemTable(list))
      case Failure(ex) =>
        mainContent.displayInternalError(ex)
    }
    f.map(_ => ())
  }

  //  def markup(): Element = contentDiv
  def markup(): Element = markupGitHubCredentials()
}
