package csw.services.icd.parser

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmModel

/**
 * See resources/alarm-schema.conf
 */
object AlarmModelParser {

  import net.ceedubs.ficus.Ficus._

  // Note: Some required args treated as optional for backward compatibility
  def apply(config: Config): AlarmModel =
    AlarmModel(
      name = config.as[String]("name"),
      description = HtmlMarkup.gfmToHtml(config.as[String]("description")),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      severityLevels = config.as[Option[List[String]]]("severityLevels").getOrElse(List("Warning", "Major", "Critical")),
      archive = config.as[Option[Boolean]]("archive").getOrElse(true),
      location = config.as[Option[String]]("location").getOrElse(""),
      alarmType = config.as[Option[String]]("alarmType").getOrElse(""),
      probableCause = HtmlMarkup.gfmToHtml(config.as[Option[String]]("probableCause").getOrElse("")),
      operatorResponse = HtmlMarkup.gfmToHtml(config.as[Option[String]]("operatorResponse").getOrElse("")),
      acknowledge = config.as[Option[Boolean]]("acknowledge").getOrElse(false),
      latched = config.as[Option[Boolean]]("latched").getOrElse(false)
    )
}
