package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

/**
 * See resources/<version>/subsystem-schema.conf
 */
object SubsystemModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[SubsystemModel] = {
    if (doc.isEmpty) None
    else
      Some(
        SubsystemModel(
          subsystem = doc.getAsOpt[String]("subsystem").get,
          title = doc.getAsOpt[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions),
          modelVersion = doc.getAsOpt[String]("modelVersion").get
        )
      )
  }
}

