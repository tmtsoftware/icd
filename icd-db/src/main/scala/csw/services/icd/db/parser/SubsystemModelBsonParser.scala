package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel
import reactivemongo.bson.BSONDocument

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModelBsonParser {

  def apply(doc: BSONDocument): SubsystemModel =
    SubsystemModel(
      subsystem = doc.getAs[String]("subsystem").get,
      title = doc.getAs[String]("title").get,
      description = HtmlMarkup.gfmToHtml(doc.getAs[String]("description").get),
      modelVersion = doc.getAs[String]("modelVersion").get
    )
}
