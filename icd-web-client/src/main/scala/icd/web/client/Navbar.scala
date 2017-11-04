package icd.web.client

import org.scalajs.dom.Element

import scalatags.JsDom.all._

/**
 * Manages the navbar
 */
case class Navbar() extends Displayable {

  val leftNavbar = ul(cls := "nav navbar-nav").render
  val rightNavbar = ul(cls := "nav navbar-nav pull-right").render

  def markup(): Element = {
    import scalatags.JsDom.tags2._

    nav(cls := "navbar navbar-default navbar-fixed-top hidden-print", role := "navigation")(
      div(cls := "navbar-header")(
        button(`type` := "button", cls := "navbar-toggle", attr("data-toggle") := "collapse", attr("data-target") := "#icd-navbar")(
          span(cls := "sr-only")("Toggle navigation/span"),
          span(cls := "icon-bar"),
          span(cls := "icon-bar"),
          span(cls := "icon-bar")
        ),
        a(cls := "navbar-brand", href := "/")("TMT ICD Database")
      ),
      div(id := "icd-navbar", cls := "collapse navbar-collapse")(
        leftNavbar, rightNavbar
      )
    ).render
  }

  /**
   * Adds an item to the navbar.
   * @param displayable the item to be added
   */
  def addItem(displayable: Displayable): Unit = leftNavbar.appendChild(displayable.markup())

  /**
   * Adds an item to the navbar on the right side.
   * @param displayable the item to be added
   */
  def addRightSideItem(displayable: Displayable): Unit = rightNavbar.appendChild(displayable.markup())

}
