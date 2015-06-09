package csw.services.icd.db

import com.mongodb.{ WriteConcern, DBObject }
import com.mongodb.casbah.Imports._
import csw.services.icd.model.BaseModel
import gnieh.diffson.{ JsonPatch, JsonDiff }
import net.liftweb.json.JsonAST.{ JNothing, JObject, JField, JValue }
import net.liftweb.json.JsonParser
import org.joda.time.{ DateTimeZone, DateTime }

/**
 * Keeps track of previous versions of ICDs.
 */
object IcdDbManager {

  import com.mongodb.casbah.commons.conversions.scala._

  RegisterJodaTimeConversionHelpers()

  // The version key inserted into all documents
  val versionKey = "_version"

  // The id key inserted into all documents
  val idKey = "_id"

  // The name of the sub-collection containing the previous versions or version information
  val versionColl = "v"

  /**
   * Holds an ICD part (collection) path and it's version
   */
  case class PartInfo(path: String, version: Int)

  /**
   * Describes a version of an ICD
   * @param version the ICD version (major.minor)
   * @param user the user that created the version
   * @param comment a change comment
   * @param date the date of the change
   * @param parts names and versions of the ICD parts
   */
  case class VersionInfo(version: String, user: String, comment: String, date: DateTime, parts: List[PartInfo]) {
    // Gets the version of the part with the given path
    def getPartVersion(path: String): Option[Int] = {
      val list = for (part ← parts if part.path == path) yield part.version
      list.headOption
    }
  }

  object VersionInfo {
    def apply(obj: DBObject): VersionInfo =
      VersionInfo(
        version = obj("version").toString,
        user = obj("user").toString,
        comment = obj("comment").toString,
        date = obj("date").asInstanceOf[DateTime].withZone(DateTimeZone.UTC),
        parts = for (part ← obj("parts").asInstanceOf[BasicDBList].toList) yield {
          val partObj = part.asInstanceOf[DBObject]
          PartInfo(partObj("name").toString, partObj("version").asInstanceOf[Int])
        })
  }

