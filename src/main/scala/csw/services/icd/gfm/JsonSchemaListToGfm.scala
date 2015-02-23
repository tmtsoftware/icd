package csw.services.icd.gfm

import csw.services.icd.model.JsonSchemaModel

/**
 * Converts a JsonSchemaModel instance to a GFM formatted string
 */
case class JsonSchemaListToGfm(list: List[JsonSchemaModel]) {
  private val head = s"""
        |Name|Description|Type|Default|Units
        |---|---|---|---|---
        | """.stripMargin

  private def toGfm(m: JsonSchemaModel): String =
    s"${m.name}|${m.description}|${m.typeStr}|${m.defaultValue}|${m.units}"

  val gfm = head + list.map(toGfm).mkString("\n")
}
