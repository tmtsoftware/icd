package icd.web.client

import org.scalajs.dom.Element

/**
 * Displays a button that, when pressed, swaps source and target subsystems
 */
case class SubsystemSwapper(listener: () ⇒ Unit) extends Displayable {

  /**
   * Returns the initial HTML markup for the item
   */
  override def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(button(
      tpe := "button",
      cls := "glyphicon glyphicon-resize-horizontal",
      title := "Swap source and target subsystems",
      onclick := listener
    ))).render
  }
}
