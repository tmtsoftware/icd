package icd.web.client

import org.scalajs.dom.Element
import scalatags.JsDom.all._

/**
 * Manages the navbar
 */
case class Navbar() extends Displayable {

  private val leftNavbar  = ul(cls := "navbar-nav").render
  private val rightNavbar = ul(cls := "navbar-nav ms-auto mb-2 mb-lg-0").render

  def markup(): Element = {
    import scalatags.JsDom.tags2._
      nav(cls := "navbar navbar-expand-md navbar-light bg-light")(
        div(cls := "container-fluid")(
          a(cls := "navbar-brand", href := "/")("TMT Interface Database System"),
          button(
            cls := "navbar-toggler",
            `type` := "button",
            attr("data-bs-toggle") := "collapse",
            attr("data-bs-target") := "#icd-navbar"
          )(
            i(cls := "fas fa-bars")
          ),
          div(id := "icd-navbar", cls := "collapse navbar-collapse")(
            leftNavbar,
            rightNavbar
          )
        )
    ).render
  }

  /**
   * Adds an item to the navbar.
   *
   * @param displayable the item to be added
   */
  def addItem(displayable: Displayable): Unit = leftNavbar.appendChild(displayable.markup())

  /**
   * Adds an item to the navbar on the right side.
   *
   * @param displayable the item to be added
   */
  def addRightSideItem(displayable: Displayable): Unit = rightNavbar.appendChild(displayable.markup())

}
