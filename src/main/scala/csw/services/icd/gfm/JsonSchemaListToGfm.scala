package csw.services.icd.gfm

import csw.services.icd.model.JsonSchemaModel

/**
 * Converts a JsonSchemaModel instance to a GFM formatted string
 */
case class JsonSchemaListToGfm(list: List[JsonSchemaModel]) extends Gfm {
  val gfm = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    list.map(m ⇒ List(m.name, m.description, m.typeStr, m.defaultValue, m.units)))
}
