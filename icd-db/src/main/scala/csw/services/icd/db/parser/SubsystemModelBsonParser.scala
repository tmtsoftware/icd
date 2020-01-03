package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel
import reactivemongo.api.bson._

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModelBsonParser {

  def apply(doc: BSONDocument): Option[SubsystemModel] = {
    if (doc.isEmpty) None
    else
      Some(
        SubsystemModel(
          subsystem = doc.getAsOpt[String]("subsystem").get,
          title = doc.getAsOpt[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get),
          modelVersion = doc.getAsOpt[String]("modelVersion").get
        )
      )
  }
}

