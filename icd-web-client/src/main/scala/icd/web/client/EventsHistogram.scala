package icd.web.client

import icd.web.shared.{EventsHistogramData, SubsystemWithVersion}
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
            id     := "eventSizes",
            cls    := "form-check-input",
            name   := "eventsHistogramType",
            value  := "eventSizes",
            checked
          ),
          label(cls := "form-check-label", "Event Sizes")
        ),
        div(
          `class` := "form-check form-check-inline",
          input(
            `type` := "radio",
            id     := "maxRates",
            cls    := "form-check-input",
            name   := "eventsHistogramType",
            value  := "maxRates"
          ),
          label(cls := "form-check-label", "Max Rates")
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
      maybeTargetSv: Option[SubsystemWithVersion]
  ): Future[EventsHistogramData] = {
    import icd.web.shared.JsonSupport.*
    Fetch
      .get(ClientRoutes.eventsHistogram(sv, maybeTargetSv))
      .map { jsonStr =>
        Json.fromJson[EventsHistogramData](Json.parse(jsonStr)).get
      }
  }

  def makeEventsHistogram(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Unit = {
    getHistogramData(sv, maybeTargetSv).foreach(makeEventsHistogram)
  }

  def makeAllEventsHistogram(): Unit = {}

  def makeEventsHistogram(histogramData: EventsHistogramData): Unit = {
    import plotly.*, element.*, layout.*, Plotly.*

    val plotEventSizes = document.getElementById("eventSizes").asInstanceOf[HTMLInputElement].checked

    val lay = Layout()
      .withTitle("Event Sizes")
      .withShowlegend(true)
      .withHeight(600)
      .withWidth(900)
      .withBargap(0.05)
      .withXaxis(Axis().withTitle("Number of Events"))
      .withYaxis(Axis().withTitle("Event Size"))

    val data = Seq(
      Bar(
        histogramData.xData,
        histogramData.yData
      )
    )

    Plotly.plot("eventsHistogram", data, lay) // attaches to div element with id 'eventsHistogram'
  }
}
