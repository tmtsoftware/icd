package csw.services.icd.model

import com.typesafe.config.Config
import EventStreamModel.EventStreamModel

/**
 * See resources/publish-schema.conf
 */
object PublishModel {

  def apply(config: Config): PublishModel = {
    import net.ceedubs.ficus.Ficus._

    val publishConfig = config.getConfig("publish")

    def getItems[A](name: String, f: Config ⇒ A): List[A] =
      for (conf ← publishConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield f(conf)

    val telemetryList = getItems("telemetry", TelemetryModel(_))
    val eventList = getItems("events", JsonSchemaModel)
    val eventStreamList = getItems("eventStreams", EventStreamModel(_))
    val alarmList = getItems("alarms", AlarmModel(_))
    val healthList = getItems("health", HealthModel(_))

    PublishModel(telemetryList = telemetryList,
      eventList = eventList,
      eventStreamList = eventStreamList,
      alarmList = alarmList,
      healthList = healthList)
  }
}

case class PublishModel(telemetryList: List[TelemetryModel],
                        eventList: List[JsonSchemaModel],
                        eventStreamList: List[EventStreamModel],
                        alarmList: List[AlarmModel],
                        healthList: List[HealthModel]) extends IcdModelBase
