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
      subsystem = config.as[String](BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey),
      prefix = config.as[String]("prefix"),
      title = config.as[String]("title"),
      description = config.as[String]("description"),
      modelVersion = config.as[String]("modelVersion"),
      wbsId = config.as[Option[String]]("wbsId").getOrElse(""))
}

case class ComponentModel(componentType: String,
                          subsystem: String,
                          component: String,
                          prefix: String,
                          title: String,
                          description: String,
                          modelVersion: String,
                          wbsId: String)
