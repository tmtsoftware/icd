package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/telemetry-schema.conf
 */
object TelemetryModel {

  import net.ceedubs.ficus.Ficus._

  // XXX TODO: define default values in schema and/or here? parse schema?
  def apply(config: Config): TelemetryModel =
    TelemetryModel(
      name = config.as[String]("name"),
      description = config.as[Option[String]]("description").getOrElse(""),
      rate = config.as[Option[Int]]("rate").getOrElse(0),
      archive = config.as[Option[Boolean]]("archive").getOrElse(false),
      archiveRate = config.as[Option[Int]]("archiveRate").getOrElse(0),
      maxRate = config.as[Option[Int]]("maxRate").getOrElse(0),
      attributesList = for (conf ← config.as[Option[List[Config]]]("attributes").getOrElse(Nil)) yield JsonSchemaModel(conf))
}

case class TelemetryModel(name: String,
                          description: String,
                          rate: Int,
                          archive: Boolean,
                          archiveRate: Int,
                          maxRate: Int,
                          attributesList: List[JsonSchemaModel])

