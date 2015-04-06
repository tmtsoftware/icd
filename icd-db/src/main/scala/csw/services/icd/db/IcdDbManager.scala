package csw.services.icd.db

import java.util.Date

import com.mongodb.{WriteConcern, DBCollection, DBObject}
import com.mongodb.casbah.Imports._

/**
 * Keeps track of previous versions of ICDs.
 */
object IcdDbManager {
  // The version key inserted into all documents
  val versionKey = "_version"

  // The name of the sub-collection containing the previous versions or version information
  val versionColl = "v"
}

case class IcdDbManager(db: MongoDB, query: IcdDbQuery) {

  import IcdDbManager._
  import IcdDbQuery._

  /**
   * Ingests the given object into the database in the named collection,
   * creating a new one if it does not already exist.
   * @param name name of the collection to use
   * @param obj the object to insert
   */
  def ingest(name: String, obj: DBObject): Unit = {
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
    val currentVersion = coll.head.get(versionKey).asInstanceOf[Int]
    val v = coll.getCollection(versionColl)
    v.insert(coll.head, WriteConcern.SAFE)
    coll.remove(coll.head)
    obj.put(versionKey, currentVersion + 1)
    coll.insert(obj, WriteConcern.SAFE)
  }


  /**
   * Increments the version for the named ICD.
   * This creates a Mongo collection named $name.v that contains the ICD version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the ICD parts.
   *
   * @param name the root path name of the ICD
   */
  def newVersion(name: String, comment: String): Unit = {
    val collName = s"$name.$versionColl"

    // Get the paths of all the ICD parts
    val paths = db.collectionNames().filter(isStdSet).map(IcdPath).filter(_.icd == name).map(_.path)

    // Generate a list of maps with name and version each ICD part
    def getVersion(path: String): Int = db(path).head.get(versionKey).asInstanceOf[Int]
    val parts = paths.map(p => (p, getVersion(p))).toList.map(x => Map("name" -> x._1, "version" -> x._2).asDBObject)

    // Start with "1.0" as the ICD version, then increment the minor version automatically each time
    def incrMinorVersion(version: String): String = {
      val ar = version.split("\\.")
      val (maj, min) = version.splitAt(version.indexOf(".") + 1)
      val next = min.toInt + 1
      s"$maj$next"
    }

    val version = if (db.collectionExists(collName))
      incrMinorVersion(db(collName).find().sort("_id" -> -1).one().get("version").toString)
    else "1.0"

    val now = new Date()
    val user = System.getProperty("user.name")
    val obj = Map(
      "version" -> version,
      "user" -> user,
      "comment" -> comment,
      "date" -> now,
      "parts" -> parts).asDBObject
    db(collName).insert(obj, WriteConcern.SAFE)
  }

}
