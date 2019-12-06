package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel
import reactivemongo.bson.BSONDocument

/**
 * See resources/event-schema.conf
 */
object EventModelBsonParser {

  def apply(doc: BSONDocument): EventModel =
    EventModel(
      name = doc.getAs[String]("name").get,
      description = doc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requirements = doc.getAs[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      minRate = safeNumGet("minRate", doc),
      maxRate = safeNumGet("maxRate", doc),
      archive = doc.getAs[Boolean]("archive").getOrElse(false),
      archiveDuration = doc.getAs[String]("archiveDuration").getOrElse(""),
      archiveRate = safeNumGet("archiveRate", doc),
      attributesList =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("attributes").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),

    )
}
