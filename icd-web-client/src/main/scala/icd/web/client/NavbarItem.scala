package icd.web.client

import icd.web.shared.{IcdVizOptions, PdfOptions}
import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.html.{Div, Input}
import org.scalajs.dom.raw.HTMLInputElement
import scalatags.JsDom
import NavbarItem._

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

object NavbarItem {
  def makeRadioButton(
      nameStr: String,
      valueStr: String,
      defaultValue: String,
      units: Option[String] = None
  ): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all._
    val unitsStr = units.map(" " + _).getOrElse("")
    div(cls := "radio-inline")(
      if (valueStr == defaultValue)
        label(input(`type` := "radio", name := nameStr, value := valueStr, checked))(s"$valueStr$unitsStr (default)")
      else
        label(input(`type` := "radio", name := nameStr, value := valueStr))(s"$valueStr$unitsStr")
    )
  }

  def makeCheckbox(nameStr: String, valueStr: String, isSelected: Boolean): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all._
    div(cls := "checkbox")(
      if (isSelected)
        label(input(`type` := "checkbox", id := nameStr, name := nameStr, checked), valueStr)
      else
        label(input(`type` := "checkbox", id := nameStr, name := nameStr), valueStr)
    )
  }

  def makeNumberEntry(nameStr: String, defaultValue: String): JsDom.TypedTag[Input] = {
    import scalatags.JsDom.all._
    input(id := nameStr, `type` := "number", min := 0, name := nameStr, value := defaultValue)
  }
}

/**
 * A navbar item with the given label that displays a popup with PDF options.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked with (orientation, fontSize)
 */
case class NavbarPdfItem(labelStr: String, tip: String, listener: PdfOptions => Unit) extends Displayable {
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

    val expandedLinkIds = if (details) Nil else getExpandedIds
    listener(PdfOptions(orientation, fontSize, lineHeight, paperSize, details, expandedLinkIds, processMarkdown = true))
  }

  // Finds all button items with aria-expanded=true, then gets the "name" attr of the following "a" element.
  // Returns list of ids for expanded rows.
  private def getExpandedIds: List[String] = {
    Array
      .from(document.querySelectorAll("[aria-expanded]"))
      .flatMap { el =>
        val attr = el.attributes.getNamedItem("aria-expanded")
        if (attr.value == "true" && el.nodeName.equalsIgnoreCase("button")) {
          Some(el.parentNode.lastChild.attributes.getNamedItem("name").value)
        } else None
      }
      .toList
  }

  // Makes the popup with options for generating the PDF
  private def makePdfModal(): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all._
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

  private val item = {
    import scalatags.JsDom.all._
    li(
      makePdfModal(),
      a(
        href := "#",
        title := tip,
        attr("data-toggle") := "modal",
        attr("data-target") := s"#pdfModal$labelStr"
      )(labelStr)
    ).render
  }

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

/**
 * A navbar item that displays a popup with icd-viz options for displaying a graph of component relationships.
 *
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked with (orientation, fontSize)
 */
case class NavbarGraphItem(labelStr: String, tip: String, listener: IcdVizOptions => Unit) extends Displayable {
  import IcdVizOptions._
  private def graphModalListener(): Unit = {
    val aspectRatio =
      Option(document.getElementById("aspectRatio").asInstanceOf[HTMLInputElement].valueAsNumber).getOrElse(defaultRatio)
    val plotMissingEvents   = document.getElementById("plotMissingEvents").asInstanceOf[HTMLInputElement].checked
    val plotMissingCommands = document.getElementById("plotMissingCommands").asInstanceOf[HTMLInputElement].checked
    val plotEventLabels     = document.getElementById("plotEventLabels").asInstanceOf[HTMLInputElement].checked
    val plotCommandLabels   = document.getElementById("plotCommandLabels").asInstanceOf[HTMLInputElement].checked
    val groupSubsystems     = document.getElementById("groupSubsystems").asInstanceOf[HTMLInputElement].checked
    val graphLayout = document
      .querySelectorAll(s"input[name='graphLayout']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
    val graphOverlap = document
      .querySelectorAll(s"input[name='graphOverlap']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head
    val useSplines = document.getElementById("useSplines").asInstanceOf[HTMLInputElement].checked
    val graphOmitTypes = document
      .querySelectorAll(s"input[name='graphOmitType']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .filter(_ != "None")
    val imageFormat = document
      .querySelectorAll(s"input[name='imageFormat']:checked")
      .map(elem => elem.asInstanceOf[HTMLInputElement].value)
      .toList
      .head

    listener(
      IcdVizOptions(
        ratio = aspectRatio,
        missingEvents = plotMissingEvents,
        missingCommands = plotMissingCommands,
        commandLabels = plotCommandLabels,
        eventLabels = plotEventLabels,
        groupSubsystems = groupSubsystems,
        layout = graphLayout,
        overlap = graphOverlap,
        splines = useSplines,
        omitTypes = graphOmitTypes,
        imageFormat = imageFormat
      )
    )
  }

  // Makes the popup with options for generating the graph
  private def makeGraphModal(): JsDom.TypedTag[Div] = {
    import scalatags.JsDom.all._
    div(cls := "modal fade", id := s"graphModal", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-dismiss") := "modal")(raw("&times;")),
            h4(cls := "modal-title")("Graph Options")
          ),
          div(cls := "modal-body")(
            form(
              p(s"Aspect ratio (y/x): ", makeNumberEntry("aspectRatio", s"$defaultRatio")),
              makeCheckbox("plotMissingEvents", "Plot missing events", isSelected = defaultMissingEvents),
              makeCheckbox("plotMissingCommands", "Plot missing commands", isSelected = defaultMissingCommands),
              makeCheckbox("plotEventLabels", "Plot event labels", isSelected = defaultEventLabels),
              makeCheckbox("plotCommandLabels", "Plot command labels", isSelected = defaultCommandLabels),
              makeCheckbox(
                "groupSubsystems",
                "Group components from same subsystem together",
                isSelected = defaultGroupSubsystems
              ),
              makeCheckbox("useSplines", "Use splines for edges?", isSelected = defaultUseSplines),
              hr,
              p(),
              h5(s"Dot layout engine:"),
              IcdVizOptions.graphLayouts
                .map(x => makeRadioButton("graphLayout", x, IcdVizOptions.defaultLayout, None)),
              hr,
              p(),
              h5(s"Node overlap handling:"),
              IcdVizOptions.overlapValues
                .map(x => makeRadioButton("graphOverlap", x, IcdVizOptions.defaultOverlap, None)),
              hr,
              p(),
              h5(s"Component types to omit as primaries:"),
              IcdVizOptions.allowedOmitTypes
                .map(x => makeRadioButton("graphOmitType", x, IcdVizOptions.defaultOmit, None)),
              hr,
              p(),
              h5(s"Image format:"),
              IcdVizOptions.imageFormats
                .map(x => makeRadioButton("imageFormat", x, IcdVizOptions.defaultImageFormat, None)),
              //
              hr,
              p()
            )
          ),
          div(cls := "modal-footer")(
            button(`type` := "button", cls := "btn btn-default", attr("data-dismiss") := "modal")("Cancel"),
            button(
              onclick := graphModalListener _,
              `type` := "button",
              cls := "btn btn-primary",
              attr("data-dismiss") := "modal"
            )("Apply")
          )
        )
      )
    )
  }

  private val item = {
    import scalatags.JsDom.all._
    li(
      makeGraphModal(),
      a(
        href := "#",
        title := tip,
        attr("data-toggle") := "modal",
        attr("data-target") := s"#graphModal"
      )(labelStr)
    ).render
  }

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
