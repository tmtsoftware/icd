package csw.services.icd.db.parser

import com.typesafe.config.{Config, ConfigObject}
import icd.web.shared.IcdModels.{ServiceModel, ServiceModelProvider}

import scala.jdk.CollectionConverters.*

/**
 * Alternative parser used during validation and ingesting into the database
 */
object ServiceModelParser {

  private object ServiceModelProviderParser {
    def apply(configObject: ConfigObject): ServiceModelProvider = {
      ServiceModelProvider(
        name = configObject.get("name").unwrapped().toString,
        description = configObject.get("description").unwrapped().toString,
        openApi = configObject.get("openApi").unwrapped().toString,
        Nil
      )
    }
  }

  def apply(config: Config): ServiceModel =
    ServiceModel(
      description = if (config.hasPath("description")) config.getString("description") else "",
      subsystem = config.getString("subsystem"),
      component = config.getString("component"),
      provides = if (config.hasPath("provides")) {
        config
          .getObjectList("provides")
          .asScala
          .toList
          .map(ServiceModelProviderParser(_))
      }
      else Nil,
      requires = Nil // Not needed in this case
    )
}
