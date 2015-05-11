package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.Element

import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import org.scalajs.dom.raw.Node

/**
 * Manages the navbar
 */
object Navbar {

  val leftNavbar = ul(cls := "nav navbar-nav").render
  val rightNavbar = ul(cls := "nav navbar-nav pull-right").render

  private def markup(): TypedTag[Element] = {
    import scalatags.JsDom.tags2._

    nav(cls := "navbar navbar-default navbar-fixed-top", role := "navigation")(
      div(cls := "navbar-header")(
        button(`type` := "button", cls := "navbar-toggle", "data-toggle".attr := "collapse", "data-target".attr := "#icd-navbar")(
          span(cls := "sr-only")("Toggle navigation/span"),
          span(cls := "icon-bar"),
          span(cls := "icon-bar"),
          span(cls := "icon-bar")
        ),
        a(cls := "navbar-brand")("TMT ICD Database")
      ),
      div(cls := "collapse navbar-collapse")(
        leftNavbar, rightNavbar
      )
    )
  }

  // Creates the navbar item
  def init(): Unit = {
    dom.document.body.appendChild(markup().render)
  }

  /**
   * Adds an HTML element to the navbar.
   * @param node a scalatags element
   */
  def addItem(node: Node): Unit = leftNavbar.appendChild(node)

  /**
   * Adds an HTML element to the navbar on the right side.
   * @param node a scalatags element
   */
  def addRightSideItem(node: Node): Unit = rightNavbar.appendChild(node)

}
