package csw.services.icd.html

import csw.services.icd.html.HtmlMarkup._
import csw.services.icd.model._

/**
 * Converts a TelemetryModel instance (or EventStreamModel, which is the same) to a HTML formatted string
 */
case class TelemetryListToHtml(list: List[TelemetryModel], title: String = "Telemetry") extends HtmlMarkup {
  private val head = mkHeading(3, title)

  private val body = {
    import scalatags.Text.all._
    div(this.list.map(TelemetryModelToHTML(_, this.title).markup))
  }

  override val tags = List(head, body)

  override val tocEntry = Some(mkTocEntry(title))
}

private case class TelemetryModelToHTML(m: TelemetryModel, title: String) extends HtmlMarkup {

  private val name = s"$title: ${m.name}"
  private val head = mkHeading(4, name)

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Name", "Value"),
    List(
      List("Min Rate", m.minRate.toString + " Hz"),
      List("Max Rate", m.maxRate.toString + " Hz"),
      List("Archive", m.archive.toString),
      List("Archive Rate", m.archiveRate.toString + " Hz")).filter(_(1) != "0 Hz"))

  private val attr = JsonSchemaListToHtml(Some(s"Attributes for ${m.name}"), m.attributesList)

  override val tags = List(head, desc, table, attr.markup)

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(li(a(href := s"#$idStr")(this.name), ul(attr.tocEntry))))
  }
}
