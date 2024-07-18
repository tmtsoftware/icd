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
      val subsystem = doc.string(BaseModelBsonParser.subsystemKey).get
      val component = doc.string(BaseModelBsonParser.componentKey).get
      Some(
        ComponentModel(
          componentType = doc.string("componentType").get,
          subsystem = subsystem,
          component = component,
          title = doc.string("title").get,
          description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions),
          modelVersion = doc.string("modelVersion").get,
          wbsId = doc.string("wbsId").getOrElse(""),
          maybeSubsystemVersion = maybeSubsystemVersion
        )
      )
    }
  }
}
