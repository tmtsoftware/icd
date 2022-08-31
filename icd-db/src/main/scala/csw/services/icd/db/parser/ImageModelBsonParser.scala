package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{EventModel, ImageModel}
import icd.web.shared.{PdfOptions, SubsystemWithVersion}
import reactivemongo.api.bson._

/**
 * See resources/<version>/image-schema.conf
 */
object ImageModelBsonParser {

  def apply(
             doc: BSONDocument,
             maybePdfOptions: Option[PdfOptions],
             maybeSv: Option[SubsystemWithVersion]
           ): ImageModel = {
    val name    = doc.getAsOpt[String]("name").get
    ImageModel(
      name = name,
      ref = doc.getAsOpt[String]("ref").getOrElse(""),
      refError = "",
      description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      metadataList =
        for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("metadata").map(_.toList).getOrElse(Nil))
          yield MetadataModelBsonParser(subDoc, maybePdfOptions, maybeSv, Some(name))
    )
  }
}
