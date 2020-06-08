package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.html.Div
import org.scalajs.dom.raw.HTMLInputElement
import scalatags.JsDom

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
 * A navbar item with the given label that displays a popup with PDF options.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked with (orientation, fontSize)
 */
case class NavbarPdfItem(labelStr: String, tip: String, listener: (String, Int) => Unit) extends Displayable {
  import scalatags.JsDom.all._

  private def pdfModalListener(): Unit = {
    val orientation = document
      .querySelectorAll(s"input[name='orientation$labelStr']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
    val fontSize = document
      .querySelectorAll(s"input[name='fontSize$labelStr']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
      .toInt
    listener(orientation, fontSize)
  }

  // Makes the popup with options for generating the PDF
  private def makePdfModal(): JsDom.TypedTag[Div] = {
    div(cls := "modal fade", id := s"pdfModal$labelStr", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-dismiss") := "modal")(raw("&times;")),
            h4(cls := "modal-title")("PDF Options")
          ),
          div(cls := "modal-body")(
            form(
              h5(s"Orientation:"),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"orientation$labelStr", value := "portrait"))("portrait")
              ),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"orientation$labelStr", value := "landscape", checked))("landscape")
              ),
              p(),
              hr,
              p(),
              h5(s"Font Size:"),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"fontSize$labelStr", value := "10", checked))("Default")
              ),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"fontSize$labelStr", value := "12"))("L")
              ),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"fontSize$labelStr", value := "14"))("XL")
              ),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"fontSize$labelStr", value := "16"))("XXL")
              )
            )
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "btn btn-default", attr("data-dismiss") := "modal")("Cancel"),
            button(
              onclick := pdfModalListener _,
              `type` := "button",
              cls := "btn btn-primary",
              attr("data-dismiss") := "modal"
            )("Apply")
          )
        )
      )
    )
  }

  private val item = li(
    makePdfModal(),
    a(
      href := "#",
      title := tip,
      attr("data-toggle") := "modal",
      attr("data-target") := s"#pdfModal$labelStr"
    )(labelStr)
  ).render

  // Returns the HTML markup for the navbar item
  def markup(): Element = item

  def hide(): Unit = item.classList.add("hide")

  def setEnabled(enabled: Boolean): Unit = {
    if (enabled)
      item.classList.remove("disabled")
    else
      item.classList.add("disabled")
  }
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
