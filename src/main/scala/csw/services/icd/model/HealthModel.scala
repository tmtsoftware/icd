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
      rate = config.as[Option[Double]]("rate").getOrElse(0.0),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true),
      archiveRate = config.as[Option[Double]]("archiveRate").getOrElse(0.0),
      maxRate = config.as[Option[Double]]("maxRate").getOrElse(0.0),
      valueType = JsonSchemaModel(config.getConfig("valueType"))
    )
  }
}

case class HealthModel(name: String,
                       description: String,
                       rate: Double,
                       archive: Boolean,
                       archiveRate: Double,
                       maxRate: Double,
                       valueType: JsonSchemaModel) extends IcdModelBase

