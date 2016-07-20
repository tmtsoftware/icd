package csw.services.icd.html

import csw.services.icd.model.AttributeModelParser
import icd.web.shared.IcdModels.AttributeModel

/**
 * Converts a AttributeModel instance to HTML
 */
case class AttributeModelListToHtml(titleOpt: Option[String], list: List[AttributeModel]) extends HtmlMarkup {

  import HtmlMarkup._

  private val headOpt = titleOpt.map(mkHeading(4, _))

  val table = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    list.map(m => List(m.name, m.description, m.typeStr, m.defaultValue, m.units))
  )

  override val tags = headOpt.toList ::: List(table)

  override val tocEntry = titleOpt.map(mkTocEntry)
}
