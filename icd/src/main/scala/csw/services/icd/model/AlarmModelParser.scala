package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmModel

/**
 * See resources/alarm-schema.conf
 */
object AlarmModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): AlarmModel =
    AlarmModel(
      name = config.as[String]("name"),
      description = HtmlMarkup.gfmToHtml(config.as[String]("description")),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      severity = config.as[Option[String]]("severity").getOrElse("none"),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true)
    )
}

