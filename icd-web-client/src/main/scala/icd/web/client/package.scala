package icd.web

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.{DOMList, Node, document}

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

/**
 * Common definitions
 */
package object client {

  // Show/hide the busy cursor while the future is running
  def showBusyCursorWhile(f: Future[Unit]): Future[Unit] = {
    // Note: See implicit NodeList to List support in package object in this dir
    document.querySelectorAll("*").foreach(_.classList.add("change-cursor"))
    f.onComplete { _ =>
      document.querySelectorAll("*").foreach(_.classList.remove("change-cursor"))
    }
    f
  }

  def $id(s: String): Element = dom.document.getElementById(s)

  // Returns an HTML div containing the given error message
  def errorDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-danger", role := "alert")(
      span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
      span(cls := "sr-only", "Error"),
      s" $msg"
    ).toString()
  }

  // Returns an HTML div containing the given warning message
  def warningDiv(msg: String): String = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-warning", role := "alert")(
      span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
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
