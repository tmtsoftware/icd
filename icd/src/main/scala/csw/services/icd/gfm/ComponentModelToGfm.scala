package csw.services.icd.gfm

import csw.services.icd.model.ComponentModel

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class ComponentModelToGfm(m: ComponentModel, level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 2, m.title)

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Subsyatem", "Name", "Prefix", "Type", "WBS ID"),
    List(List(m.subsystem, m.component, m.prefix, m.componentType, m.wbsId)))

  val gfm = s"$head\n$desc\n$table"
}
