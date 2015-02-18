package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * See resources/alarm-schema.conf
 */
object AlarmModel {

  def apply(config: Config): AlarmModel = {
    val name = config.as[Option[String]]("name").getOrElse("")
    val description = config.as[Option[String]]("description").getOrElse("")
    val severity = config.as[Option[String]]("severity").getOrElse("none")
    val archive = config.as[Option[String]]("archive").getOrElse("Yes")

    AlarmModel(name = name,
      description = description,
      severity = severity,
      archive = archive)
  }
}

case class AlarmModel(name: String,
                      description: String,
                      severity: String,
                      archive: String) extends IcdModelBase

