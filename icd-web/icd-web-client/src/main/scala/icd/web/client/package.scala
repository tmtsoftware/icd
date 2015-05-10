package icd.web

import org.scalajs.dom
import org.scalajs.dom._


/**
 * Common definitions
 */
package object client {
  def $id(s: String) = dom.document.getElementById(s)

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
