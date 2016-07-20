package csw.services.icd.html

import java.util.UUID

import icd.web.shared.IcdModels.AlarmModel

import scalatags.Text.TypedTag

/**
 * Converts a list of alarm descriptions to HTML
 */
case class AlarmListToHtml(list: List[AlarmModel], compName: String) extends HtmlMarkup {

  import HtmlMarkup._

  private val titleStr = s"Alarms published by $compName"
  private val head = mkHeading(3, titleStr)

  private val table = mkTable(
    List("Name", "Description", "Severity", "Archive"),
    list.map(m => List(m.name, m.description, m.severity, m.archive.toString))
  )

  override val tags = if (list.nonEmpty) List(head, table) else {
    import scalatags.Text.all._
    List(div())
  }

  override val tocEntry = if (list.nonEmpty) Some(mkTocEntry(titleStr)) else None
}
