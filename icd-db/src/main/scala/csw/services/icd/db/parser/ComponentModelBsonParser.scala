package csw.services.icd.db.parser

import com.typesafe.scalalogging.Logger
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ComponentModel
import reactivemongo.api.bson._

/**
 * See resources/component-schema.conf
 */
object ComponentModelBsonParser {

  protected lazy val log: Logger = Logger("csw.services.icd.db.parser.ComponentModelBsonParser")

  def apply(doc: BSONDocument): Option[ComponentModel] = {
    if (doc.isEmpty) None
    else {
      val subsystem      = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get
      val component      = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get
      val prefix         = s"$subsystem.$component"
      val declaredPrefix = doc.getAsOpt[String]("prefix").getOrElse(prefix)
      if (declaredPrefix != prefix) {
        log.warn(s"Prefix '$declaredPrefix' ignored. Using subsystem.component ($prefix) instead.")
      }
      Some(
        ComponentModel(
          componentType = doc.getAsOpt[String]("componentType").get,
          subsystem = subsystem,
          component = component,
          title = doc.getAsOpt[String]("title").get,
          description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get),
          modelVersion = doc.getAsOpt[String]("modelVersion").get,
          wbsId = doc.getAsOpt[String]("wbsId").getOrElse("")
        )
      )
    }
  }
}
