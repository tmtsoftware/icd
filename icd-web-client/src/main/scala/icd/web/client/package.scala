package icd.web

import icd.web.client.BrowserHistory.ViewType
import icd.web.shared.{IcdVersion, SubsystemWithVersion}
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Div, Input}
import org.scalajs.dom.{DOMList, Node, document}

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import scalatags.JsDom

/**
 * Common definitions
 */
package object client {

  // Hide or show the sidebar
  def setSidebarVisible(show: Boolean): Unit = {
    val s = document.querySelector("#sidebar")
    if (show) {
      s.classList.remove("d-none")
    }
    else {
      s.classList.add("d-none")
    }
  }

  /**
   * Push (or replace) the current app state for the browser history.
   * (Replace is needed if the browser is following a link, in which case the browser automatically pushes something
   * on the stack that we don't want.)
   *
   * If a single component is selected, it should be passed as compName.
   */
  def pushState(
      viewType: ViewType,
      compName: Option[String] = None,
      maybeSourceSubsystem: Option[SubsystemWithVersion] = None,
      maybeTargetSubsystem: Option[SubsystemWithVersion] = None,
      maybeIcd: Option[IcdVersion] = None,
      maybeUri: Option[String] = None
  ): Unit = {
    val hist = BrowserHistory(
      maybeSourceSubsystem,
      maybeTargetSubsystem,
      maybeIcd,
      viewType,
      compName,
      maybeUri
    )
    hist.pushState()
  }

  // Show/hide the busy cursor and disable all while the future is running
  def showBusyCursorWhile(f: Future[Unit]): Future[Unit] = {
    // Note: See implicit NodeList to List support in package object in this dir
    document.querySelectorAll("*").foreach { e =>
      e.classList.add("change-cursor")
//      e.classList.add("pe-none")
    }
    f.onComplete { _ =>
      document.querySelectorAll("*").foreach { e =>
        e.classList.remove("change-cursor")
//        e.classList.remove("pe-none")
      }
    }
    f
  }

  def $id(s: String): Element = dom.document.getElementById(s)

  // Returns an HTML div containing the given error message
  def errorDiv(msg: String): String = {
    import scalatags.JsDom.all.*
    div(cls := "alert alert-danger card-body", role := "alert")(
      span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
      span(cls                                                         := "sr-only", " Error: "),
      s" $msg"
    ).toString()
  }

  // Returns an HTML div containing the given warning message
  def warningDiv(msg: String): String = {
    import scalatags.JsDom.all.*
    div(cls := "alert alert-warning card-body", role := "alert")(
      span(i(cls := "bi bi-exclamation-triangle"), attr("aria-hidden") := "true"),
      span(cls                                                         := "sr-only", " Warning: "),
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

  def makeRadioButton(
      nameStr: String,
      valueStr: String,
      defaultValue: String,
      units: Option[String] = None
  ): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all.*
    val unitsStr   = units.map(" " + _).getOrElse("")
    val defaultStr = if (valueStr == defaultValue) " (default)" else ""
    val labelStr   = s"$valueStr$unitsStr$defaultStr"
    div(cls := "form-check")(
      if (valueStr == defaultValue)
        input(`type` := "radio", cls := "form-check-input", name := nameStr, value := valueStr, checked)
      else
        input(`type` := "radio", cls := "form-check-input", name := nameStr, value := valueStr),
      label(cls := "form-check-label", labelStr)
    )
  }

  def makeCheckbox(nameStr: String, valueStr: String, isSelected: Boolean): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all.*
    div(cls := "form-check")(
      if (isSelected)
        input(`type` := "checkbox", cls := "form-check-input", id := nameStr, name := nameStr, checked)
      else
        input(`type` := "checkbox", cls := "form-check-input", id := nameStr, name := nameStr),
      label(cls := "form-check-label", valueStr)
    )
  }

  def makeNumberEntry(nameStr: String, defaultValue: String): JsDom.TypedTag[Input] = {
    import scalatags.JsDom.all.*
    input(id := nameStr, `type` := "number", min := 0, name := nameStr, value := defaultValue)
  }
}
