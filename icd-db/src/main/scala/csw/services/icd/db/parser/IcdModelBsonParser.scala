package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.IcdModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

/**
 * See resources/<version>/icd-schema.conf
 */
object IcdModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[IcdModel] = {
    if (doc.isEmpty) None
    else
      Some(
        IcdModel(
          subsystem = doc.getAsOpt[String]("subsystem").get,
          targetSubsystem = doc.getAsOpt[String]("targetSubsystem").get,
          title = doc.getAsOpt[String]("title").getOrElse(""),
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions)
        )
      )
  }
}

