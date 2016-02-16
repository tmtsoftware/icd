package csw.services.icd.model

import com.typesafe.config.Config

/**
 * Fake model class used to hold only the subsystem and component name
 */
object BaseModel {

  val subsystemKey = "subsystem"
  val componentKey = "component"

  def apply(config: Config): BaseModel =
    BaseModel(
      subsystem = config.getString(subsystemKey),
      component = config.getString(componentKey)
    )
}

case class BaseModel(subsystem: String, component: String)
