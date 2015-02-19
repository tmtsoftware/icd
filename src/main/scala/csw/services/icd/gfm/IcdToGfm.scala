package csw.services.icd.gfm

import csw.services.icd.IcdParser
import csw.services.icd.model._

/**
 * Converts an ICD model to "GitHub Flavored Markdown" or GFM.
 */
case class IcdToGfm(p: IcdParser) {

  // Ignore missing parts for now...
  val parts = List(
    p.icdModel.map(IcdModelToGfm(_).gfm),
    p.componentModel.map(ComponentModelToGfm(_).gfm),
    p.publishModel.map(PublishModelToGfm(_).gfm),
    p.subscribeModel.map(toGfm),
    p.commandModel.map(toGfm)
  ).flatten

  /**
   * The "GitHub Flavored Markdown" or GFM for the model as a string
   */
  val gfm = parts.mkString("\n\n")


  // --- XXX TMP
  private def toGfm(model: SubscribeModel): String = ""
  private def toGfm(model: CommandModel): String = ""
}
