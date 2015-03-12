package csw.services.icd.gfm

import csw.services.icd.model._
import Gfm._

/**
 * Converts a TelemetryModel instance (or EventStreamModel, which is the same) to a GFM formatted string
 */
case class TelemetryListToGfm(list: List[TelemetryModel], level: Level, title: String = "Telemetry") extends Gfm {
  private val head = mkHeading(level, 3, title)

  private val body =
    if (list.isEmpty) "n/a"
    else
      list.zipWithIndex.map {
        case (m, i) ⇒ TelemetryModelToGfm(m, level.inc4(i), title).gfm
      }.mkString("\n")

  val gfm = s"$head\n$body"
}

private case class TelemetryModelToGfm(m: TelemetryModel, level: Level, title: String) extends Gfm {

  private val head = mkHeading(level, 4, s"$title: ${m.name}")

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Name", "Value"),
    List(
      List("Rate", m.rate.toString + "Hz"),
      List("Max Rate", m.maxRate.toString + "Hz"),
      List("Archive", m.archive.toString),
      List("Archive Rate", m.archiveRate.toString + "Hz")).filter(_(1) != "0Hz"))

  private val attrHead = mkHeading(level, 4, s"Attributes for ${m.name}")

  private val attr = JsonSchemaListToGfm(m.attributesList).gfm

  val gfm = s"$head\n$desc\n$table\n$attrHead\n$attr"
}