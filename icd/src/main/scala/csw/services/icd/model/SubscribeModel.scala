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
      alarmList = getItems("alarms")
    )
  }
}

/**
 * Describes the items a component subscribes to
 * @param subsystem the component's subsystem
 * @param component the component
 * @param description a top level description of the subscribed items
 * @param telemetryList list of subscribed telemetry
 * @param eventList list of subscribed events
 * @param eventStreamList list of subscribed event streams
 * @param alarmList list of subscribed alarms
 */
case class SubscribeModel(
  subsystem:       String,
  component:       String,
  description:     String,
  telemetryList:   List[SubscribeInfo],
  eventList:       List[SubscribeInfo],
  eventStreamList: List[SubscribeInfo],
  alarmList:       List[SubscribeInfo]
)

// Inner object in subscribe arrays
object SubscribeInfo {

  def apply(config: Config): SubscribeInfo =
    SubscribeInfo(
      subsystem = config.as[Option[String]]("subsystem").getOrElse(""),
      component = config.as[String](BaseModel.componentKey),
      name = config.as[Option[String]]("name").getOrElse(""),
      usage = config.as[Option[String]]("usage").getOrElse(""),
      requiredRate = config.as[Option[Double]]("requiredRate").getOrElse(0),
      maxRate = config.as[Option[Double]]("maxRate").getOrElse(0)
    )
}

/**
 * Describes an item the component subscribes to
 * @param subsystem the publisher's subsystem
 * @param component the publisher's component
 * @param name the name of the published item
 * @param usage describes how the item is used by the subscriber
 * @param requiredRate the required rate
 * @param maxRate the max rate
 */
case class SubscribeInfo(
  subsystem:    String,
  component:    String,
  name:         String,
  usage:        String,
  requiredRate: Double,
  maxRate:      Double
)

