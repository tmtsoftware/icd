package csw.services.icd.gfm

import csw.services.icd.model.IcdModel

/**
 * Converts an IcdModel instance to a GFM formatted string
 */
case class IcdModelToGfm(m: IcdModel) extends Gfm {

  import Gfm._

  private val head = mkHeading(0, s"Interface Control Document\n${m.name}")

  private val table = mkTable(
    List("Version", "Subsystem", "WBS Id"),
    List(List(m.version.toString, m.subsystem, m.wbsId)))

  private val desc = mkParagraph(m.description)

  val gfm = s"$head\n$desc\n$table"
}
