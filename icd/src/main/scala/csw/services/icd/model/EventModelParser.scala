package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.EventModel

/**
 * See resources/event-schema.conf
 */
object EventModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): EventModel =
    EventModel(
      name = config.as[String]("name"),
      description = config.as[Option[String]]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      minRate = config.as[Option[Double]]("minRate").getOrElse(0.0),
      maxRate = config.as[Option[Double]]("maxRate").getOrElse(0.0),
      archive = config.as[Option[Boolean]]("archive").getOrElse(false),
      archiveDuration = config.as[Option[String]]("archiveDuration").getOrElse(""),
      archiveRate = config.as[Option[Double]]("archiveRate").getOrElse(0.0),
      attributesList = for (conf <- config.as[Option[List[Config]]]("attributes").getOrElse(Nil)) yield AttributeModelParser(conf)
    )
}
