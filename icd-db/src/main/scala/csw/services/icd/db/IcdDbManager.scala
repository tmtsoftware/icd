package csw.services.icd.db

import play.api.libs.json.{JsNumber, JsObject}
import reactivemongo.play.json._
//import play.api.libs.json.Reads._
//import play.api.libs.json.Writes._
import reactivemongo.api.DefaultDB
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONNumberLike}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

/**
 * Manages ingesting objects into the database while keeping track of
 * previous versions.
 */
case class IcdDbManager(db: DefaultDB, versionManager: IcdVersionManager) {

  import IcdVersionManager._

  // XXX TODO FIXME
  private val timeout = 60.seconds

  /**
   * Ingests the given object into the database in the named collection,
   * creating a new one if it does not already exist.
   *
   * @param name name of the collection to use
   * @param obj  the object to insert
   */
  private[db] def ingest(name: String, obj: JsObject): Unit = {
    val collection = db.collection[BSONCollection](name)
    if (Await.result(db.collectionNames, timeout).contains(name))
      update(collection, obj)
    else
      insert(collection, obj)
  }

  // Inserts an new object in a collection
  private def insert(coll: BSONCollection, obj: JsObject): Unit = {
    Await.result(coll.insert.one(obj + (versionKey -> JsNumber(1))), timeout)
  }

  // Updates an object in an existing collection
  private def update(coll: BSONCollection, obj: JsObject): Unit = {
    // XXX TODO FIXME: Instead of delete and insert, do a mongodb update?
    val doc = Await.result(coll.find(BSONDocument(), None).one[BSONDocument], timeout).get
    val currentVersion = doc.getAs[BSONNumberLike](versionKey).get.toInt
    Await.result(coll.delete().one(BSONDocument(versionKey -> currentVersion)), timeout)
    Await.result(coll.insert.one(obj + (versionKey -> JsNumber(currentVersion))), timeout)
  }
}
