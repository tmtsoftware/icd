package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ImageModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

/**
 * See resources/<version>/image-schema.conf
 */
object ImageModelBsonParser {

  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions]
  ): ImageModel = {
    val name = doc.getAsOpt[String]("name").get
    ImageModel(
      name = name,
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      channel = doc.getAsOpt[String]("channel").getOrElse(""),
      metadataList =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("metadata").map(_.toList).getOrElse(Nil))
          yield MetadataModelBsonParser(subDoc, maybePdfOptions)
    )
  }
}
