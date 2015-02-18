package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/command-schema.conf
 */
object CommandModel {

  def apply(config: Config): CommandModel = {
    import net.ceedubs.ficus.Ficus._

    val name = config.as[Option[String]]("name").getOrElse("")
    val requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil)
    val requiredArgs = config.as[Option[List[String]]]("requiredArgs").getOrElse(Nil)
    val args = for (conf ← config.as[Option[List[Config]]]("args").getOrElse(Nil)) yield JsonSchemaModel(conf)

    CommandModel(name = name,
      requirements = requirements,
      requiredArgs = requiredArgs,
      args = args)
  }
}

case class CommandModel(name: String,
                        requirements: List[String],
                        requiredArgs: List[String],
                        args: List[JsonSchemaModel]) extends IcdModelBase {
}
