package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/component-schema.conf
 */
object ComponentModel {
  def apply(config: Config): ComponentModel = {
    ComponentModel(
      name = config.getString("name"),
      description = config.getString("description"),
      usesTime = config.getBoolean("usesTime"),
      usesEvents = config.getBoolean("usesEvents"),
      usesConfigurations = config.getBoolean("usesConfigurations"),
      usesProperties = config.getBoolean("usesProperties"),
      componentType = config.getString("componentType")
    )
  }
}

case class ComponentModel(name: String,
                          description: String,
                          usesTime: Boolean,
                          usesEvents: Boolean,
                          usesProperties: Boolean,
                          usesConfigurations: Boolean,
                          componentType: String) extends IcdModelBase
