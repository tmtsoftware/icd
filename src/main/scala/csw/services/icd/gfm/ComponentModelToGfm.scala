package csw.services.icd.gfm

import csw.services.icd.model.ComponentModel

/**
 * Converts a ComponentModel instance to a GFM formatted string
 */
case class ComponentModelToGfm(m: ComponentModel) extends Gfm {
  val gfm =
    s"""
      |##Component: ${m.name}
      |
      |${m.description}
      |
      |
      |Component Type | Uses Time | Uses Events | Uses Configurations | Uses Properties
      |---|---|---|---|---
      |${m.componentType} | ${m.usesTime} | ${m.usesEvents} | ${m.usesConfigurations} | ${m.usesProperties}
      |
    """.stripMargin
}
