package csw.services.icd.html

import csw.services.icd.html.HtmlMarkup._
import csw.services.icd.model._

/**
 * Converts a TelemetryModel instance to a HTML formatted string
 */
case class TelemetryListToHtml(list: List[TelemetryModel], pubType: String, compName: String) extends HtmlMarkup {
  private val titleStr = s"$pubType Published by $compName"
  private val head = mkHeading(3, titleStr)

  private val body = {
    import scalatags.Text.all._
    div(this.list.map(TelemetryModelToHTML(_, pubType).markup))
  }

  override val tags = if (list.nonEmpty) List(head, body) else {
    import scalatags.Text.all._
    List(div())
  }

  override val tocEntry = if (list.nonEmpty) Some(mkTocEntry(titleStr)) else None
}

private case class TelemetryModelToHTML(m: TelemetryModel, pubType: String) extends HtmlMarkup {

  private val name = s"$pubType: ${m.name}"
  private val head = mkHeading(4, name)

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Min Rate", "Max Rate", "Archive", "Archive Rate"),
    List(List(formatRate(m.minRate), formatRate(m.maxRate), if (m.archive) "yes" else "no",
      formatRate(m.archiveRate))))

  private val attr = JsonSchemaListToHtml(Some(s"Attributes for ${m.name}"), m.attributesList)

  override val tags = List(head, desc, table, attr.markup)

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(li(a(href := s"#$idStr")(this.name), ul(attr.tocEntry))))
  }
}
