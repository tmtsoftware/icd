package csw.services.icd.db

import java.util.Date

import com.mongodb.{WriteConcern, DBCollection, DBObject}
import com.mongodb.casbah.Imports._
import org.joda.time.{DateTimeZone, DateTime}

/**
 * Keeps track of previous versions of ICDs.
 */
object IcdDbManager {
  import com.mongodb.casbah.commons.conversions.scala._
  RegisterJodaTimeConversionHelpers()

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
  private [db] def ingest(name: String, obj: DBObject): Unit = {
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

  // Name of version history collection for the given ICD root name
  private def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Increments the version for the named ICD.
   * This creates a Mongo collection named $name.v that contains the ICD version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the ICD parts.
   *
   * @param name the root path name of the ICD
   * @param comment change comment
   * @param majorVersion if true, increment the ICD's major version
   */
  private [db] def newVersion(name: String, comment: String = "", majorVersion: Boolean = false): Unit = {
    val collName = versionCollectionName(name)

    // Get the paths of all the ICD parts
    val paths = db.collectionNames().filter(isStdSet).map(IcdPath).filter(_.icd == name).map(_.path)

    // Generate a list of maps with name and version each ICD part
    def getVersion(path: String): Int = db(path).head.get(versionKey).asInstanceOf[Int]
    val parts = paths.map(p => (p, getVersion(p))).toList.map(x => Map("name" -> x._1, "version" -> x._2).asDBObject)

    // Start with "1.0" as the ICD version, then increment the minor version automatically each time.
    // If the user requests a new major version, increment that and reset minor version to 0.
    def incrVersion(version: String): String = {
      val Array(maj,min) = version.split("\\.")
      if (majorVersion) s"${maj.toInt+1}.0" else s"$maj.${min.toInt+1}"
    }

    val version = if (db.collectionExists(collName))
      incrVersion(db(collName).find().sort("_id" -> -1).one().get("version").toString)
    else "1.0"

    val now = new DateTime(DateTimeZone.UTC)
    val user = System.getProperty("user.name") // XXX TODO Which user name to use for web app?
    val obj = Map(
      "version" -> version,
      "user" -> user,
      "comment" -> comment,
      "date" -> now,
      "parts" -> parts).asDBObject
    db(collName).insert(obj, WriteConcern.SAFE)
  }


  /**
   * Describes a version of an ICD
   * @param version the ICD version (major.minor)
   * @param user the user that created the version
   * @param comment a change comment
   * @param date the date of the change
   */
  case class VersionInfo(version: String, user: String, comment: String, date: DateTime)

  /**
   * Returns a list of information about the versions of the ICD
   * @param name the root name of the ICD
   */
  def getIcdVersions(name: String): List[VersionInfo] = {
    val collName = versionCollectionName(name)
    val result = for(obj <- db(collName).find().sort("_id" -> -1))
      yield VersionInfo(
        obj.get("version").toString,
        obj.get("user").toString,
        obj.get("comment").toString,
        obj.get("date").asInstanceOf[DateTime].withZone(DateTimeZone.UTC)
      )
    result.toList
  }

}
