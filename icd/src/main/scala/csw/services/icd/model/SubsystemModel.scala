package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/subsystem-schema.conf
 */
object SubsystemModel {

  def apply(config: Config): SubsystemModel =
    SubsystemModel(
      subsystem = config.getString("subsystem"),
      title = config.getString("title"),
      description = config.getString("description"),
      modelVersion = config.getString("modelVersion")
    )
}

case class SubsystemModel(
  subsystem:    String,
  title:        String,
  description:  String,
  modelVersion: String
)

