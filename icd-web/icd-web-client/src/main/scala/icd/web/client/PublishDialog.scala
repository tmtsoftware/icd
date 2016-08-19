//package icd.web.client
//
//import icd.web.shared.SubsystemWithVersion
//import org.scalajs.dom
//import org.scalajs.dom._
//import org.scalajs.dom.ext.Ajax
//import scala.language.implicitConversions
////import org.scalajs.jquery.{ jQuery => $, _ }
//import org.querki.jquery._
//import scala.concurrent.ExecutionContext.Implicits.global
//
///**
// * Displays the page for publishing APIs or ICDs
// */
//case class PublishDialog(subsystem: Subsystem, targetSubsystem: Subsystem, icdChooser: IcdChooser) extends Displayable {
//
//  // Message to display above publish button
//  private val messageItem = {
//    import scalatags.JsDom.all._
//    div.render
//  }
//
//  // Displays publish button
//  private val publishButton = {
//    import scalatags.JsDom.all._
//    button(onclick := publishHandler _)("Publish").render
//  }
//
//  // Publish comment box
//  private val commentBox = {
//    import scalatags.JsDom.all._
//    textarea(
//      cls := "form-control",
//      name := "comments",
//      rows := 10,
//      cols := 80,
//      placeholder := "Enter publish comment here..."
//    ).render
//  }
//
//  // Message about missing username (XXX should be an easier way...)
//  private val userNameMissing = {
//    import scalatags.JsDom.all._
//    div(id := "userNameMissing", cls := "hide has-error",
//      label(cls := "control-label", "Username is required!"))
//  }
//
//  private def userNameMissingItem = $("#userNameMissing")
//
//  private def userNameChanged(): Unit = {
//    val userName = userNameBox.value
//    if (userName.isEmpty)
//      userNameMissingItem.removeClass("hide")
//    else
//      userNameMissingItem.addClass("hide")
//  }
//
//  // Publish user name field
//  private val userNameBox = {
//    import scalatags.JsDom.all._
//    input(
//      cls := "form-control",
//      name := "userName",
//      id := "userName",
//      onkeyup := userNameChanged _,
//      required,
//      placeholder := "Enter your user name..."
//    ).render
//  }
//
//  private val majorVersionCheckBox = {
//    import scalatags.JsDom.all._
//    input(tpe := "checkbox").render
//  }
//
//  /**
//   * Sets the message to display above the Publish button
//   */
//  private def setMessage(msg: Element): Unit = {
//    messageItem.innerHTML = ""
//    messageItem.appendChild(msg)
//  }
//
//  /**
//   * Sets the text to display for the Publish button
//   */
//  private def setPublishButtonLabel(s: String): Unit = publishButton.textContent = s
//
//  // True if published source and target subsystems are selected (enable publishing the ICD)
//  private def isPublishIcd(s: SubsystemWithVersion, t: SubsystemWithVersion): Boolean =
//    s.subsystemOpt.isDefined && s.versionOpt.isDefined && t.subsystemOpt.isDefined && t.versionOpt.isDefined
//
//  // True if an unpublished source subsystem is selected and no target (enable publishing the source API)
//  private def isPublishApi(s: SubsystemWithVersion, t: SubsystemWithVersion): Boolean =
//    s.subsystemOpt.isDefined && s.versionOpt.isEmpty && t.subsystemOpt.isEmpty
//
//  /**
//   * Called when the source or target subsystem was changed: Update the enabled states
//   */
//  def subsystemChanged(): Unit = {
//    import scalatags.JsDom.all._
//    val s = subsystem.getSubsystemWithVersion
//    val t = targetSubsystem.getSubsystemWithVersion
//
//    if (isPublishApi(s, t)) {
//      val source = s.subsystemOpt.get
//      publishButton.disabled = false
//      setMessage(p(s"Click below to publish the $source API").render)
//      setPublishButtonLabel("Publish API")
//    } else if (isPublishIcd(s, t)) {
//      val source = s.subsystemOpt.get
//      publishButton.disabled = false
//      val target = t.subsystemOpt.get
//      val targetVersion = t.versionOpt.get
//      val sourceVersion = s.versionOpt.get
//      setMessage(p(s"Click below to publish the ICD from $source $sourceVersion to $target $targetVersion").render)
//      setPublishButtonLabel("Publish ICD")
//    } else {
//      setMessage(p(
//        s"Please select an ",
//        em("unpublished"),
//        " (version = *) subsystem and the target ",
//        em("All"),
//        " to publish the API for the subsystem.",
//        br,
//        "Or select a published subsystem and target to publish the ICD from the subsystem to the target subsystem"
//      ).render)
//      setPublishButtonLabel("Publish (disabled)")
//      publishButton.disabled = true
//    }
//  }
//
//  def statusItem = $("#status")
//
//  def busyStatusItem = $("#busyStatus")
//
//  // Called when the publish button is pressed
//  def publishHandler(e: dom.Event): Unit = {
//    val userName = userNameBox.value
//    if (userName.isEmpty) {
//      userNameMissingItem.removeClass("hide")
//    } else {
//      statusItem.addClass("label-default").text("Working...")
//      statusItem.removeClass("label-danger")
//      busyStatusItem.removeClass("hide")
//
//      val majorVersion = majorVersionCheckBox.checked
//      val comment = commentBox.value
//
//      val s = subsystem.getSubsystemWithVersion
//      val t = targetSubsystem.getSubsystemWithVersion
//
//      if (isPublishApi(s, t)) {
//        val route = Routes.publishApi(s.subsystemOpt.get, majorVersion, comment, userName)
//        Ajax.post(route).map { r =>
//          subsystem.updateSubsystemVersionOptions()
//          displayResultStatus(r)
//        }
//      } else if (isPublishIcd(s, t)) {
//        val route = Routes.publishIcd(s.subsystemOpt.get, s.versionOpt.get, t.subsystemOpt.get, t.versionOpt.get, majorVersion, comment, userName)
//        Ajax.post(route).map { r =>
//          icdChooser.updateIcdOptions()
//          displayResultStatus(r)
//        }
//      }
//    }
//  }
//
//  // Displays the result status of the publish op
//  private def displayResultStatus(r: XMLHttpRequest): Unit = {
//    busyStatusItem.addClass("hide")
//    val statusClass = if (r.status == 200) "label-success" else "label-danger"
//    val statusMsg = if (r.status == 200) "Success" else r.statusText
//    statusItem.removeClass("label-default").addClass(statusClass).text(statusMsg)
//  }
//
//  // Produce the HTML to display for the publish screen
//  override def markup(): Element = {
//    import scalacss.ScalatagsCss._
//    import scalatags.JsDom.all._
//
//    div(
//      cls := "container",
//      messageItem,
//      div(cls := "panel panel-info")(
//        div(cls := "panel-body")(
//          div(Styles.commentBox, label("Comments")(commentBox)),
//          div(Styles.commentBox, label("Username")("*", userNameBox, userNameMissing)),
//          div(
//            div(cls := "checkbox")(label(majorVersionCheckBox, "Increment major version")),
//            publishButton
//          )
//        )
//      ),
//      h4("Status")(
//        span(style := "margin-left:15px;"),
//        span(id := "busyStatus", cls := "glyphicon glyphicon-refresh glyphicon-refresh-animate hide"),
//        span(style := "margin-left:15px;"),
//        span(id := "status", cls := "label", "Working...")
//      )
//    ).render
//  }
//}
