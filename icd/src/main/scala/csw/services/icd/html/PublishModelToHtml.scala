package csw.services.icd.html

import csw.services.icd.model._

/**
 * Converts a PublishModel instance to a HTML formatted string
 */
case class PublishModelToHtml(m: PublishModel) extends HtmlMarkup {

  import HtmlMarkup._

  private val name = "Publish"
  private val head = mkHeading(2, name)

  private val desc = mkParagraph(m.description)

  private val parts: List[HtmlMarkup] = List(
    TelemetryListToHtml(m.telemetryList),
    EventListToHtml(m.eventList),
    TelemetryListToHtml(m.eventStreamList, "Event Streams"),
    AlarmListToHtml(m.alarmList),
    HealthListToHtml(m.healthList))

  override val tags = List(head, desc) ::: parts.map(_.markup)

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(li(a(href := s"#$idStr")(this.name), ul(parts.flatMap(_.tocEntry)))))
  }
}
