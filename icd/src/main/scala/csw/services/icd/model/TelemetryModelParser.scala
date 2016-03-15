package csw.services.icd.model

import com.typesafe.config.Config
import icd.web.shared.IcdModels.TelemetryModel

/**
 * See resources/telemetry-schema.conf
 */
object TelemetryModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): TelemetryModel =
    TelemetryModel(
      name = config.as[String]("name"),
      description = config.as[Option[String]]("description").getOrElse(""),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      minRate = config.as[Option[Double]]("minRate").getOrElse(0),
      maxRate = config.as[Option[Double]]("maxRate").getOrElse(0),
      archive = config.as[Option[Boolean]]("archive").getOrElse(false),
      archiveRate = config.as[Option[Double]]("archiveRate").getOrElse(0),
      attributesList = for (conf ← config.as[Option[List[Config]]]("attributes").getOrElse(Nil)) yield AttributeModelParser(conf)
    )
}

