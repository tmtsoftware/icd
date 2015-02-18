package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/subscribe-schema.conf
 */
object SubscribeModel {

  def apply(config: Config): SubscribeModel = {
    import net.ceedubs.ficus.Ficus._

    val subscribeConfig = config.getConfig("subscribe")

    def getItems(name: String): List[SubscribeInfo] =
      for (conf ← subscribeConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield SubscribeInfo(conf)

    val telemetryList = getItems("telemetry")
    val eventList = getItems("events")
    val eventStreamList = getItems("eventStreams")
    val alarmList = getItems("alarms")
    val healthList = getItems("health")

    SubscribeModel(
      telemetryList = telemetryList,
      eventList = eventList,
      eventStreamList = eventStreamList,
      alarmList = alarmList,
      healthList = healthList)
  }
}

case class SubscribeModel(telemetryList: List[SubscribeInfo],
                          eventList: List[SubscribeInfo],
                          eventStreamList: List[SubscribeInfo],
                          alarmList: List[SubscribeInfo],
                          healthList: List[SubscribeInfo]) extends IcdModelBase

// Inner object in subscribe arrays
object SubscribeInfo {
  import net.ceedubs.ficus.Ficus._
  def apply(config: Config): SubscribeInfo = {
    val subsystem = config.as[Option[String]]("subsystem").getOrElse("")
    val name = config.as[Option[String]]("name").getOrElse("")
    val requiredRate = config.as[Option[Int]]("requiredRate").getOrElse(0)
    val maxRate = config.as[Option[Int]]("maxRate").getOrElse(0)

    SubscribeInfo(subsystem = subsystem,
      name = name,
      requiredRate = requiredRate,
      maxRate = maxRate)
  }
}

case class SubscribeInfo(subsystem: String, name: String, requiredRate: Int, maxRate: Int)

