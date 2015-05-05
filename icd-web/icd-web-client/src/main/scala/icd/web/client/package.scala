package icd.web

import org.scalajs.dom
import org.scalajs.dom._


/**
 * Common definitions
 */
package object client {
  def $id(s: String) = dom.document.getElementById(s)

  val contentTitle = $id("contentTitle")
  val content = $id("content")

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, content: String): Unit = {
    contentTitle.textContent = title
    this.content.innerHTML = content
  }

  // Returns an HTML div containing the given error message
  def errorDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-danger", role := "alert")(
      span(cls := "glyphicon glyphicon-exclamation-sign", "aria-hidden".attr := "true"),
      span(cls := "sr-only", "Error"), s" $msg").toString()
  }

  // Returns an HTML div containing the given warning message
  def warningDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-warning", role := "alert")(
      span(cls := "glyphicon glyphicon-warning-sign", "aria-hidden".attr := "true"),
      span(cls := "sr-only", "Warning"), s" $msg").toString()
  }

  def displayInternalError(ex: Throwable): Unit = {
    // Display an error message
    println(s"Internal error: $ex")
    setContent("Internal Error", errorDiv("Can't get the list of ICDs from the server. The database may be down."))
  }

  /**
   * Describes any validation problems found
   * @param severity a string describing the error severity: fatal, error, warning, etc.
   * @param message describes the problem
   */
  case class Problem(severity: String, message: String) {
    def errorMessage(): String = s"$severity: $message"
  }

  /**
   * Removes all the children from the given HTML element
   */
  def clearElement(elem: Element): Unit = {
    val children = elem.childNodes
    for (i <- (0 until children.length).reverse) {
      elem.removeChild(children(i))
    }
  }
}
