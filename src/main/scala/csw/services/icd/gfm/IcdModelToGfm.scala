package csw.services.icd.gfm

import csw.services.icd.model.IcdModel

/**
 * Converts an IcdModel instance to a GFM formatted string
 */
case class IcdModelToGfm(m: IcdModel) {

  val gfm =
    s"""
      |#Interface Control Document: ${m.name}
      |
      | Version | Subsystem | WBS Id
      | ---|---|---
      |${m.version} | ${m.subsystem} | ${m.wbsId}
      |
      |${m.description}
      |
    """.stripMargin
}
