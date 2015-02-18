package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/telemetry-schema.conf
 */
object TelemetryModel {

  def apply(config: Config): TelemetryModel = {
    import net.ceedubs.ficus.Ficus._

    // XXX TODO: define default values in schema and/or here? parse schema?
    val name = config.as[Option[String]]("name").getOrElse("")
    val description = config.as[Option[String]]("description").getOrElse("")
    val rate = config.as[Option[Double]]("rate").getOrElse(0.0)
    val archive = config.as[Option[String]]("archive").getOrElse("No")
    val archiveRate = config.as[Option[Double]]("archiveRate").getOrElse(0.0)
    val maxRate = config.as[Option[Double]]("maxRate").getOrElse(0.0)
    val attributesList = for (conf ← config.as[Option[List[Config]]]("attributes").getOrElse(Nil)) yield JsonSchemaModel(conf)

    TelemetryModel(name = name,
      description = description,
      rate = rate,
      archive = archive,
      archiveRate = archiveRate,
      maxRate = maxRate,
      attributesList = attributesList)
  }
}

case class TelemetryModel(name: String,
                          description: String,
                          rate: Double,
                          archive: String,
                          archiveRate: Double,
                          maxRate: Double,
                          attributesList: List[JsonSchemaModel]) extends IcdModelBase

