package csw.services.icd.gfm

import csw.services.icd.model.{ CommandItemModel, CommandModel }

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class CommandModelToGfm(m: CommandModel, level: Level) extends Gfm {
  private val head = s"##${level(1)} Commands\n"

  private val body = m.items.zipWithIndex.map {
    case (t, i) ⇒ CommandItemToGfm(t, level.inc2(i)).gfm
  }.mkString("\n\n")

  val gfm = s"$head$body"
}

case class CommandItemToGfm(m: CommandItemModel, level: Level) extends Gfm {
  private val head = s"\n##${level(2)} Configuration: *${m.name}*\n"

  private val requirementsHead = s"\n###${level(3)} *${m.name}* Requirements\n"

  private val requirements = (for (r ← m.requirements) yield s"* $r").mkString("\n")

  private val argsHead = s"\n###${level(3)} *${m.name}* Arguments\n"

  private val argsTableHead =
    s"""
       |Name|Description|Type|Default|Units
       |---|---|---|---|---\n""".stripMargin

  private val argsTableBody = (
    for (a ← m.args) yield s"${a.name} | ${t(a.description)} | ${a.typeStr} | ${a.defaultValue} | ${a.units} |").mkString("\n")

  private val argsTable = s"$argsTableHead$argsTableBody"

  val gfm = List(head, requirementsHead, requirements, argsHead, argsTable).mkString("\n")
}
