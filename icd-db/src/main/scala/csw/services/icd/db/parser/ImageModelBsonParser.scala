package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ImageModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/image-schema.conf
 */
object ImageModelBsonParser {
  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions]
  ): ImageModel = {
    val name = doc.string("name").get
    ImageModel(
      name = name,
      description = doc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      channel = doc.string("channel").getOrElse(""),
      format = doc.string("format").getOrElse("FITS"),
      size = doc.getAsOpt[Array[Int]]("size").map(x => if (x.length == 2) (x.head, x.tail.head) else (0, 0)).getOrElse((0, 0)),
      pixelSize = doc.int("pixelSize").getOrElse(0),
      maybeMaxRate = doc.getAsOpt[Double]("maxRate"),
      metadataList =
        for (subDoc <- doc.children("metadata"))
          yield MetadataModelBsonParser(subDoc, maybePdfOptions)
    )
  }
}
