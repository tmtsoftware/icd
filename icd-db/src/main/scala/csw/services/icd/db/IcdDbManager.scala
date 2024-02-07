package csw.services.icd.db

import play.api.libs.json.*
import csw.services.icd.*
import reactivemongo.api.DB
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.bson.*

import reactivemongo.play.json.compat.*
import bson2json.*
import lax.*
import json2bson.*

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages ingesting objects into the database while keeping track of
 * previous versions.
 */
case class IcdDbManager(db: DB, versionManager: IcdVersionManager) {

  import IcdVersionManager.*

  /**
   * Ingests the given object into the database in the named collection,
   * creating a new one if it does not already exist.
   *
   * @param name name of the collection to use
   * @param tmpName temp name of the collection to use during ingest
   * @param obj  the object to insert
   */
  private[db] def ingest(name: String, tmpName: String, obj: JsObject): Unit = {
    val tmpCollection = db.collection[BSONCollection](tmpName)
    if (db.collectionNames.await.contains(name)) {
      val collection = db.collection[BSONCollection](name)
      update(collection, tmpCollection, obj)
    } else {
      insert(tmpCollection, obj)
    }
  }

  // Inserts an new object in a collection, with _version = 1
  private def insert(coll: BSONCollection, obj: JsObject): Unit = {
    coll.insert.one(obj + (versionKey -> JsNumber(1))).await
  }

  // Updates an object in an existing collection, keeping the same _version number
  private def update(coll: BSONCollection, tmpColl: BSONCollection, obj: JsObject): Unit = {
    val doc = coll.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await.get
    val currentVersion = doc.getAsOpt[BSONInteger](versionKey).get.toInt.get
    tmpColl.insert.one(obj + (versionKey -> JsNumber(currentVersion))).await
  }
}
