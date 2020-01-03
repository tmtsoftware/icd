package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{SubscribeModel, SubscribeModelInfo}
import reactivemongo.api.bson._

/**
 * See resources/subscribe-schema.conf
 */
object SubscribeModelBsonParser {

  def apply(doc: BSONDocument): Option[SubscribeModel] = {
    if (doc.isEmpty) None
    else
      Some {
        val subscribeDoc = doc.getAsOpt[BSONDocument]("subscribe").get

        def getItems[A](name: String): List[SubscribeModelInfo] =
          for (subDoc <- subscribeDoc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil))
            yield SubscribeInfoBsonParser(subDoc)

        SubscribeModel(
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          description = subscribeDoc.getAsOpt[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
          eventList = getItems("events"),
          observeEventList = getItems("observeEvents"),
          currentStateList = getItems("currentStates"),
          alarmList = getItems("alarms")
        )
      }
  }
}

// Inner object in subscribe arrays
object SubscribeInfoBsonParser {

  def apply(doc: BSONDocument): SubscribeModelInfo =
    SubscribeModelInfo(
      subsystem = doc.getAsOpt[String]("subsystem").getOrElse(""),
      component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
      name = doc.getAsOpt[String]("name").getOrElse(""),
      usage = doc.getAsOpt[String]("usage").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requiredRate = safeNumGet("requiredRate", doc),
      maxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0))
    )
}
