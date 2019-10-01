package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{SubscribeModel, SubscribeModelInfo}
import reactivemongo.bson.BSONDocument

/**
 * See resources/subscribe-schema.conf
 */
object SubscribeModelBsonParser {

  def apply(doc: BSONDocument): SubscribeModel = {
    val subscribeDoc = doc.getAs[BSONDocument]("subscribe").get

    def getItems[A](name: String): List[SubscribeModelInfo] =
      for (subDoc <- subscribeDoc.getAs[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield SubscribeInfoBsonParser(subDoc)

    SubscribeModel(
      subsystem = doc.getAs[String](BaseModelBsonParser.subsystemKey).get,
      component = doc.getAs[String](BaseModelBsonParser.componentKey).get,
      description = subscribeDoc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      eventList = getItems("events"),
      observeEventList = getItems("observeEvents"),
      currentStateList = getItems("currentStates"),
      alarmList = getItems("alarms")
    )
  }
}

// Inner object in subscribe arrays
object SubscribeInfoBsonParser {

  def apply(doc: BSONDocument): SubscribeModelInfo =
    SubscribeModelInfo(
      subsystem = doc.getAs[String]("subsystem").getOrElse(""),
      component = doc.getAs[String](BaseModelBsonParser.componentKey).get,
      name = doc.getAs[String]("name").getOrElse(""),
      usage = doc.getAs[String]("usage").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      requiredRate = doc.getAs[Double]("requiredRate").getOrElse(0.0),
      maxRate = doc.getAs[Double]("maxRate").getOrElse(0.0)
    )
}
