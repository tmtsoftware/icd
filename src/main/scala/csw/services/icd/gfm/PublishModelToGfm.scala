package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a PublishModel instance to a GFM formatted string
 */
case class PublishModelToGfm(m: PublishModel, level: Level) {

  private val head = s"##${level(1)} Publish\n"
  private val counter = (0 to 5).iterator
  private val parts = List(
    TelemetryListToGfm(m.telemetryList, level.inc2(counter.next())).gfm,
    EventListToGfm(m.eventList, level.inc2(counter.next())).gfm,
    TelemetryListToGfm(m.eventStreamList, level.inc2(counter.next()), "Event Streams").gfm,
    AlarmListToGfm(m.alarmList, level.inc2(counter.next())).gfm,
    HealthListToGfm(m.healthList, level.inc2(counter.next())).gfm
  )

  val gfm = head + parts.mkString("\n\n")

//  val gfm =
//    s"""
//        |
//        |###1.4 Alarms
//        |
//        |Name|Description|Severity|Archive
//        |---|---|---|---
//        |alarm1|First alarm|minor|Yes
//        |alarm2|Second alarm|major|Yes
//        |
//        |
//        |
//        |###1.5 Health
//        |
//        |Name|Description|Value Type|Default|Rate|Max Rate|Archive|Archive Rate
//        |---|---|---|---|---|---|---|---|---
//        |health1|First health item|string ("good", "ill", "bad", "unknown")|good|0|100|Yes|10
//        |health2|Second health item|string ("good", "ill", "bad", "unknown")|good|1|10|No|1
//        |
//        |
//        |---
//        |
//    """.stripMargin
}
