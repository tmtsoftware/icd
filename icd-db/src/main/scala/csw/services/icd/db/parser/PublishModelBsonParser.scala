package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.PublishModel
import reactivemongo.api.bson.BSONDocument

/**
 * See resources/publish-schema.conf
 */
object PublishModelBsonParser {

  def apply(doc: BSONDocument): Option[PublishModel] = {
    if (doc.isEmpty) None else Some {
      val publishDoc = doc.getAsOpt[BSONDocument]("publish").get

      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- publishDoc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)

      // For backward compatibility
      val oldEvents = getItems("telemetry", EventModelBsonParser(_)) ++ getItems("eventStreams", EventModelBsonParser(_))

      PublishModel(
        subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
        component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
        description = publishDoc.getAsOpt[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse(""),
        eventList = oldEvents ++ getItems("events", EventModelBsonParser(_)),
        observeEventList = getItems("observeEvents", EventModelBsonParser(_)),
        currentStateList = getItems("currentStates", EventModelBsonParser(_)),
        alarmList = getItems("alarms", AlarmModelBsonParser(_))
      )
    }
  }
}
