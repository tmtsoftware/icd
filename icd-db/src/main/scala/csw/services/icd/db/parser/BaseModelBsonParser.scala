package csw.services.icd.db.parser

import icd.web.shared.IcdModels.BaseModel
import reactivemongo.api.bson.*

/**
 * Fake model class used to hold only the subsystem and component name
 */
object BaseModelBsonParser {

  val subsystemKey = "subsystem"
  val componentKey = "component"

  def apply(doc: BSONDocument): BaseModel =
    BaseModel(
      subsystem = doc.getAsOpt[String](subsystemKey).get,
      component = doc.getAsOpt[String](componentKey).get
    )
}
