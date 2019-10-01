package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmModel
import reactivemongo.bson.BSONDocument

/**
 * See resources/alarm-schema.conf
 */
object AlarmModelBsonParser {


  // Note: Some required args treated as optional for backward compatibility
  def apply(doc: BSONDocument): AlarmModel =
    AlarmModel(
      name = doc.getAs[String]("name").get,
      description = HtmlMarkup.gfmToHtml(doc.getAs[String]("description").get),
      requirements = doc.getAs[Array[String]]("requirements").map(_.toList).getOrElse(Nil),
      severityLevels = doc.getAs[Array[String]]("severityLevels").map(_.toList).getOrElse(List("Warning", "Major", "Critical")),
      archive = doc.getAs[Boolean]("archive").getOrElse(true),
      location = doc.getAs[String]("location").getOrElse(""),
      alarmType = doc.getAs[String]("alarmType").getOrElse(""),
      probableCause = HtmlMarkup.gfmToHtml(doc.getAs[String]("probableCause").getOrElse("")),
      operatorResponse = HtmlMarkup.gfmToHtml(doc.getAs[String]("operatorResponse").getOrElse("")),
      acknowledge = doc.getAs[Boolean]("acknowledge").getOrElse(false),
      latched = doc.getAs[Boolean]("latched").getOrElse(false)
    )
}
