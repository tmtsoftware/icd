package csw.services.icd.gfm

import csw.services.icd.model.SubsystemModel

/**
 * Converts an IcdModel instance to a GFM formatted string
 */
case class IcdModelToGfm(m: SubsystemModel, level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 1, m.title)

  private val table = mkTable(
    List("Version"),
    List(List(m.version.toString)))

  private val desc = mkParagraph(m.description)

  val gfm = s"$head\n$desc\n$table"
}
