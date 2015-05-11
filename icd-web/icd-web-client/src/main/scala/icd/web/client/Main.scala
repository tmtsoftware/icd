package icd.web.client

import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLStyleElement

import scala.scalajs.js
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalacss.Defaults._
import org.scalajs.dom.Element

import scalatags.JsDom.TypedTag

/**
 * Manages the main content section
 */
object Main {
  val contentTitleId = "contentTitle"
  lazy val contentTitle = $id(contentTitleId)

  val contentId = "content"
  lazy val content = $id(contentId)

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, content: String): Unit = {
    contentTitle.textContent = title
    this.content.innerHTML = content
  }

  // Sets the title and HTML content of the main section of the page
  def setContentTitle(title: String): Unit = {
    contentTitle.textContent = title
  }

  def clearContent(): Unit = setContent("", "")

  def displayInternalError(ex: Throwable): Unit = {
    // Display an error message
    println(s"Internal error: $ex")
    setContent("Internal Error", errorDiv("Internal error. The database may be down."))
  }

  private def markup() = {
    import scalacss.ScalatagsCss._
    import scalatags.JsDom.all._
    //Styles.render[TypedTag[HTMLStyleElement]], Styles.mainWrapper,
    div(Styles.mainWrapper)(
      div(Styles.main)(
        h3(id := contentTitleId, cls := "page-header"),
        div(id := contentId)
      )
    )
  }

  def init(): Unit = {
    Layout.addItem(markup())
  }
}
