package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{SubscribeModel, SubscribeModelInfo}
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/subscribe-schema.conf
 */
object SubscribeModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[SubscribeModel] = {
    if (doc.isEmpty) None
    else
      Some {
        val subscribeDoc = doc.getAsOpt[BSONDocument]("subscribe").get

        def getItems(name: String): List[SubscribeModelInfo] =
          for (subDoc <- subscribeDoc.children(name))
            yield SubscribeInfoBsonParser(subDoc, maybePdfOptions)

        // For backward compatibility
        val oldEvents = getItems("telemetry") ++ getItems("eventStreams")

        SubscribeModel(
          subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
          component = doc.string(BaseModelBsonParser.componentKey).get,
          description = subscribeDoc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
          eventList = oldEvents ++ getItems("events"),
          observeEventList = getItems("observeEvents"),
          currentStateList = getItems("currentStates"),
          imageList = getItems("images"),
        )
      }
  }
}

// Inner object in subscribe arrays
object SubscribeInfoBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): SubscribeModelInfo =
    SubscribeModelInfo(
      subsystem = doc.string("subsystem").getOrElse(""),
      component = doc.string(BaseModelBsonParser.componentKey).get,
      name = doc.string("name").getOrElse(""),
      usage = doc.string("usage").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      requiredRate = safeNumGet("requiredRate", doc),
      maxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0))
    )
}
