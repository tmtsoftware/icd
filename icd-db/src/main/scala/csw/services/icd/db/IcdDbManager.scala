package csw.services.icd.db

import com.mongodb.{ WriteConcern, DBObject }
import com.mongodb.casbah.Imports._

/**
 * Manages ingesting objects into the database while keeping track of
 * previous versions.
 */
case class IcdDbManager(db: MongoDB, versionManager: IcdVersionManager) {
  import IcdVersionManager._

  /**
   * Ingests the given object into the database in the named collection,
   * creating a new one if it does not already exist.
   * @param name name of the collection to use
   * @param obj the object to insert
   */
  private[db] def ingest(name: String, obj: DBObject): Unit = {
    if (db.collectionExists(name))
      update(db(name), obj)
    else
      insert(db(name), obj)
  }

  // Inserts an new object in a collection
  private def insert(coll: MongoCollection, obj: DBObject): Unit = {
    obj.put(versionKey, 1)
    coll.insert(obj, WriteConcern.SAFE)
  }

  // Updates an object in an existing collection
  private def update(coll: MongoCollection, obj: DBObject): Unit = {
    val currentVersion = coll.head(versionKey).asInstanceOf[Int]
    obj.put(versionKey, currentVersion)
    coll.remove(coll.head)
    coll.insert(obj, WriteConcern.SAFE)
  }
}
