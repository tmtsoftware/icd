package csw.services.icd.html

import csw.services.icd.model.JsonSchemaModel

/**
 * Converts a JsonSchemaModel instance to a GFM formatted string
 */
case class JsonSchemaListToHtml(titleOpt: Option[String], list: List[JsonSchemaModel]) extends HtmlMarkup {

  import HtmlMarkup._

  private val headOpt = titleOpt.map(mkHeading(4, _))

  val table = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    list.map(m ⇒ List(m.name, gfmToHtml(m.description), m.typeStr, m.defaultValue, m.units))
  )

  override val tags = headOpt.toList ::: List(table)

  override val tocEntry = titleOpt.map(mkTocEntry)
}
