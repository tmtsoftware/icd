package csw.services.icd.db

import reactivemongo.bson.{BSONDocument, BSONNumberLike, BSONString}

// Safely get a number from MongoDB, or return 0.0 if not found.
package object parser {
  def safeNumGet(field: String, doc: BSONDocument): Double = {
    doc.getAs[BSONNumberLike](field).map(_.toDouble).getOrElse(0.0)
  }
}
