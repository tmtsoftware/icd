package csw.services.icd.html

import icd.web.shared.IcdModels.ComponentModel

/**
 * Converts a ComponentModel instance to a HTML formatted string
 */
case class ComponentModelToHtml(m: ComponentModel) extends HtmlMarkup {

  import HtmlMarkup._

  private val head = mkHeading(2, m.title)

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Subsyatem", "Name", "Prefix", "Type", "WBS ID"),
    List(List(m.subsystem, m.component, m.prefix, m.componentType, m.wbsId))
  )

  override val tags = List(head, desc, table)

  override val tocEntry = Some(mkTocEntry(m.title))
}
