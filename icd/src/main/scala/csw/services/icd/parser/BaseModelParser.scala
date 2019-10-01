package csw.services.icd.parser

import com.typesafe.config.Config
import icd.web.shared.IcdModels.BaseModel

/**
 * Fake model class used to hold only the subsystem and component name
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
