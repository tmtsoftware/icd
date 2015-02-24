package csw.services.icd.gfm

import csw.services.icd.model.AlarmModel

/**
 * Converts a list of alarm descriptions to a GFM formatted string
 */
case class AlarmListToGfm(list: List[AlarmModel], level: Level) {

  private def itemToGfm(m: AlarmModel): String = {
    s"${m.name} | ${m.description} | ${m.severity} | ${m.archive} |"
  }

  private val head =
    s"""
       |###${level(2)} Alarms
                        |
                        |Name|Description|Severity|Archive
                        |---|---|---|---
                        |""".stripMargin

  private val table = list.map(itemToGfm).mkString("\n")

  val gfm = s"$head$table"
}
