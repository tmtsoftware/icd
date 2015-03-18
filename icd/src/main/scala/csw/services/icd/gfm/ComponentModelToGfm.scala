package csw.services.icd.gfm

import csw.services.icd.model.ComponentModel

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class ComponentModelToGfm(m: ComponentModel, level: Level) extends Gfm {

  import Gfm._

  private val head = mkHeading(level, 2, s"Component: ${m.name}")

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Component Type", "Prefix", "Uses Time", "Uses Events", "Uses Configurations", "Uses Properties"),
    List(List(m.componentType, m.prefix, m.usesTime.toString, m.usesEvents.toString, m.usesConfigurations.toString, m.usesProperties.toString)))

  val gfm = s"$head\n$desc\n$table"
}
