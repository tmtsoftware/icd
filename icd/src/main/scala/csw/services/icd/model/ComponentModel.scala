package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/component-schema.conf
 */
object ComponentModel {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): ComponentModel =
    ComponentModel(
      componentType = config.as[String]("componentType"),
      subsystem = config.getString("subsystem"),
      prefix = config.as[String]("prefix"),
      name = config.as[String]("name"),
      title = config.getString("title"),
      description = config.as[String]("description"),
      modelVersion = config.getString("modelVersion"),
      version = config.getInt("version"),
      wbsId = config.as[Option[String]]("wbsId").getOrElse(""))
}

case class ComponentModel(componentType: String,
                          subsystem: String,
                          prefix: String,
                          name: String,
                          title: String,
                          description: String,
                          modelVersion: String,
                          version: Int,
                          wbsId: String)
