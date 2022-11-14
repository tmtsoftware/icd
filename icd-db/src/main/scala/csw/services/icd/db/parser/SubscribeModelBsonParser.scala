package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{SubscribeModel, SubscribeModelInfo}
import icd.web.shared.PdfOptions
import reactivemongo.api.bson._

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
          for (subDoc <- subscribeDoc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil))
            yield SubscribeInfoBsonParser(subDoc, maybePdfOptions)

        // For backward compatibility
        val oldEvents = getItems("telemetry") ++ getItems("eventStreams")

        SubscribeModel(
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          description = subscribeDoc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
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
      subsystem = doc.getAsOpt[String]("subsystem").getOrElse(""),
      component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
      name = doc.getAsOpt[String]("name").getOrElse(""),
      usage = doc.getAsOpt[String]("usage").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
      requiredRate = safeNumGet("requiredRate", doc),
      maxRate = doc.getAsOpt[BSONNumberLike]("maxRate").map(_.toDouble.getOrElse(1.0))
    )
}
