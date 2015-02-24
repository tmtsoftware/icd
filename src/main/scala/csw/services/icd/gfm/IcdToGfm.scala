package csw.services.icd.gfm

import csw.services.icd.IcdParser
import csw.services.icd.model._

/**
 * Converts an ICD model to "GitHub Flavored Markdown" or GFM.
 */
case class IcdToGfm(p: IcdParser) {

  val level = Level()

  private implicit val counter = (0 to 5).iterator

  // Ignore missing parts for now...
  val parts = List(
    p.icdModel.map(IcdModelToGfm(_).gfm),
    p.componentModel.map(ComponentModelToGfm(_).gfm),
    p.publishModel.map(PublishModelToGfm(_, level.inc1()).gfm),
    p.subscribeModel.map(SubscribeModelToGfm(_, level.inc1()).gfm),
    p.commandModel.map(CommandModelToGfm(_, level.inc1()).gfm)).flatten

  /**
   * The "GitHub Flavored Markdown" or GFM for the model as a string
   */
  val gfm = parts.mkString("\n\n---\n\n")
}
