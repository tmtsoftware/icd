package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModel {

  def apply(config: Config): SubsystemModel =
    SubsystemModel(
      name = config.getString("name"),
      title = config.getString("title"),
      description = config.getString("description"),
      modelVersion = config.getString("modelVersion"),
      version = config.getInt("version"))
}

case class SubsystemModel(name: String,
                          title: String,
                          description: String,
                          modelVersion: String,
                          version: Int)

