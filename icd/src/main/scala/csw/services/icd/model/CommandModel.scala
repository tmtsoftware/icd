package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * Model for commands received: See resources/command-schema.conf
 */
object ReceiveCommandModel {
  def apply(config: Config): ReceiveCommandModel =
    ReceiveCommandModel(
      name = config.as[String]("name"),
      description = config.as[String]("description"),
      requirements = config.as[Option[List[String]]]("requirements").getOrElse(Nil),
      requiredArgs = config.as[Option[List[String]]]("requiredArgs").getOrElse(Nil),
      args = for (conf ← config.as[Option[List[Config]]]("args").getOrElse(Nil)) yield JsonSchemaModel(conf))
}

case class ReceiveCommandModel(name: String,
                               description: String,
                               requirements: List[String],
                               requiredArgs: List[String],
                               args: List[JsonSchemaModel])

/**
 * Model for commands sent
 */
object SendCommandModel {
  def apply(config: Config): SendCommandModel =
    SendCommandModel(
      name = config.as[String]("name"),
      subsystem = config.as[String](BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey))
}

case class SendCommandModel(name: String,
                            subsystem: String,
                            component: String)

/**
 * Model for commands
 */
object CommandModel {
  def apply(config: Config): CommandModel =
    CommandModel(
      subsystem = config.as[String](BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey),
      description = config.as[Option[String]]("description").getOrElse(""),
      receive = config.as[List[Config]]("receive").map(ReceiveCommandModel(_)),
      send = config.as[Option[List[Config]]]("send").getOrElse(Nil).map(SendCommandModel(_)))
}

/**
 * Model for commands received and sent by component: See resources/command-schema.conf
 */
case class CommandModel(subsystem: String,
                        component: String,
                        description: String,
                        receive: List[ReceiveCommandModel],
                        send: List[SendCommandModel])