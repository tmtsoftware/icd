package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.MetadataModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.BSONDocument

object MetadataModelBsonParser {
  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions]
  ): MetadataModel = {
    val name = doc.getAsOpt[String]("name").get
    MetadataModel(
      name = name,
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      keyword = doc.getAsOpt[String]("keyword").getOrElse("")
    )
  }

}
