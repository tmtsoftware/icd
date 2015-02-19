package csw.services.icd.gfm

import csw.services.icd.model._

/**
 * Converts a TelemetryModel instance to a GFM formatted string
 */
case class TelemetryModelToGfm(telemetryList: List[TelemetryModel]) {

  private def telemetryItemToGfm(m: TelemetryModel): String = {
    s"${m.name}|${m.description}|${m.rate}|${m.maxRate}|${m.archive}|${m.archiveRate}"
  }

  private val head =
    s"""
       |##1 Publish
       |
       |###1.1 Telemetry
       |
       |Name|Description|Rate|Max Rate|Archive|Archive Rate
       |---|---|---|---|---|---
       |""".stripMargin

  private val mainTable = telemetryList.map(telemetryItemToGfm).mkString("\n")

  private var level = 0

  private def attributeHead(name: String) = {
    level = level + 1
    s"""
       |
       |####1.1.$level Attributes for $name
        |
        |Name|Description|Type|Default|Units
        |---|---|---|---|---
        | """.stripMargin
  }

  private def attributeToGfm(m: JsonSchemaModel): String =
    s"${m.name}|${m.description}|${m.typeStr}|${m.defaultValue}|${m.units}"

  private def attributesToGfm(m: TelemetryModel): String =
    attributeHead(m.name) + m.attributesList.map(attributeToGfm).mkString("\n")

  private val attributes = telemetryList.map(attributesToGfm).mkString("\n")

  val gfm = s"$head$mainTable$attributes"
}
