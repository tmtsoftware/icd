package icd.web.client

import org.scalajs.dom._

/**
 * A simple navbar item with the given label.
 *
 * @param labelStr the label to display
 * @param listener called when the item is clicked
 */
case class NavbarItem(labelStr: String, listener: () => Unit) extends Displayable {

  // Returns the HTML markup for the navbar item
  def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(onclick := listener)(labelStr)).render
  }
}
