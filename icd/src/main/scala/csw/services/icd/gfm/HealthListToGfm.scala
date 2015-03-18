package csw.services.icd.gfm

import csw.services.icd.model.HealthModel

/**
 * Converts a list of Health descriptions to a GFM formatted string
 */
case class HealthListToGfm(list: List[HealthModel], level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 3, "Health")

  private val table = mkTable(
    List("Name", "Description", "Rate", "Archive", "Archive Rate", "Max Rate", "Value Type"),
    list.map(m ⇒ List(m.name, m.description, m.rate.toString, m.archive.toString,
      m.archiveRate.toString, m.maxRate.toString, m.valueType.typeStr)))

  val gfm = s"$head\n$table"
}
