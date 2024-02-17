package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.html.{Button, UList}
import scalatags.JsDom

/**
 * A navbar dropdown menu
 *
 * @param labelStr label for the navbar item
 * @param tip tool tip
 * @param items items in menu
 * @param listener call this when an item is selected
 */
case class DropDownButtonItem(labelStr: String, tip: String, items: List[String], listener: String => Unit)
extends Displayable {
  import scalatags.JsDom.all.*

  // called when an item is selected
  private def itemSelected(item: String)(e: dom.Event): Unit = {
    e.preventDefault()
    listener(item)
  }

  private val item: Button = {
    import scalatags.JsDom.all.*
    button(
      `type` := "button",
      cls := "btn btn-secondary dropdown-toggle",
      title := tip,
      attr("data-bs-toggle") := "dropdown",
      attr("aria-expanded") := "false"
    )(labelStr).render
  }

  private val dropdown: JsDom.TypedTag[UList] = {
    import scalatags.JsDom.all.*
    ul(
      cls := "dropdown-menu",
      items.map { item =>
        li(a(cls := "dropdown-item", href := "#", onclick := itemSelected(item) _)(item))
      }
    )
  }

  override def setEnabled(enabled: Boolean): Unit = {
    if (enabled)
      item.classList.remove("disabled")
    else
      item.classList.add("disabled")
  }

  override def markup(): Element = {
    import scalatags.JsDom.all.*

    div(cls := "selectDialogButton btn-group", item, dropdown).render
  }

}
