package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * See resources/health-schema.conf
 */
object HealthModel {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): HealthModel = {
    // XXX TODO: define default values in schema and/or here? parse schema?

    HealthModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      rate = config.as[Option[Int]]("rate").getOrElse(0),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true),
      archiveRate = config.as[Option[Int]]("archiveRate").getOrElse(0),
      maxRate = config.as[Option[Int]]("maxRate").getOrElse(0),
      valueType = JsonSchemaModel(config.getConfig("valueType")))
  }
}

case class HealthModel(name: String,
                       description: String,
                       rate: Int,
                       archive: Boolean,
                       archiveRate: Int,
                       maxRate: Int,
                       valueType: JsonSchemaModel)

