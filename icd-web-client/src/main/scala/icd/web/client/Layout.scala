package icd.web.client

import org.scalajs.dom.*
import org.scalajs.dom.html.Div
import scalacss.ScalatagsCss.*
import scalatags.JsDom.all.*

/**
 * Manages the main layout (below the navbar)
 */
case class Layout() extends Displayable {
  private val row: Div = div(cls := "row h-100").render
  private val container: Div = div(Styles.layout, cls :=   "container-fluid vh-100")(row).render

  override def markup(): Element = container

  /**
   * Adds an item to the layout.
   *
   * @param displayable the item to be added
   */
  def addItem(displayable: Displayable): Unit = {
    row.appendChild(displayable.markup())
  }
}
