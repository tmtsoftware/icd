package csw.services.icd.gfm

import csw.services.icd.model._

private case class SubscribeInfoToGfm(list: List[SubscribeInfo], level: Level, title: String) {

  private def itemToGfm(m: SubscribeInfo): String = {
    s"${m.subsystem}|${m.name}|${m.requiredRate}|${m.maxRate}"
  }

  private val head =
    s"""
       |###${level(2)} $title
        |
        |Subsystem|Name|Required Rate|Max Rate
        |---|---|---|---
        |""".stripMargin

  private val table = list.map(itemToGfm).mkString("\n")

  val gfm = s"$head$table"
}

/**
 * Converts a SubscribeModel instance to a GFM formatted string
 */
case class SubscribeModelToGfm(m: SubscribeModel, level: Level) {
  private val head = s"##${level(1)} Subscribe\n"
  private val counter = (0 to 5).iterator
  private val parts = List(
    SubscribeInfoToGfm(m.telemetryList, level.inc2(counter.next()), "Telemetry").gfm,
    SubscribeInfoToGfm(m.eventList, level.inc2(counter.next()), "Events").gfm,
    SubscribeInfoToGfm(m.eventStreamList, level.inc2(counter.next()), "Event Streams").gfm,
    SubscribeInfoToGfm(m.alarmList, level.inc2(counter.next()), "Alarms").gfm,
    SubscribeInfoToGfm(m.healthList, level.inc2(counter.next()), "Health").gfm
  )

  val gfm = head + parts.mkString("\n\n")
}
