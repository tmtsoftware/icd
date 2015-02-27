package csw.services.icd.gfm

import csw.services.icd.model.AlarmModel

/**
 * Converts a list of alarm descriptions to a GFM formatted string
 */
case class AlarmListToGfm(list: List[AlarmModel], level: Level) extends Gfm {

  private val head = mkHeading(level, 2, "Alarms")

  private val table = mkTable(
    List("Name", "Description", "Severity", "Archive"),
    list.map(m ⇒ List(m.name, m.description, m.severity, m.archive.toString)))

  val gfm = s"$head\n$table"
}
