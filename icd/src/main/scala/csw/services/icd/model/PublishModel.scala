package csw.services.icd.model

import com.typesafe.config.Config
import EventStreamModel.EventStreamModel

/**
 * See resources/publish-schema.conf
 */
object PublishModel {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): PublishModel = {
    val publishConfig = config.getConfig("publish")

    def getItems[A](name: String, f: Config ⇒ A): List[A] =
      for (conf ← publishConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield f(conf)

    PublishModel(
      subsystem = config.as[String](BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey),
      description = publishConfig.as[Option[String]]("description").getOrElse(""),
      telemetryList = getItems("telemetry", TelemetryModel(_)),
      eventList = getItems("events", JsonSchemaModel),
      eventStreamList = getItems("eventStreams", EventStreamModel(_)),
      alarmList = getItems("alarms", AlarmModel(_)))
  }
}

case class PublishModel(subsystem: String,
                        component: String,
                        description: String,
                        telemetryList: List[TelemetryModel],
                        eventList: List[JsonSchemaModel],
                        eventStreamList: List[EventStreamModel],
                        alarmList: List[AlarmModel])
