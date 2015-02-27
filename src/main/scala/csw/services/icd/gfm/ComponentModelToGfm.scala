package csw.services.icd.gfm

import csw.services.icd.model.ComponentModel

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class ComponentModelToGfm(m: ComponentModel) extends Gfm {

  private val head = mkHeading(1, s"Component: ${m.name}")

  private val desc = mkParagraph(m.description)

  private val table = mkTable(
    List("Component Type", "Uses Time", "Uses Events", "Uses Configurations", "Uses Properties"),
    List(List(m.componentType, m.usesTime.toString, m.usesEvents.toString, m.usesConfigurations.toString, m.usesProperties.toString)))

  val gfm = s"$head\n$desc\n$table"
}
