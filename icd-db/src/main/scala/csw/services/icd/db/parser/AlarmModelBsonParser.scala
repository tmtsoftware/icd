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
      name = doc.string("name").get,
      description = HtmlMarkup.gfmToHtml(doc.string("description").get, maybePdfOptions),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      severityLevels = doc.getAsOpt[Array[String]]("severityLevels").map(_.toList).getOrElse(List("Warning", "Major", "Critical")),
      location = doc.string("location").getOrElse(""),
      alarmType = doc.string("alarmType").getOrElse(""),
      probableCause = HtmlMarkup.gfmToHtml(doc.string("probableCause").getOrElse(""), maybePdfOptions),
      operatorResponse = HtmlMarkup.gfmToHtml(doc.string("operatorResponse").getOrElse(""), maybePdfOptions),
      autoAck = doc.booleanLike("autoAck").getOrElse(false),
      latched = doc.booleanLike("latched").getOrElse(false)
    )
  }
}
