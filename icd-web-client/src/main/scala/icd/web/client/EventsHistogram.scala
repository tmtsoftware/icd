package icd.web.client

import icd.web.shared.{EventsHistogramData, EventsHistogramOptions, SubsystemWithVersion}
import org.scalajs.dom.{Element, HTMLInputElement, document}
import org.scalajs.dom.html.{Button, Div}
import scalatags.JsDom

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.json.*

object EventsHistogram {

  /**
   * A navbar item that generates and display a histogram of all events based on event size
   * and/or data rate, for the selected subsystems/components (or all subsystems).
   *
   * @param labelStr the label to display
   * @param tip      the tool tip to display when hovering over the item
   * @param listener called when the item is clicked
   */
  case class ButtonItem(labelStr: String, tip: String, listener: () => Unit) extends Displayable {
    private val item = {
      import scalatags.JsDom.all.*
      div(
        button(
          `type`  := "button",
          cls     := "btn btn-secondary",
          title   := tip,
          onclick := (() => listener())
        )(labelStr),
        raw("&nbsp;"),
        raw("&nbsp;"),
        div(
          `class` := "form-check form-check-inline",
          input(
            `type` := "radio",
            id     := EventsHistogramOptions.eventSize,
            cls    := "form-check-input",
            name   := "eventsHistogramType",
            value  := EventsHistogramOptions.eventSize,
            checked
          ),
          label(cls := "form-check-label", "Event Sizes")
        ),
        div(
          `class` := "form-check form-check-inline",
          input(
            `type` := "radio",
            id     := EventsHistogramOptions.dataRate,
            cls    := "form-check-input",
            name   := "eventsHistogramType",
            value  := EventsHistogramOptions.dataRate
          ),
          label(cls := "form-check-label", "Data Rates")
        ),
        div(cls := "form-check")(
          input(
            id  := "swapAxis",
            name  := "swapAxis",
            cls   := "form-check-input",
            title := s"Swap the X and Y axis in the chart",
            tpe   := "checkbox"
          ),
          label(cls := "form-check-label", "Swap X and Y axis")
        )
      ).render
    }

    override def setEnabled(enabled: Boolean): Unit = {
      if (enabled)
        item.classList.remove("disabled")
      else
        item.classList.add("disabled")
    }

    override def markup(): Element = {
      import scalatags.JsDom.all.*

      span(cls := "selectDialogButton", item).render
    }
  }

  private def getHistogramData(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      eventsHistogramOptions: EventsHistogramOptions
  ): Future[EventsHistogramData] = {
    import icd.web.shared.JsonSupport.*
    Fetch
      .get(ClientRoutes.eventsHistogram(sv, maybeTargetSv, eventsHistogramOptions))
      .map { jsonStr =>
        Json.fromJson[EventsHistogramData](Json.parse(jsonStr)).get
      }
  }

  def makeEventsHistogram(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Unit = {
    val plotEventSizes = document.getElementById(EventsHistogramOptions.eventSize).asInstanceOf[HTMLInputElement].checked
    val swapAxis       = document.getElementById("swapAxis").asInstanceOf[HTMLInputElement].checked
    val eventsHistogramOptions =
      EventsHistogramOptions(if (plotEventSizes) EventsHistogramOptions.eventSize else EventsHistogramOptions.dataRate, swapAxis)

    getHistogramData(sv, maybeTargetSv, eventsHistogramOptions).foreach(makeEventsHistogram(_, eventsHistogramOptions))
  }

  def makeAllEventsHistogram(): Unit = {}

  def makeEventsHistogram(histogramData: EventsHistogramData, eventsHistogramOptions: EventsHistogramOptions): Unit = {
    import plotly.*, element.*, layout.*, Plotly.*
    import eventsHistogramOptions.*

    val axis1 = Axis().withTitle("Number of Events")
    val axis2 =
      if (histogramType == EventsHistogramOptions.eventSize) Axis().withTitle("Event Size") else Axis().withTitle("Data Rate")
    val xAxis = if (swapAxis) axis2 else axis1
    val yAxis = if (swapAxis) axis1 else axis2

    val lay = Layout()
      .withTitle("Event Sizes")
      .withShowlegend(true)
      .withHeight(600)
      .withWidth(900)
      .withBargap(0.05)
      .withXaxis(xAxis)
      .withYaxis(yAxis)

    val data = Seq(
      if (swapAxis)
        Bar(
          histogramData.yData,
          histogramData.xData
        )
      else
        Bar(
          histogramData.xData,
          histogramData.yData
        )
    )

    Plotly.plot("eventsHistogram", data, lay) // attaches to div element with id 'eventsHistogram'
  }
}
