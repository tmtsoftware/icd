package icd.web.client

import org.scalajs.dom._
import org.scalajs.dom.html.Div
import scalacss.ScalatagsCss._
import scalatags.JsDom.all._

/**
 * Manages the main layout (below the navbar)
 */
case class Layout() extends Displayable {
  val wrapper: Div = div(Styles.layout, cls := "flex-nowrap").render

  override def markup(): Element = wrapper

  /**
   * Adds an item to the layout.
   *
   * @param displayable the item to be added
   */
  def addItem(displayable: Displayable): Unit = {
    wrapper.appendChild(displayable.markup())
  }
}
