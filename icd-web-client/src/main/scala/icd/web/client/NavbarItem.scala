package icd.web.client

import icd.web.shared.PdfOptions
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
case class NavbarPdfItem(labelStr: String, tip: String, listener: PdfOptions => Unit) extends Displayable {
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
    val lineHeight = document
      .querySelectorAll(s"input[name='lineHeight$labelStr']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
    val paperSize = document
      .querySelectorAll(s"input[name='paperSize$labelStr']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
    val details = document
      .querySelectorAll(s"input[name='details$labelStr']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
      .toBoolean
    listener(PdfOptions(orientation, fontSize, lineHeight, paperSize, details))
  }

  private def makeRadioButton(nameStr: String, valueStr: String, defaultValue: String, units: Option[String] = None) = {
    val unitsStr = units.map(" " + _).getOrElse("")
    div(cls := "radio-inline")(
      if (valueStr == defaultValue)
        label(input(`type` := "radio", name := nameStr, value := valueStr, checked))(s"$valueStr$unitsStr (default)")
      else
        label(input(`type` := "radio", name := nameStr, value := valueStr))(s"$valueStr$unitsStr")
    )
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
              PdfOptions.orientations.map(x =>
                makeRadioButton(s"orientation$labelStr", x, PdfOptions.defaultOrientation)
              ),
              p(),
              hr,
              p(),
              h5(s"Font Size:"),
              PdfOptions.fontSizes.map(x =>
                makeRadioButton(s"fontSize$labelStr", x.toString, PdfOptions.defaultFontSize.toString, Some("px"))
              ),
              hr,
              p(),
              h5(s"Line Height:"),
              PdfOptions.lineHeights.map(x =>
                makeRadioButton(s"lineHeight$labelStr", x, PdfOptions.defaultLineHeight)
              ),
              hr,
              p(),
              h5(s"Paper Size:"),
              PdfOptions.paperSizes.map(x =>
                makeRadioButton(s"paperSize$labelStr", x, PdfOptions.defaultPaperSize)
              ),
              hr,
              p(),
              h5(s"Details:"),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"details$labelStr", value := "true", checked))(
                  "Show the details for all events, commands, alarms (default)"
                )
              ),
              div(cls := "radio")(
                label(input(`type` := "radio", name := s"details$labelStr", value := "false"))(
                  "Include only the details that are expanded in the HTML view"
                )
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
