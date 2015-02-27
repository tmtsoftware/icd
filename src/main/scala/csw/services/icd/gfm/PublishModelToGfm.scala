package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a PublishModel instance to a GFM formatted string
 */
case class PublishModelToGfm(m: PublishModel, level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 1, "Publish")

  private val desc = mkParagraph(m.description)

  private implicit val counter = (0 to 5).iterator

  private val parts = List(
    TelemetryListToGfm(m.telemetryList, level.inc2()).gfm,
    EventListToGfm(m.eventList, level.inc2()).gfm,
    TelemetryListToGfm(m.eventStreamList, level.inc2(), "Event Streams").gfm,
    AlarmListToGfm(m.alarmList, level.inc2()).gfm,
    HealthListToGfm(m.healthList, level.inc2()).gfm)

  val gfm = head + desc + parts.mkString("\n\n")
}
