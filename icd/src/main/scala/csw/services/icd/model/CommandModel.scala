package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * See resources/command-schema.conf
 */
object CommandItemModel {
  def apply(config: Config): CommandItemModel =
    CommandItemModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      requiredArgs = config.as[Option[List[String]]]("requiredArgs").getOrElse(Nil),
      args = for (conf ← config.as[Option[List[Config]]]("args").getOrElse(Nil)) yield JsonSchemaModel(conf))
}

case class CommandItemModel(name: String,
                            description: String,
                            requirements: List[String],
                            requiredArgs: List[String],
                            args: List[JsonSchemaModel]) {
}

object CommandModel {
  def apply(config: Config): CommandModel =
    CommandModel(
      subsystem = config.getString(BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey),
      description = config.as[Option[String]]("description").getOrElse(""),
      items = config.as[List[Config]]("configurations").map(CommandItemModel(_)))
}

case class CommandModel(subsystem: String,
                        component: String,
                        description: String,
                        items: List[CommandItemModel]) {
}
