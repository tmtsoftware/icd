package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.*

/**
 * See resources/<version>/alarm-schema.conf
 */
object AlarmModelBsonParser {


  // Note: Some required args treated as optional for backward compatibility
  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): AlarmModel = {
    AlarmModel(
      name = doc.getAsOpt[String]("name").get,
      description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get, maybePdfOptions),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      severityLevels = doc.getAsOpt[Array[String]]("severityLevels").map(_.toList).getOrElse(List("Warning", "Major", "Critical")),
      location = doc.getAsOpt[String]("location").getOrElse(""),
      alarmType = doc.getAsOpt[String]("alarmType").getOrElse(""),
      probableCause = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("probableCause").getOrElse(""), maybePdfOptions),
      operatorResponse = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("operatorResponse").getOrElse(""), maybePdfOptions),
      autoAck = doc.getAsOpt[Boolean]("autoAck").getOrElse(false),
      latched = doc.getAsOpt[Boolean]("latched").getOrElse(false)
    )
  }
}
