package icd.web

import org.scalajs.dom
import org.scalajs.dom.{DOMList, Node}

/**
 * Common definitions
 */
package object client {
  def $id(s: String) = dom.document.getElementById(s)

  // Returns an HTML div containing the given error message
  def errorDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-danger", role := "alert")(
      span(cls := "glyphicon glyphicon-exclamation-sign", attr("aria-hidden") := "true"),
      span(cls := "sr-only", "Error"),
      s" $msg"
    ).toString()
  }

  // Returns an HTML div containing the given warning message
  def warningDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-warning", role := "alert")(
      span(cls := "glyphicon glyphicon-warning-sign", attr("aria-hidden") := "true"),
      span(cls := "sr-only", "Warning"),
      s" $msg"
    ).toString()
  }

  // Support for working with NodeList:
  // See https://www.scala-js.org/doc/sjs-for-js/es6-to-scala-part3.html
  implicit class NodeListSeq[T <: Node](nodes: DOMList[T]) extends IndexedSeq[T] {
    override def foreach[U](f: T => U): Unit = {
      for (i <- 0 until nodes.length) {
        f(nodes(i))
      }
    }

    override def length: Int = nodes.length

    override def apply(idx: Int): T = nodes(idx)
  }
}
