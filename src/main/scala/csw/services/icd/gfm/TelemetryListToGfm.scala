package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a TelemetryModel instance (or EventStreamModel, which is the same) to a GFM formatted string
 */
case class TelemetryListToGfm(list: List[TelemetryModel], level: Level, title: String = "Telemetry") {

  private def itemToGfm(m: TelemetryModel): String = {
    s"${m.name}|${m.description}|${m.rate}|${m.maxRate}|${m.archive}|${m.archiveRate}"
  }

  private val head =
    s"""
       |###${level(2)} $title
        |
        |Name|Description|Rate|Max Rate|Archive|Archive Rate
        |---|---|---|---|---|--- |\n""".stripMargin

  private val table = list.map(itemToGfm).mkString("\n")

  private def attributeHead(name: String, level: Level) = {
    s"\n\n####${level(3)} Attributes for $title $name\n"
  }

  private def attributesToGfm(m: TelemetryModel, level: Level): String =
    attributeHead(m.name, level) + JsonSchemaListToGfm(m.attributesList).gfm

  private val attributes = list.zipWithIndex.map {
    case (t, i) ⇒ attributesToGfm(t, level.inc3(i))
  }.mkString("\n")

  val gfm = s"$head$table$attributes"
}
