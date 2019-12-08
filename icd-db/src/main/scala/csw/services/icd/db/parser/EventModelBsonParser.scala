package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel
import reactivemongo.bson.{BSONDocument, BSONNumberLike}

/**
 * See resources/event-schema.conf
 */
object EventModelBsonParser {

  def apply(doc: BSONDocument): EventModel =
    EventModel(
      name = doc.getAs[String]("name").get,
      description = doc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requirements = doc.getAs[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      maybeMaxRate = doc.getAs[BSONNumberLike]("maxRate").map(_.toDouble),
      archive = doc.getAs[Boolean]("archive").getOrElse(false),
      archiveDuration = doc.getAs[String]("archiveDuration").getOrElse(""),
      attributesList =
        for (subDoc <- doc.getAs[Array[BSONDocument]]("attributes").map(_.toList).getOrElse(Nil))
          yield AttributeModelBsonParser(subDoc),

    )
}
