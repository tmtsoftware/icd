package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * See resources/health-schema.conf
 */
object HealthModel {

  def apply(config: Config): HealthModel = {
    // XXX TODO: define default values in schema and/or here? parse schema?
    val name = config.as[Option[String]]("name").getOrElse("")
    val description = config.as[Option[String]]("description").getOrElse("")
    val rate = config.as[Option[Double]]("rate").getOrElse(0.0)
    val archive = config.as[Option[String]]("archive").getOrElse("No")
    val archiveRate = config.as[Option[Double]]("archiveRate").getOrElse(0.0)
    val maxRate = config.as[Option[Double]]("maxRate").getOrElse(0.0)
    val valueType = JsonSchemaModel(config.getConfig("valueType"))

    HealthModel(name = name,
      description = description,
      rate = rate,
      archive = archive,
      archiveRate = archiveRate,
      maxRate = maxRate,
      valueType = valueType)
  }
}

case class HealthModel(name: String,
                       description: String,
                       rate: Double,
                       archive: String,
                       archiveRate: Double,
                       maxRate: Double,
                       valueType: JsonSchemaModel) extends IcdModelBase

