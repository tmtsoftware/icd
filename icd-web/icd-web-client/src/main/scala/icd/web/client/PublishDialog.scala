package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import scala.language.implicitConversions
import org.scalajs.jquery.{ jQuery ⇒ $, _ }

/**
 * Displays the page for publishing APIs or ICDs
 */
case class PublishDialog(subsystem: Subsystem, targetSubsystem: Subsystem) extends Displayable {

  // Message to display above publish button
  private val messageItem = {
    import scalatags.JsDom.all._
    p("Click below to publish").render
  }

  // Displays upload button
  private val publishButton = {
    import scalatags.JsDom.all._
    button(onclick := publishHandler _)("Publish").render
  }

  // Upload comment box
  private val commentBox = {
    import scalatags.JsDom.all._
    textarea(cls := "form-control", name := "comments", rows := 10, cols := 80).render
  }

  /**
   * Sets the message to display above the Publish button
   */
  private def setMessage(msg: String): Unit = messageItem.textContent = msg

  /**
   * Called when the source or target subsystem was changed
   */
  def subsystemChanged(): Unit = {
    val s = subsystem.getSubsystemWithVersion
    val t = targetSubsystem.getSubsystemWithVersion
    if (s.subsystemOpt.isDefined && s.versionOpt.isDefined) {
      publishButton.disabled = false
      val source = s.subsystemOpt.get
      val sourceVersion = s.versionOpt.get
      if (t.subsystemOpt.isDefined && t.versionOpt.isDefined) {
        // ICD
        val target = t.subsystemOpt.get
        val targetVersion = t.versionOpt.get
        val icdVersion = "1.0" // XXX TODO
        setMessage(s"Click below to publish version $icdVersion of the ICD from $source $sourceVersion to $target $targetVersion")
      } else {
        // API
        val apiVersion = "1.0" // XXX TODO
        setMessage(s"Click below to publish version $apiVersion of the $source API (based on $source $sourceVersion)")
      }
    } else {
      // Disabled
      setMessage(s"Please select the subsystem for the API or the source and target subsystems for an ICD")
      publishButton.disabled = true
    }
  }

  // Called when the publish button is pressed
  def publishHandler(e: dom.Event): Unit = {
    println(s"XXX Publish")
  }

  // Produce the HTML to display for the upload screen
  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    import scalatags.JsDom.all._

    div(cls := "container",
      messageItem,
      div(cls := "panel panel-info")(
        div(cls := "panel-body")(
          div(publishButton),
          div(Styles.commentBox, label("Comments")(commentBox))))).render
  }
}
