package csw.services.icd.html

import icd.web.shared.IcdModels.SubsystemModel

/**
 * Converts a SubsystemModel instance to a HTML formatted string
 */
case class SubsystemModelToHtml(m: SubsystemModel) extends HtmlMarkup {

  import HtmlMarkup._

  private val head = mkHeading(1, m.title)

  private val desc = mkParagraph(m.description)

  override val tags = List(head, desc)

  override val tocEntry = Some(mkTocEntry(m.title))
}
