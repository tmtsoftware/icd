package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ComponentModel

/**
 * See resources/component-schema.conf
 */
object ComponentModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): ComponentModel =
    ComponentModel(
      componentType = config.as[String]("componentType"),
      subsystem = config.as[String](BaseModelParser.subsystemKey),
      component = config.as[String](BaseModelParser.componentKey),
      prefix = config.as[String]("prefix"),
      title = config.as[String]("title"),
      description = HtmlMarkup.gfmToHtml(config.as[String]("description")),
      modelVersion = config.as[String]("modelVersion"),
      wbsId = config.as[Option[String]]("wbsId").getOrElse("")
    )
}

