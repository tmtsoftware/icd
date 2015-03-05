package csw.services.icd.gfm

import csw.services.icd.model._
import Gfm._

private case class SubscribeInfoToGfm(list: List[SubscribeInfo], level: Level, title: String) extends Gfm {

  private val head = mkHeading(level, 3, title)

  private val table = mkTable(
    List("Subsystem", "Name", "Required Rate", "Max Rate"),
    list.map(m ⇒ List(m.subsystem, m.name, m.requiredRate.toString, m.maxRate.toString)))

  val gfm = s"$head\n$table"
}

/**
 * Converts a SubscribeModel instance to a GFM formatted string
 */
case class SubscribeModelToGfm(m: SubscribeModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 2, "Subscribe")

  private val desc = mkParagraph(m.description)

  private implicit val counter = (0 to 5).iterator

  private val parts = List(
    SubscribeInfoToGfm(m.telemetryList, level.inc3(), "Telemetry").gfm,
    SubscribeInfoToGfm(m.eventList, level.inc3(), "Events").gfm,
    SubscribeInfoToGfm(m.eventStreamList, level.inc3(), "Event Streams").gfm,
    SubscribeInfoToGfm(m.alarmList, level.inc3(), "Alarms").gfm,
    SubscribeInfoToGfm(m.healthList, level.inc3(), "Health").gfm)

  val gfm = head + desc + parts.mkString("\n\n")
}
