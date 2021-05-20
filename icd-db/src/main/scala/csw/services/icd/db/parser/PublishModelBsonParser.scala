package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.PublishModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

/**
 * See resources/<version>/publish-schema.conf
 */
object PublishModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[PublishModel] = {
    if (doc.isEmpty) None else Some {
      val publishDoc = doc.getAsOpt[BSONDocument]("publish").get

      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- publishDoc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)

      // For backward compatibility
      val oldEvents = getItems("telemetry", EventModelBsonParser(_, maybePdfOptions)) ++ getItems("eventStreams", EventModelBsonParser(_, maybePdfOptions))

      PublishModel(
        subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
        component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
        description = publishDoc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
        eventList = oldEvents ++ getItems("events", EventModelBsonParser(_, maybePdfOptions)),
        observeEventList = getItems("observeEvents", EventModelBsonParser(_, maybePdfOptions)),
        currentStateList = getItems("currentStates", EventModelBsonParser(_, maybePdfOptions)),
        alarmList = getItems("alarms", AlarmModelBsonParser(_, maybePdfOptions))
      )
    }
  }
}
