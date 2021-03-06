package icd.web.client

import org.scalajs.dom.Element

/**
 * Displays a button that, when pressed, swaps source and target subsystems
 */
case class SubsystemSwapper(listener: () => Unit) extends Displayable {

  private val swapperItem = {
    import scalatags.JsDom.all._
    button(
      tpe := "button",
      cls := "btn btn-default glyphicon glyphicon-resize-vertical",
      title := "Swap first and second subsystems",
      onclick := listener
    ).render
  }

  override def markup(): Element = {
    import scalatags.JsDom.all._
    a(swapperItem).render
  }

  def setEnabled(enabled: Boolean): Unit = {
    if (enabled) {
      swapperItem.removeAttribute("disabled")
    } else {
      swapperItem.setAttribute("disabled", "true")
    }
  }

}
