package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{CommandModel, SendCommandModel, ReceiveCommandModel}
import net.ceedubs.ficus.Ficus._

/**
 * Model for commands received: See resources/command-schema.conf
 */
object ReceiveCommandModelParser {
  def apply(config: Config): ReceiveCommandModel =
    ReceiveCommandModel(
      name = config.as[String]("name"),
      description = HtmlMarkup.gfmToHtml(config.as[String]("description")),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      requiredArgs = config.as[Option[List[String]]]("requiredArgs").getOrElse(Nil),
      args = for (conf <- config.as[Option[List[Config]]]("args").getOrElse(Nil)) yield AttributeModelParser(conf)
    )
}

/**
 * Model for commands sent
 */
object SendCommandModelParser {
  def apply(config: Config): SendCommandModel =
    SendCommandModel(
      name = config.as[String]("name"),
      subsystem = config.as[String](BaseModelParser.subsystemKey),
      component = config.as[String](BaseModelParser.componentKey)
    )
}

/**
 * Model for commands
 */
object CommandModelParser {
  def apply(config: Config): CommandModel =
    CommandModel(
      subsystem = config.as[String](BaseModelParser.subsystemKey),
      component = config.as[String](BaseModelParser.componentKey),
      description = config.as[Option[String]]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      receive = config.as[List[Config]]("receive").map(ReceiveCommandModelParser(_)),
      send = config.as[Option[List[Config]]]("send").getOrElse(Nil).map(SendCommandModelParser(_))
    )
}

