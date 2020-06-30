package csw.services.icd.db.parser

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{BaseModel, SubsystemModel}
import icd.web.shared.PdfOptions

/**
 * Base model class used to hold only the subsystem and component name
 * (present in all the model files except for subsystem-model.conf)
 */
object BaseModelParser {

  val subsystemKey = "subsystem"
  val componentKey = "component"

  def apply(config: Config): BaseModel =
    BaseModel(
      subsystem = config.getString(subsystemKey),
      component = config.getString(componentKey)
    )
}

/**
 * Alternative parser for subsystem model that takes a Config as input.
 * See resources/subsystem-schema.conf
 */
object SubsystemModelParser {

  def apply(config: Config): SubsystemModel =
    SubsystemModel(
      subsystem = config.getString("subsystem"),
      title = config.getString("title"),
      description = config.getString("description"),
      modelVersion = config.getString("modelVersion")
    )
}
