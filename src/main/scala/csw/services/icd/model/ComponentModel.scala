package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/component-schema.conf
 */
object ComponentModel {
  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): ComponentModel =
    ComponentModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      usesTime = config.as[Option[Boolean]]("usesTime").getOrElse(false),
      usesEvents = config.as[Option[Boolean]]("usesEvents").getOrElse(false),
      usesConfigurations = config.as[Option[Boolean]]("usesConfigurations").getOrElse(false),
      usesProperties = config.as[Option[Boolean]]("usesProperties").getOrElse(false),
      componentType = config.as[String]("componentType"))
}

case class ComponentModel(name: String,
                          description: String,
                          usesTime: Boolean,
                          usesEvents: Boolean,
                          usesProperties: Boolean,
                          usesConfigurations: Boolean,
                          componentType: String)
