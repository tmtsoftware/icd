package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._

/**
 * A simple navbar item with the given label.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked
 */
case class NavbarItem(labelStr: String, tip: String, listener: () => Unit) extends Displayable {
  import scalatags.JsDom.all._
  private val item = li(a(onclick := listener, title := tip)(labelStr)).render

  // Returns the HTML markup for the navbar item
  def markup(): Element = item

  def hide(): Unit = item.classList.add("hide")
}

/**
 * A navbar dropdown menu
 *
 * @param labelStr label for the navbar item
 * @param tip tool tip
 * @param items items in menu
 * @param listener call this when an item is selected
 */
case class NavbarDropDownItem(labelStr: String, tip: String, items: List[String], listener: String => Unit) extends Displayable {
  import scalatags.JsDom.all._

  // called when an item is selected
  private def itemSelected(item: String)(e: dom.Event): Unit = {
    e.preventDefault()
    listener(item)
  }

  private val item = li(cls := "dropdown")(
    a(href := "#", title := tip, cls := "dropdown-toggle", attr("data-toggle") := "dropdown", role := "button")(
      labelStr,
      span(cls := "caret")
    ),
    ul(
      cls := "dropdown-menu",
      items.map { item =>
        li(a(href := "#", onclick := itemSelected(item) _)(item))
      }
    )
  ).render

  // Returns the HTML markup for the navbar item
  def markup(): Element = item

  def hide(): Unit = item.classList.add("hide")
}
