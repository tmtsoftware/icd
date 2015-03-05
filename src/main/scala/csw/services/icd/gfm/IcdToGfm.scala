package csw.services.icd.gfm

import csw.services.icd.IcdParser
import csw.services.icd.model._

/**
 * Converts an ICD model to "GitHub Flavored Markdown" or GFM.
 */
case class IcdToGfm(p: IcdParser, level: Level = Level()) extends Gfm {

  // Used to increment the level below
  private implicit val counter = (0 to 5).iterator

  // Ignore missing parts for now...
  val parts = List(
    p.icdModel.map(IcdModelToGfm(_, level).gfm),
    p.componentModel.map(ComponentModelToGfm(_, level.inc2()).gfm),
    p.publishModel.map(PublishModelToGfm(_, level.inc2()).gfm),
    p.subscribeModel.map(SubscribeModelToGfm(_, level.inc2()).gfm),
    p.commandModel.map(CommandModelToGfm(_, level.inc2()).gfm)).flatten

  /**
   * The "GitHub Flavored Markdown" or GFM for the model as a string
   */
  val gfm = parts.mkString("\n\n")
}
