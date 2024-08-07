package csw.services.icd.db.parser

import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{EventModel, PublishModel}
import icd.web.shared.{PdfOptions, SubsystemWithVersion}
import reactivemongo.api.bson.*

/**
 * See resources/<version>/publish-schema.conf
 */
object PublishModelBsonParser {

  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions],
      observeEventMap: Map[String, EventModel],
      fitsKeyMap: FitsKeyMap,
      maybeSv: Option[SubsystemWithVersion]
  ): Option[PublishModel] = {
    if (doc.isEmpty) None
    else
      Some {
        val publishDoc = doc.getAsOpt[BSONDocument]("publish").get

        def getItems[A](name: String, f: BSONDocument => A): List[A] =
          for (subDoc <- publishDoc.children(name)) yield f(subDoc)

        def getObserveEventItems[A](name: String, f: String => A): List[A] =
          for (eventName <- publishDoc.getAsOpt[Array[String]](name).map(_.toList).getOrElse(Nil)) yield f(eventName)

        // For backward compatibility
        val oldEvents = getItems("telemetry", EventModelBsonParser(_, maybePdfOptions, fitsKeyMap, maybeSv)) ++
          getItems("eventStreams", EventModelBsonParser(_, maybePdfOptions, fitsKeyMap, maybeSv))

        PublishModel(
          subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
          component = doc.string(BaseModelBsonParser.componentKey).get,
          description =
            publishDoc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
          eventList = oldEvents ++ getItems("events", EventModelBsonParser(_, maybePdfOptions, fitsKeyMap, maybeSv)),
          observeEventList = getObserveEventItems("observeEvents", observeEventMap(_)),
          currentStateList = getItems("currentStates", EventModelBsonParser(_, maybePdfOptions, Map.empty, None)),
          imageList = getItems("images", ImageModelBsonParser(_, maybePdfOptions)),
          alarmList = getItems("alarms", AlarmModelBsonParser(_, maybePdfOptions))
        )
      }
  }
}
