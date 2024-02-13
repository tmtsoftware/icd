package icd.web.client

import org.scalajs.dom.*

/**
 * A simple navbar item with the given label.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked
 */
case class NavbarItem(labelStr: String, tip: String, listener: () => Unit) extends Displayable {
  import scalatags.JsDom.all.*
  private val item = li(cls := "nav-item", a(cls := "nav-link", onclick := listener, title := tip)(labelStr)).render

  // Returns the HTML markup for the navbar item
  def markup(): Element = item

  def hide(): Unit = item.classList.add("d-none")
}
