package csw.services.icd.gfm

import csw.services.icd.model.{ ReceiveCommandModel, SendCommandModel, CommandModel }
import Gfm._

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class CommandModelToGfm(m: CommandModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 2, "Commands")

  private val desc = mkParagraph(m.description)

  private val receive = m.receive.zipWithIndex.map {
    case (t, i) ⇒ ReceiveCommandModelToGfm(t, level.inc3(i)).gfm
  }.mkString("\n")

  private val send = SendCommandModelToGfm(m.send, level.inc3(m.receive.length)).gfm

  val gfm = s"$head\n$desc\n$receive\n$send"
}

private case class ReceiveCommandModelToGfm(m: ReceiveCommandModel, level: Level) extends Gfm {
  private val head = mkHeading(level, 3, s"Configuration: ${m.name}")

  private val desc = mkParagraph(m.description)

  private val requirements = mkParagraph(bold("Requirements:") + " " + m.requirements.mkString(", "))

  private val argsHead = mkParagraph(bold("Arguments:"))

  private val argsTable = mkTable(
    List("Name", "Description", "Type", "Default", "Units"),
    m.args.map(a ⇒ List(a.name, a.description, a.typeStr, a.defaultValue, a.units)))

  val gfm = List(head, requirements, desc, argsHead, argsTable).mkString("\n")
}

private case class SendCommandModelToGfm(list: List[SendCommandModel], level: Level) extends Gfm {
  private val head = mkHeading(level, 3, "Configurations Sent to Other Components")

  private val table = mkTable(
    List("Name", "Component", "Subsystem"),
    list.map(m ⇒ List(m.name, m.component, m.subsystem)))

  val gfm = List(head, table).mkString("\n")
}

