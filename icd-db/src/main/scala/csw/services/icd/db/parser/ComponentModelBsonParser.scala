package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ComponentModel
import reactivemongo.bson.BSONDocument

/**
 * See resources/component-schema.conf
 */
object ComponentModelBsonParser {

  def apply(doc: BSONDocument): Option[ComponentModel] = {
    if (doc.isEmpty) None
    else
      Some(
        ComponentModel(
          componentType = doc.getAs[String]("componentType").get,
          subsystem = doc.getAs[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAs[String](BaseModelBsonParser.componentKey).get,
          prefix = doc.getAs[String]("prefix").get,
          title = doc.getAs[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAs[String]("description").get),
          modelVersion = doc.getAs[String]("modelVersion").get,
          wbsId = doc.getAs[String]("wbsId").getOrElse("")
        )
      )
  }
}
