package csw.services.icd.gfm

import csw.services.icd.model.HealthModel

/**
 * Converts a list of Health descriptions to a GFM formatted string
 */
case class HealthListToGfm(list: List[HealthModel], level: Level) {

  private def itemToGfm(m: HealthModel): String = {
    s"${m.name} | ${m.description} | ${m.rate} | ${m.archive} | ${m.archiveRate} | ${m.maxRate} | ${m.valueType.typeStr} |"
  }

  private val head =
    s"""
       |###${level(2)} Health
                        |
                        |Name|Description|Rate|Archive|Archive Rate|Max Rate|Value Type
                        |---|---|---|---|---|---|---
                        |""".stripMargin

  private val table = list.map(itemToGfm).mkString("\n")

  val gfm = s"$head$table"
}
