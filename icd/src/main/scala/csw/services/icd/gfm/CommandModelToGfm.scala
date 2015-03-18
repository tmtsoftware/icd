package csw.services.icd.gfm

import csw.services.icd.model.{ CommandItemModel, CommandModel }
import Gfm._

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class CommandModelToGfm(m: CommandModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 2, "Commands")

  private val desc = mkParagraph(m.description)

  private val body = m.items.zipWithIndex.map {
    case (t, i) ⇒ CommandItemToGfm(t, level.inc3(i)).gfm
  }.mkString("\n")

  val gfm = s"$head\n$desc\n$body"
}

private case class CommandItemToGfm(m: CommandItemModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 3, s"Configuration: ${m.name}")

  private val desc = mkParagraph(m.description)

  private val requirements = mkParagraph(bold("Requirements:") + " " + m.requirements.mkString(", "))

  private val argsHead = mkParagraph(bold("Arguments:"))

  private val argsTable = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    m.args.map(a ⇒ List(a.name, a.description, a.typeStr, a.defaultValue, a.units)))

  val gfm = List(head, requirements, desc, argsHead, argsTable).mkString("\n")
}
