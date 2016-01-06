package csw.services.icd.html

import csw.services.icd.model.HealthModel

/**
 * Converts a list of Health descriptions to a HTML formatted string
 */
case class HealthListToHtml(list: List[HealthModel]) extends HtmlMarkup {

  import HtmlMarkup._

  private val name = "Health"
  private val head = mkHeading(3, name)

  private val table = mkTable(
    List("Name", "Description", "Rate", "Archive", "Archive Rate", "Max Rate", "Value Type"),
    list.map(m ⇒ List(m.name, m.description, m.rate.toString, m.archive.toString,
      m.archiveRate.toString, m.maxRate.toString, m.valueType.typeStr)))

  override val tags = List(head, table)

  override val tocEntry = Some(mkTocEntry(name))
}
