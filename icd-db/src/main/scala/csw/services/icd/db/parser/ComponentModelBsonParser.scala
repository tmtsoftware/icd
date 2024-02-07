package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ComponentModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/component-schema.conf
 */
object ComponentModelBsonParser {

  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions] = None,
      maybeSubsystemVersion: Option[String] = None
  ): Option[ComponentModel] = {
    if (doc.isEmpty) None
    else {
      val subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get
      val component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get
      Some(
        ComponentModel(
          componentType = doc.getAsOpt[String]("componentType").get,
          subsystem = subsystem,
          component = component,
          title = doc.getAsOpt[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions),
          modelVersion = doc.getAsOpt[String]("modelVersion").get,
          wbsId = doc.getAsOpt[String]("wbsId").getOrElse(""),
          maybeSubsystemVersion = maybeSubsystemVersion
        )
      )
    }
  }
}
