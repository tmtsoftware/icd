package csw.services.icd.model

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/**
 * See resources/subscribe-schema.conf
 */
object SubscribeModel {

  def apply(config: Config): SubscribeModel = {
    val subscribeConfig = config.getConfig("subscribe")

    def getItems(name: String): List[SubscribeInfo] =
      for (conf ← subscribeConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield SubscribeInfo(conf)

    SubscribeModel(
      subsystem = config.as[String](BaseModel.subsystemKey),
      component = config.as[String](BaseModel.componentKey),
      description = subscribeConfig.as[Option[String]]("description").getOrElse(""),
      telemetryList = getItems("telemetry"),
      eventList = getItems("events"),
      eventStreamList = getItems("eventStreams"),
      alarmList = getItems("alarms"))
  }
}

case class SubscribeModel(subsystem: String,
                          component: String,
                          description: String,
                          telemetryList: List[SubscribeInfo],
                          eventList: List[SubscribeInfo],
                          eventStreamList: List[SubscribeInfo],
                          alarmList: List[SubscribeInfo])

// Inner object in subscribe arrays
object SubscribeInfo {

  def apply(config: Config): SubscribeInfo =
    SubscribeInfo(
      subsystem = config.as[Option[String]]("subsystem").getOrElse(""),
      name = config.as[Option[String]]("name").getOrElse(""),
      requiredRate = config.as[Option[Int]]("requiredRate").getOrElse(0),
      maxRate = config.as[Option[Int]]("maxRate").getOrElse(0))
}

case class SubscribeInfo(subsystem: String, name: String, requiredRate: Int, maxRate: Int)

