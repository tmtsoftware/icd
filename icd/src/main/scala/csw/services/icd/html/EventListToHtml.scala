package csw.services.icd.html

import csw.services.icd.model.JsonSchemaModel

/**
 * Converts a list of event descriptions to a HTML formatted string
 */
case class EventListToHtml(list: List[JsonSchemaModel]) extends HtmlMarkup {

  private val name = "Events"

  private val head = mkHeading(3, name)

  private val table = JsonSchemaListToHtml(None, list).markup

  override val tags = List(head, table)

  override val tocEntry = Some(mkTocEntry(name))
}
