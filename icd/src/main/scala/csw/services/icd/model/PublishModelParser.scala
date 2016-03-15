package csw.services.icd.model

import com.typesafe.config.Config
import icd.web.shared.IcdModels.PublishModel

/**
 * See resources/publish-schema.conf
 */
object PublishModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): PublishModel = {
    val publishConfig = config.getConfig("publish")

    def getItems[A](name: String, f: Config ⇒ A): List[A] =
      for (conf ← publishConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield f(conf)

    PublishModel(
      subsystem = config.as[String](BaseModelParser.subsystemKey),
      component = config.as[String](BaseModelParser.componentKey),
      description = publishConfig.as[Option[String]]("description").getOrElse(""),
      telemetryList = getItems("telemetry", TelemetryModelParser(_)),
      eventList = getItems("events", TelemetryModelParser(_)),
      eventStreamList = getItems("eventStreams", TelemetryModelParser(_)),
      alarmList = getItems("alarms", AlarmModelParser(_))
    )
  }
}
