package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/alarm-schema.conf
 */
object AlarmModel {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): AlarmModel =
    AlarmModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      severity = config.as[Option[String]]("severity").getOrElse("none"),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true))
}

case class AlarmModel(name: String,
                      description: String,
                      severity: String,
                      archive: Boolean)

