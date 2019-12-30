package csw.services.icd.db

import reactivemongo.api.bson.{BSONDocument, BSONNumberLike}


// Safely get a number from MongoDB, or return 0.0 if not found.
package object parser {
  def safeNumGet(field: String, doc: BSONDocument): Double = {
    doc.getAsOpt[BSONNumberLike](field).map(_.toDouble.getOrElse(0.0)).getOrElse(0.0)
  }
}