  /**
   * Represents the difference between two versions of an ICD part in the db
   * (parts have names that end with "icd", "component", "publish", "subscribe", "command")
   * @param path the path to a part of the ICD (for example: "NFIRAOS.lgsWfs.publish")
   * @param patch an object describing the difference for the ICD part
   */
  case class VersionDiff(path: String, patch: JsonPatch)

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
    obj.put(versionKey, currentVersion + 1)
    obj.put(idKey, JNothing)
    diff(coll, obj) match {
      case None ⇒
        obj.remove(idKey)
      case Some(VersionDiff(path, patch)) ⇒
        obj.remove(idKey)
        val v = coll.getCollection(versionColl)
        v.insert(coll.head, WriteConcern.SAFE)
        coll.remove(coll.head)
        coll.insert(obj, WriteConcern.SAFE)
    }
  }

  // Name of version history collection for the given ICD root name
  private def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Returns the current version of the given ICD
   * @param name the root name of the ICD
   */
  def getCurrentIcdVersion(name: String): String =
    db(versionCollectionName(name)).find().sort(idKey -> -1).one().get("version").toString

  // Returns a list of all of the parts (collections) belonging to the named ICD
  private def getIcdPaths(name: String): List[String] =
    db.collectionNames().filter(isStdSet).map(IcdPath).filter(_.subsystem == name).map(_.path).toList

  /**
   * Increments the version for the named ICD.
   * This creates a Mongo collection named "name.v" that contains the ICD version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the ICD parts.
   *
   * @param name the name of ICD component or subsystem
   * @param comment change comment
   * @param majorVersion if true, increment the ICD's major version
   */
  def newVersion(name: String, comment: String, majorVersion: Boolean): Unit = {

    // XXX TODO: Only insert new version if there were changes!

    val collName = versionCollectionName(name)

    // Get the paths of all the ICD parts
    val paths = getIcdPaths(name)

    // Generate a list of maps with name and version each current ICD part (to store with this version)
    def getVersion(path: String): Int = {
      db(path).head(versionKey).asInstanceOf[Int]
    }
    val parts = paths.map(p ⇒ (p, getVersion(p))).map(x ⇒ Map("name" -> x._1, "version" -> x._2).asDBObject)

//    if (db.collectionExists(collName)) ... XXX TODO check if parts is different than what is in collname

    // Start with "1.0" as the ICD version, then increment the minor version automatically each time.
    // If the user requests a new major version, increment that and reset minor version to 0.
    def incrVersion(version: String): String = {
      val Array(maj, min) = version.split("\\.")
      if (majorVersion) s"${maj.toInt + 1}.0" else s"$maj.${min.toInt + 1}"
    }

    val version = if (db.collectionExists(collName))
      incrVersion(getCurrentIcdVersion(name))
    else "1.0"

    val now = new DateTime(DateTimeZone.UTC)
    val user = System.getProperty("user.name") // XXX TODO Which user name to use for web app? (Need user login...)
    val obj = Map(
      "version" -> version,
      "user" -> user,
      "comment" -> comment,
      "date" -> now,
      "parts" -> parts).asDBObject
    db(collName).insert(obj, WriteConcern.SAFE)
  }

  /**
   * Returns the list of unique component names found in the given list.
   * @param list list of ICD model files packaged as StdConfig object
   * @return the list of component names found
   */
  private def getComponentNames(list: List[StdConfig]): List[String] = {
    list.flatMap { stdConfig ⇒
      if (stdConfig.stdName.isSubsystemModel)
        None
      else
        Some(BaseModel(stdConfig.config).component)
    }.distinct
  }

  // XXX TODO move version related stuff to different class

  /**
   * Increments the version for the components in the list.
   * This creates a Mongo collection named "name.v" that contains the ICD version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the ICD parts.
   *
   * @param list list of standard ICD config files
   * @param comment change comment
   * @param majorVersion if true, increment the ICD's major version
   */
  def newVersion(list: List[StdConfig], comment: String, majorVersion: Boolean): Unit = {
    getComponentNames(list).foreach(newVersion(_, comment, majorVersion))
  }

  /**
   * Returns a list of information about the versions of the ICD
   * @param name the root name of the ICD
   */
  def getIcdVersions(name: String): List[VersionInfo] = {
    val collName = versionCollectionName(name)
    val result = for (obj ← db(collName).find().sort(idKey -> -1)) yield VersionInfo(obj)
    result.toList
  }

  /**
   * Returns information about the given version of the given ICD
   * @param name the root name of the ICD
   * @param version the version of interest
   */
  def getIcdVersion(name: String, version: String): Option[VersionInfo] = {
    val collName = versionCollectionName(name)
    db(collName).findOne("version" -> version).map(VersionInfo(_))
  }

  /**
   * Compares all of the named ICD parts and returns a list of patches describing any differences.
   * @param name the root ICD name
   * @param v1 the first version to compare
   * @param v2 the second version to compare
   * @return a list of diffs, one for each ICD part
   */
  def diff(name: String, v1: String, v2: String): List[VersionDiff] = {
    val v1Info = getIcdVersion(name, v1)
    val v2Info = getIcdVersion(name, v2)
    if (v1Info.isEmpty || v2Info.isEmpty) Nil
    else {
      val result = for {
        p1 ← v1Info.get.parts
        p2 ← v2Info.get.parts if p1.path == p2.path
      } yield diffPart(p1.path, p1.version, p2.version)
      result.flatten
    }
  }

  // Parse string to JSON and Remove _id and _version keys for comparing, since they change each time
  private def parseNoVersionOrId(json: String): JValue = {
    JsonParser.parse(json).replace(idKey :: Nil, JNothing).replace(versionKey :: Nil, JNothing)
  }

  // Returns the JSON for the given version of the collection path
  private def getJson(path: String, version: Int): JValue = {
    val coll = db(path)
    val currentVersion = coll.head(versionKey).asInstanceOf[Int]
    val v = coll.getCollection(versionColl)
    val jsonStr = if (version == currentVersion) {
      coll.head.toString
    } else {
      v.find(versionKey -> version).one().toString
    }
    parseNoVersionOrId(jsonStr)
  }

  // Returns the diff of the given versions of the given collection path, if they are different
  private def diffPart(path: String, v1: Int, v2: Int): Option[VersionDiff] = {
    diffJson(path, getJson(path, v1), getJson(path, v2))
  }

  // Compares the two json values, returning None if equal, otherwise some VersionDiff
  private def diffJson(path: String, json1: JValue, json2: JValue): Option[VersionDiff] = {
    if (json1 == json2) None else Some(VersionDiff(path, JsonDiff.diff(json1, json2)))
  }

  // Compares the given object with the current version in the collection
  private def diff(coll: MongoCollection, obj: DBObject): Option[VersionDiff] = {
    val json1 = parseNoVersionOrId(coll.head.toString)
    val json2 = parseNoVersionOrId(obj.toString)
    diffJson(coll.name, json1, json2)
  }
}
