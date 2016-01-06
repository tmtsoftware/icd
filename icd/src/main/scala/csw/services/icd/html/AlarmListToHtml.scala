package csw.services.icd.html

import java.util.UUID

import csw.services.icd.model.AlarmModel

import scalatags.Text.TypedTag

/**
 * Converts a list of alarm descriptions to HTML
 */
case class AlarmListToHtml(list: List[AlarmModel]) extends HtmlMarkup {

  import HtmlMarkup._

  private val name = "Alarms"
  private val head = mkHeading(3, name)

  private val table = mkTable(
    List("Name", "Description", "Severity", "Archive"),
    list.map(m ⇒ List(m.name, m.description, m.severity, m.archive.toString)))

  override val tags = List(head, table)

  override val tocEntry = Some(mkTocEntry(name))
}
