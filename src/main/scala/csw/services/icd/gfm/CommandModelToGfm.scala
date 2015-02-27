package csw.services.icd.gfm

import csw.services.icd.model.{ CommandItemModel, CommandModel }
import Gfm._

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class CommandModelToGfm(m: CommandModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 1, "Commands")

  private val body = m.items.zipWithIndex.map {
    case (t, i) ⇒ CommandItemToGfm(t, level.inc2(i)).gfm
  }.mkString("\n")

  val gfm = s"$head$body"
}

private case class CommandItemToGfm(m: CommandItemModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 2, s"Configuration: *${m.name}*")

  private val requirementsHead = mkHeading(level, 3, s"*${m.name}* Requirements")

  private val requirements = mkList(m.requirements)

  private val argsHead = mkHeading(level.inc3(1), 3, s"*${m.name}* Arguments")

  private val argsTable = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    m.args.map(a ⇒ List(a.name, a.description, a.typeStr, a.defaultValue, a.units)))

  val gfm = List(head, requirementsHead, requirements, argsHead, argsTable).mkString("\n")
}
