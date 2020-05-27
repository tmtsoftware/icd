package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmModel
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson._

/**
 * See resources/alarm-schema.conf
 */
object AlarmModelBsonParser {


  // Note: Some required args treated as optional for backward compatibility
  def apply(doc: BSONDocument): AlarmModel = {
    AlarmModel(
      name = doc.getAsOpt[String]("name").get,
      description = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("description").get),
      requirements = doc.getAsOpt[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      severityLevels = doc.getAsOpt[Array[String]]("severityLevels").map(_.toList).getOrElse(List("Warning", "Major", "Critical")),
      location = doc.getAsOpt[String]("location").getOrElse(""),
      alarmType = doc.getAsOpt[String]("alarmType").getOrElse(""),
      probableCause = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("probableCause").getOrElse("")),
      operatorResponse = HtmlMarkup.gfmToHtml(doc.getAsOpt[String]("operatorResponse").getOrElse("")),
      autoAck = doc.getAsOpt[Boolean]("autoAck").getOrElse(false),
      latched = doc.getAsOpt[Boolean]("latched").getOrElse(false)
    )
  }
}
