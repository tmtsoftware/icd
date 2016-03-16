package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.SubsystemModel

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModelParser {

  def apply(config: Config): SubsystemModel =
    SubsystemModel(
      subsystem = config.getString("subsystem"),
      title = config.getString("title"),
      description = HtmlMarkup.gfmToHtml(config.getString("description")),
      modelVersion = config.getString("modelVersion")
    )
}

