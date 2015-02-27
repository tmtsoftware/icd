package csw.services.icd.gfm

import csw.services.icd.model._
import Gfm._

private case class SubscribeInfoToGfm(list: List[SubscribeInfo], level: Level, title: String) extends Gfm {

  private val head = mkHeading(level, 2, title)

  private val table = mkTable(
    List("Subsystem", "Name", "Required Rate", "Max Rate"),
    list.map(m ⇒ List(m.subsystem, m.name, m.requiredRate.toString, m.maxRate.toString)))

  val gfm = s"$head\n$table"
}

/**
 * Converts a SubscribeModel instance to a GFM formatted string
 */
case class SubscribeModelToGfm(m: SubscribeModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 1, "Subscribe")

  private implicit val counter = (0 to 5).iterator

  private val parts = List(
    SubscribeInfoToGfm(m.telemetryList, level.inc2(), "Telemetry").gfm,
    SubscribeInfoToGfm(m.eventList, level.inc2(), "Events").gfm,
    SubscribeInfoToGfm(m.eventStreamList, level.inc2(), "Event Streams").gfm,
    SubscribeInfoToGfm(m.alarmList, level.inc2(), "Alarms").gfm,
    SubscribeInfoToGfm(m.healthList, level.inc2(), "Health").gfm)

  val gfm = head + parts.mkString("\n\n")
}
