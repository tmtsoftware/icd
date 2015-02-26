package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a TelemetryModel instance (or EventStreamModel, which is the same) to a GFM formatted string
 */
case class TelemetryListToGfm(list: List[TelemetryModel], level: Level, title: String = "Telemetry") {
  private val head = s"###${level(2)} $title\n"
  private val body = list.zipWithIndex.map {
    case (m, i) ⇒ TelemetryModelToGfm(m, level.inc3(i), title).gfm
  }.mkString("\n")
  val gfm = s"$head\n$body"
}

private case class TelemetryModelToGfm(m: TelemetryModel, level: Level, title: String) {
  val attr = JsonSchemaListToGfm(m.attributesList).gfm
  val gfm = s"""####${level(3)} $title: ${m.name}
      |
      |${m.description}
      |
      |Name|Value
      |---|---
      |Rate | ${m.rate}
      |Max Rate | ${m.maxRate}
      |Archive | ${m.archive}
      |Archive Rate | ${m.archiveRate}
      |
      |#####${level(4)} Attributes for ${m.name}
      |
      |$attr
      |
      |""".stripMargin
}
