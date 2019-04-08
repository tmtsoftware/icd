package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{SubscribeModelInfo, SubscribeModel}
import net.ceedubs.ficus.Ficus._

/**
 * See resources/subscribe-schema.conf
 */
object SubscribeModelParser {

  def apply(config: Config): SubscribeModel = {
    val subscribeConfig = config.getConfig("subscribe")

    def getItems(name: String): List[SubscribeModelInfo] =
      for (conf <- subscribeConfig.as[Option[List[Config]]](name).getOrElse(Nil)) yield SubscribeInfoParser(conf)

    SubscribeModel(
      subsystem = config.as[String](BaseModelParser.subsystemKey),
      component = config.as[String](BaseModelParser.componentKey),
      description = subscribeConfig.as[Option[String]]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      eventList = getItems("events"),
      observeEventList = getItems("observeEvents"),
      currentStateList = getItems("currentStates"),
      alarmList = getItems("alarms")
    )
  }
}

// Inner object in subscribe arrays
object SubscribeInfoParser {

  def apply(config: Config): SubscribeModelInfo =
    SubscribeModelInfo(
      subsystem = config.as[Option[String]]("subsystem").getOrElse(""),
      component = config.as[String](BaseModelParser.componentKey),
      name = config.as[Option[String]]("name").getOrElse(""),
      usage = config.as[Option[String]]("usage").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requiredRate = config.as[Option[Double]]("requiredRate").getOrElse(0.0),
      maxRate = config.as[Option[Double]]("maxRate").getOrElse(0.0)
    )
}

