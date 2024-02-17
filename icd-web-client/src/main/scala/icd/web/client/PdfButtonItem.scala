package icd.web.client

import icd.web.shared.PdfOptions
import org.scalajs.dom.{Element, HTMLInputElement, document}
import org.scalajs.dom.html.{Button, Div}
import scalatags.JsDom

/**
 * A button item with the given label that displays a popup with PDF options.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked with (orientation, fontSize)
 * @param showDocumentNumber if true, show the  Document Number field
 * @param showDetailButtons if true, show the event details radio buttons
 */
case class PdfButtonItem(
    labelStr: String,
    tip: String,
    listener: PdfOptions => Unit,
    showDocumentNumber: Boolean,
    showDetailButtons: Boolean = true
) extends Displayable {
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
    val documentNumber =
      if (showDocumentNumber)
        document
          .querySelectorAll(s"input[name='documentNumber$labelStr']")
          .map(elem => elem.asInstanceOf[HTMLInputElement].value)
          .toList
          .head
      else ""

    val expandedLinkIds = if (details) Nil else getExpandedIds
    listener(
      PdfOptions(
        orientation,
        fontSize,
        lineHeight,
        paperSize,
        details,
        expandedLinkIds,
        processMarkdown = true,
        documentNumber = documentNumber
      )
    )
  }

  // Finds all button items with aria-expanded=true, then gets the "name" attr of the following "a" element.
  // Returns list of ids for expanded rows.
  private def getExpandedIds: List[String] = {
    try {
      Array
        .from(document.querySelectorAll("[aria-expanded]"))
        .flatMap { el =>
          val attr = el.attributes.getNamedItem("aria-expanded")
          if (attr.value == "true" && el.nodeName.equalsIgnoreCase("button")) {
            Some(el.parentNode.lastChild.attributes.getNamedItem("id").value)
          }
          else None
        }
        .toList
    }
    catch {
      case ex: Exception =>
        ex.printStackTrace()
        Nil
    }
  }

  // Makes the popup with options for generating the PDF
  private def makePdfModal(): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all.*
    val docNumCls       = if (showDocumentNumber) "docNum" else "d-none"
    val eventDetailsCls = if (showDetailButtons) "eventDetails" else "d-none"
    div(cls := "modal fade", id := s"pdfModal$labelStr", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-bs-dismiss") := "modal")(raw("&times;")),
            h4(cls := "modal-title")("PDF Options")
          ),
          div(cls := "modal-body")(
            form(
              h5(s"Orientation:"),
              PdfOptions.orientations.map(x => makeRadioButton(s"orientation$labelStr", x, PdfOptions.defaultOrientation)),
              p(),
              hr,
              p(),
              h5(s"Font Size:"),
              PdfOptions.fontSizes
                .map(x => makeRadioButton(s"fontSize$labelStr", x.toString, PdfOptions.defaultFontSize.toString, Some("px"))),
              hr,
              p(),
              h5(s"Line Height:"),
              PdfOptions.lineHeights.map(x => makeRadioButton(s"lineHeight$labelStr", x, PdfOptions.defaultLineHeight)),
              hr,
              p(),
              h5(s"Paper Size:"),
              PdfOptions.paperSizes.map(x => makeRadioButton(s"paperSize$labelStr", x, PdfOptions.defaultPaperSize)),
              hr,
              p(),
              h5(cls := eventDetailsCls, "Details:"),
              div(
                cls := s"form-check $eventDetailsCls",
                input(`type` := "radio", cls := "form-check-input", name := s"details$labelStr", value := "true", checked),
                label(cls := "form-check-label", "Show the details for all events, commands, alarms (default)")
              ),
              div(
                cls := s"form-check $eventDetailsCls",
                input(`type` := "radio", cls := "form-check-input", name := s"details$labelStr", value := "false"),
                label(cls := "form-check-label", "Include only the details that are expanded in the HTML view")
              ),
              hr(cls := docNumCls),
              p(cls := docNumCls),
              h5(cls := docNumCls, s"Document Number:"),
              input(cls := docNumCls, id := s"documentNumber$labelStr", name := s"documentNumber$labelStr")
            )
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "btn btn-secondary", attr("data-bs-dismiss") := "modal")("Cancel"),
            button(
              onclick := pdfModalListener _,
              `type` := "button",
              cls := "btn btn-primary",
              attr("data-bs-dismiss") := "modal"
            )("Apply")
          )
        )
      )
    )
  }

  private val item: Button = {
    import scalatags.JsDom.all.*
    button(
      `type` := "button",
      cls := "btn btn-secondary",
      title := tip,
      attr("data-bs-toggle") := "modal",
      attr("data-bs-target") := s"#pdfModal$labelStr"
    )(labelStr).render
  }

  private val pdfModal = makePdfModal()

  override def setEnabled(enabled: Boolean): Unit = {
    if (enabled)
      item.classList.remove("disabled")
    else
      item.classList.add("disabled")
  }

  override def markup(): Element = {
    import scalatags.JsDom.all.*

    div(cls := "selectDialogButton btn-group", item, pdfModal).render
  }

}
