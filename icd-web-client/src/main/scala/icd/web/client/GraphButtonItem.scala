package icd.web.client

import icd.web.shared.IcdVizOptions
import org.scalajs.dom.{Element, HTMLInputElement, document}
import org.scalajs.dom.html.{Button, Div}
import scalatags.JsDom

/**
 * A navbar item that displays a popup with icd-viz options for displaying a graph of component relationships.
 *
 * @param labelStr the label to display
 * @param tip the tool tip to display when hovering over the item
 * @param listener called when the item is clicked with (orientation, fontSize)
 */
case class GraphButtonItem(labelStr: String, tip: String, listener: IcdVizOptions => Unit) extends Displayable {
  import IcdVizOptions.*

  private def graphModalListener(): Unit = {
    val aspectRatio =
      Option(document.getElementById("aspectRatio").asInstanceOf[HTMLInputElement].valueAsNumber).getOrElse(defaultRatio)
    val plotMissingEvents   = document.getElementById("plotMissingEvents").asInstanceOf[HTMLInputElement].checked
    val plotMissingCommands = document.getElementById("plotMissingCommands").asInstanceOf[HTMLInputElement].checked
    val plotEventLabels     = document.getElementById("plotEventLabels").asInstanceOf[HTMLInputElement].checked
    val plotCommandLabels   = document.getElementById("plotCommandLabels").asInstanceOf[HTMLInputElement].checked
    val groupSubsystems     = document.getElementById("groupSubsystems").asInstanceOf[HTMLInputElement].checked
    val onlySubsystems      = document.getElementById("onlySubsystems").asInstanceOf[HTMLInputElement].checked
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
        groupSubsystems = groupSubsystems && !onlySubsystems,
        onlySubsystems = onlySubsystems,
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
    import scalatags.JsDom.all.*
    div(cls := "modal fade", id := s"graphModal", tabindex := "-1", role := "dialog", style := "padding-top: 130px")(
      div(cls := "modal-dialog")(
        div(cls := "modal-content")(
          div(cls := "modal-header")(
            button(`type` := "button", cls := "close", attr("data-bs-dismiss") := "modal")(raw("&times;")),
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
              makeCheckbox(
                "onlySubsystems",
                "Only display subsystems, not components",
                isSelected = defaultOnlySubsystems
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
            button(`type` := "button", cls := "btn btn-secondary", attr("data-bs-dismiss") := "modal")("Cancel"),
            button(
              onclick := graphModalListener _,
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
      attr("data-bs-target") := s"#graphModal"
    )(labelStr).render
  }

  private val graphModal: JsDom.TypedTag[Div] = makeGraphModal()


  override def setEnabled(enabled: Boolean): Unit = {
    if (enabled)
      item.classList.remove("disabled")
    else
      item.classList.add("disabled")
  }

  override def markup(): Element = {
    import scalatags.JsDom.all.*
    import scalacss.ScalatagsCss.*
    div(Styles.selectDialogButton, cls := "btn-group", item, graphModal).render
  }

}
