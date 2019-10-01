package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.PublishModel
import reactivemongo.bson.BSONDocument

/**
 * See resources/publish-schema.conf
 */
object PublishModelBsonParser {

  def apply(doc: BSONDocument): PublishModel = {
    val publishDoc = doc.getAs[BSONDocument]("publish").get

    def getItems[A](name: String, f: BSONDocument => A): List[A] =
      for (subDoc <- publishDoc.getAs[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)

    // For backward compatibility
    val oldEvents = getItems("telemetry", EventModelBsonParser(_)) ++ getItems("eventStreams", EventModelBsonParser(_))

    PublishModel(
      subsystem = doc.getAs[String](BaseModelBsonParser.subsystemKey).get,
      component = doc.getAs[String](BaseModelBsonParser.componentKey).get,
      description = publishDoc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
      eventList = oldEvents ++ getItems("events", EventModelBsonParser(_)),
      observeEventList = getItems("observeEvents", EventModelBsonParser(_)),
      currentStateList = getItems("currentStates", EventModelBsonParser(_)),
      alarmList = getItems("alarms", AlarmModelBsonParser(_))
    )
  }
}
