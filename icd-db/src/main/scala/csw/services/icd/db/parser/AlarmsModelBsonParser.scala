package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AlarmsModel
import icd.web.shared.PdfOptions
import reactivemongo.api.bson.*

/**
 * See resources/<version>/alarms-schema.conf
 */
object AlarmsModelBsonParser {

  def apply(doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[AlarmsModel] = {
    if (doc.isEmpty) None else Some {
      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- doc.children(name)) yield f(subDoc)

      AlarmsModel(
        subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
        component = doc.string(BaseModelBsonParser.componentKey).get,
        alarmList = getItems("alarms", AlarmModelBsonParser(_, maybePdfOptions))
      )
    }
  }
}
