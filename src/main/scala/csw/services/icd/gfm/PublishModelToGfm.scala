package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a PublishModel instance to a GFM formatted string
 */
case class PublishModelToGfm(m: PublishModel, level: Level) {

  private val head = s"##${level(1)} Publish\n"
  private val desc = s"\n${m.description}\n"
  private implicit val counter = (0 to 5).iterator
  private val parts = List(
    TelemetryListToGfm(m.telemetryList, level.inc2()).gfm,
    EventListToGfm(m.eventList, level.inc2()).gfm,
    TelemetryListToGfm(m.eventStreamList, level.inc2(), "Event Streams").gfm,
    AlarmListToGfm(m.alarmList, level.inc2()).gfm,
    HealthListToGfm(m.healthList, level.inc2()).gfm)

  val gfm = head + desc + parts.mkString("\n\n")
}
