package icd.web.client

import org.scalajs.dom.Element

/**
 * Displays a button that, when pressed, expands or collapses all the hidden table rows
 */
case class ExpandToggler() extends Displayable {

  private val item = {
    import scalatags.JsDom.all.*

    button(
      cls := "attributeBtn btn btn-sm d-none",
      tpe := "button",
      id := "expand-init",
      attr("data-bs-toggle") := "collapse",
      attr("data-bs-target") := ".panel-collapse",
      title := "Expand or collapse all detailed information"
    )(i(cls := "navbarBtn bi bi-caret-down-square")).render
  }

  override def setEnabled(enabled: Boolean): Unit = {
    item.disabled = !enabled
  }

  def setVisible(show: Boolean): Unit = {
    if (show) {
      item.classList.remove("d-none")
    }
    else {
      item.classList.add("d-none")
    }
  }

  /**
   * Returns the initial HTML markup for the item
   */
  override def markup(): Element = {
    import scalatags.JsDom.all.*
    li(a(item)
    ).render
  }
}
