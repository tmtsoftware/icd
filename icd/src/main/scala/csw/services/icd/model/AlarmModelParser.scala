package csw.services.icd.model

import com.typesafe.config.Config
import icd.web.shared.IcdModels.AlarmModel

/**
 * See resources/alarm-schema.conf
 */
object AlarmModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): AlarmModel =
    AlarmModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      severity = config.as[Option[String]]("severity").getOrElse("none"),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true)
    )
}

