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
      val subsystem       = doc.string("subsystem").get
      val targetSubsystem = doc.string("targetSubsystem").get
      val title           = doc.string("title").getOrElse(s"Interface between $subsystem and $targetSubsystem")
      Some(
        IcdModel(
          subsystem = subsystem,
          targetSubsystem = targetSubsystem,
          title = title,
          description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions)
        )
      )
    }
  }
}
