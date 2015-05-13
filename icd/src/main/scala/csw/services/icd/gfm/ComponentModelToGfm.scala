package csw.services.icd.gfm

import csw.services.icd.model.ComponentModel

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class ComponentModelToGfm(m: ComponentModel, level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 2, s"${m.componentType}: ${m.title}")

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Subsyatem", "Name", "Prefix", "Version", "WBS ID"),
    List(List(m.subsystem, m.name, m.prefix, m.version.toString, m.wbsId)))

  val gfm = s"$head\n$desc\n$table"
}
