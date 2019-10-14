package csw.services.icd.db.parser

import reactivemongo.bson.BSONDocument
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModelBsonParser {

  def apply(doc: BSONDocument): Option[SubsystemModel] = {
    if (doc.isEmpty) None
    else
      Some(
        SubsystemModel(
          subsystem = doc.getAs[String]("subsystem").get,
          title = doc.getAs[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAs[String]("description").get),
          modelVersion = doc.getAs[String]("modelVersion").get
        )
      )
  }
}

