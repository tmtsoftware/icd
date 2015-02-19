package csw.services.icd.gfm

import csw.services.icd.model.{TelemetryModel, PublishModel}

/**
 * Converts a PublishModel instance to a GFM formatted string
 */
case class PublishModelToGfm(m: PublishModel) {

  private val parts = List(
    TelemetryModelToGfm(m.telemetryList).gfm
  )

  val gfm = parts.mkString("\n\n")

//  val gfm =
//    s"""
//       |##1 Publish
//       |
//       |###1.1 Telemetry
//       |
//       |Name|Description|Rate|Max Rate|Archive|Archive Rate
//       |---|---|---|---|---
//       | $telemetry
//        |
//        |####1.1.1 Attributes for status1
//        |
//        |Name|Description|Type|Default|Units
//        |---|---|---|---|---
//        |a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
//        |a2|array of float|array of numbers (length = 5)||mm
//        |a3|enum choice|String: ("red", "green", "blue")|"blue"
//        |
//        |####1.1.2 Attributes for status2
//        |
//        |Name|Description|Type|Default|Units
//        |---|---|---|---|---
//        |a4|single value with min/max|integer (-2 ≤ i ≤ 22)|10|m
//        |
//        |
//        |###1.2 Events
//        |
//        |Name|Description|Type|Default|Units
//        |---|---|---|---|---
//        |a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
//        |a2|array of float|array of numbers (length = 5)||mm
//        |a3|enum choice|String: ("red", "green", "blue")|"green"
//        |
//        |
//        |
//        |###1.3 Event Streams
//        |
//        |Name|Rate|Max Rate|Archive|Archive Rate
//        |---|---|---|---|---|---|---
//        |status1|0|100|Yes|10
//        |status2|10|10|No|
//        |
//        |####1.3.1 Attributes for status1
//        |
//        |Name|Description|Type|Default|Units
//        |---|---|---|---|---
//        |a1|single value with min/max|integer (-2 ≤ i ≤ 22)||m
//        |a2|array of float|array of numbers (length = 5)||mm
//        |a3|enum choice|String: ("red", "green", "blue")|"red"
//        |
//        |####1.3.2 Attributes for status2
//        |
//        |Name|Description|Type|Default|Units
//        |---|---|---|---|---
//        |a4|single value with min/max|integer (-2 ≤ i ≤ 22)|3|m
//        |
//        |
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
