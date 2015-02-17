package csw.services.icd

import com.typesafe.config.Config

object ComponentModel {
  def apply(config: Config): ComponentModel = {
    ComponentModel(
      componentType = config.getString("componentType"),
      description = config.getString("description"),
      name = config.getString("name"),
      usesConfigurations = config.getString("usesConfigurations"),
      usesEvents = config.getString("usesEvents"),
      usesProperties = config.getString("usesProperties"),
      usesTime = config.getString("usesTime")
    )
  }
}

case class ComponentModel(componentType: String,
                          description: String,
                          name: String,
                          usesConfigurations: String,
                          usesEvents: String,
                          usesProperties: String,
                          usesTime: String) extends IcdModelBase
