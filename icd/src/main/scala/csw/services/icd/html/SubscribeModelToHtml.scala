package csw.services.icd.html

import csw.services.icd.html.HtmlMarkup._
import csw.services.icd.model._

private case class SubscribeInfoToHtml(list: List[SubscribeInfo], title: String) extends HtmlMarkup {

  private val head = mkHeading(3, title)

  private val table = mkTable(
    List("Subsystem", "Component", "Name", "Required Rate", "Max Rate", "Usage"),
    list.map(m ⇒ List(m.subsystem, m.component, m.name, m.requiredRate.toString, m.maxRate.toString, gfmToHtml(m.usage)))
  )

  override val tags = if (list.nonEmpty) List(head, table) else {
    import scalatags.Text.all._
    List(div())
  }

  override val tocEntry = if (list.nonEmpty) Some(mkTocEntry(title)) else None
}

/**
 * Converts a SubscribeModel instance to a HTML formatted string
 */
case class SubscribeModelToHtml(m: SubscribeModel) extends HtmlMarkup {
  private val name = s"Items Subscribed to by ${m.component}"
  private val head = mkHeading(2, name)

  private val desc = mkParagraph(m.description)

  private implicit val counter = (0 to 5).iterator

  private val parts = List(
    SubscribeInfoToHtml(m.telemetryList, s"Telemetry Subscribed to by ${m.component}"),
    SubscribeInfoToHtml(m.eventList, s"Events Subscribed to by ${m.component}"),
    SubscribeInfoToHtml(m.eventStreamList, s"Event Streams Subscribed to by ${m.component}"),
    SubscribeInfoToHtml(m.alarmList, s"Alarms Subscribed to by ${m.component}")
  )

  override val tags = List(head, desc) ::: parts.map(_.markup)

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(li(a(href := s"#$idStr")(this.name), ul(parts.flatMap(_.tocEntry)))))
  }
}
