package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.IcdModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/icd-schema.conf
 */
object IcdModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[IcdModel] = {
    if (doc.isEmpty) None
    else {
      val subsystem       = doc.getAsOpt[String]("subsystem").get
      val targetSubsystem = doc.getAsOpt[String]("targetSubsystem").get
      val title           = doc.getAsOpt[String]("title").getOrElse(s"Interface between $subsystem and $targetSubsystem")
      Some(
        IcdModel(
          subsystem = subsystem,
          targetSubsystem = targetSubsystem,
          title = title,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions)
        )
      )
    }
  }
}
