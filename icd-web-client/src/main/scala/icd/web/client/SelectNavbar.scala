package icd.web.client

import org.scalajs.dom.Element
import scalatags.JsDom.all.*

/**
 * Manages the navbar for the Select dialog (Used to select an API or ICD to view)
 */
case class SelectNavbar() extends Displayable {

  private val leftNavbar  = ul(cls := "navbar-nav").render

  def markup(): Element = {

    import scalatags.JsDom.tags2.*
    nav(cls := "navbar navbar-expand-lg bg-light border-bottom")(
      div(cls := "container-fluid")(
        div(id := "icd-navbar", cls := "collapse navbar-collapse")(
          leftNavbar
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
}
