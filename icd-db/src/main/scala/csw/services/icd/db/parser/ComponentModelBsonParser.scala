package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ComponentModel
import reactivemongo.api.bson.BSONDocument

/**
 * See resources/component-schema.conf
 */
object ComponentModelBsonParser {

  def apply(doc: BSONDocument): Option[ComponentModel] = {
    if (doc.isEmpty) None
    else
      Some(
        ComponentModel(
          componentType = doc.getAsOpt[String]("componentType").get,
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          prefix = doc.getAsOpt[String]("prefix").get,
          title = doc.getAsOpt[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get),
          modelVersion = doc.getAsOpt[String]("modelVersion").get,
          wbsId = doc.getAsOpt[String]("wbsId").getOrElse("")
        )
      )
  }
}
