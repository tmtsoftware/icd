package csw.services.icd.db.parser

import icd.web.shared.IcdModels.BaseModel
import reactivemongo.bson.BSONDocument

/**
 * Fake model class used to hold only the subsystem and component name
 */
object BaseModelBsonParser {

  val subsystemKey = "subsystem"
  val componentKey = "component"

  def apply(doc: BSONDocument): BaseModel =
    BaseModel(
      subsystem = doc.getAs[String](subsystemKey).get,
      component = doc.getAs[String](componentKey).get
    )
}
