package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/subsystem-schema.conf
 */
object SubsystemModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[SubsystemModel] = {
    if (doc.isEmpty) None
    else {
      Some(
        SubsystemModel(
          subsystem = doc.string("subsystem").get,
          title = doc.string("title").get,
          description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions),
          modelVersion = doc.string("modelVersion").get
        )
      )
    }
  }
}
