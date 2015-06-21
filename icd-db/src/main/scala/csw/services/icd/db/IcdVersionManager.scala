package csw.services.icd.db

import com.mongodb.{ WriteConcern, DBObject }
import com.mongodb.casbah.Imports._
import com.typesafe.config.Config
import gnieh.diffson.{ JsonDiff, JsonPatch }
import net.liftweb.json.JsonAST.{ JNothing, JValue }
import net.liftweb.json.JsonParser
import org.joda.time.{ DateTimeZone, DateTime }
import csw.services.icd.model._

/**
 * Manages Subsystem and component versioning in the database.
 * Previous versions of a MongoDB collection coll are stored in coll.v.
 * In addition, a top level collection keeps track of which versions of each collection belong
 * to a given "top level" version for the subsystem (or component).
 */
object IcdVersionManager {

  import com.mongodb.casbah.commons.conversions.scala._

  RegisterJodaTimeConversionHelpers()

  // The id key inserted into all documents
  val idKey = "_id"

  // The version key inserted into all documents
  val versionKey = "_version"

  // The version key used for top level subsystems or components
  val versionStrKey = "version"

  // The name of the sub-collection containing the previous versions or version information
  val versionColl = "v"

  /**
   * Holds a collection path for a component or subsystem and it's version
   */
  case class PartInfo(path: String, version: Int)

  // Name of version history collection for the given subsystem or component
  def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Describes a version of a subsystem or component
   * @param versionOpt the subsystem or component version (major.minor), if published
   * @param user the user that created the version
   * @param comment a change comment
   * @param date the date of the change
   * @param parts names and versions of the subsystem or component parts
   */
  case class VersionInfo(versionOpt: Option[String], user: String, comment: String, date: DateTime, parts: List[PartInfo]) {
    // Gets the version of the part with the given path
    def getPartVersion(path: String): Option[Int] = {
      val list = for (part ← parts if part.path == path) yield part.version
      list.headOption
    }
  }

  object VersionInfo {
    // Creates a VersionInfo instance from an object in the database
    def apply(obj: DBObject): VersionInfo =
      VersionInfo(
        versionOpt = Some(obj(versionStrKey).toString),
        user = obj("user").toString,
        comment = obj("comment").toString,
        date = obj("date").asInstanceOf[DateTime].withZone(DateTimeZone.UTC),
        parts = for (part ← obj("parts").asInstanceOf[BasicDBList].toList) yield {
          val partObj = part.asInstanceOf[DBObject]
          PartInfo(partObj("name").toString, partObj(versionStrKey).asInstanceOf[Int])
        })
  }

  /**
   * Represents the difference between two versions of an subsystem or component part in the db
   * (parts have names that end with "icd", "component", "publish", "subscribe", "command")
   * @param path the path to a part of the subsystem or component (for example: "NFIRAOS.lgsWfs.publish")
   * @param patch an object describing the difference for the subsystem or component part
   */
  case class VersionDiff(path: String, patch: JsonPatch)

}

case class IcdVersionManager(db: MongoDB) {

  import IcdVersionManager._
  import IcdDbQuery._

  /**
   * Increments the version for the named subsystem or component.
   * This creates a Mongo collection named "name.v" that contains the subsystem or component version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the subsystem or component parts.
   *
   * @param subsystem the subsystem
   * @param compNameOpt if defined, publish a new version of the component, otherwise the subsystem
   * @param versions list of (name, version) pairs for the collections belonging to the subsystem or component
   * @param comment change comment
   * @param majorVersion if true, increment the subsystem or component's major version
   */
  private def newVersion(subsystem: String, compNameOpt: Option[String], versions: List[(String, Int)],
                         comment: String, majorVersion: Boolean): Unit = {

    println(s"XXX newVersion $subsystem $compNameOpt versions=$versions")
    // Start with "1.0" as the subsystem or component version, then increment the minor version automatically each time.
    // If the user requests a new major version, increment that and reset minor version to 0.
    def incrVersion(versionOpt: Option[String]): String = {
      versionOpt match {
        case Some(v) ⇒
          val Array(maj, min) = v.split("\\.")
          if (majorVersion) s"${maj.toInt + 1}.0" else s"$maj.${min.toInt + 1}"
        case None ⇒ "1.0"
      }
    }

    val parts = versions.map(v ⇒ Map("name" -> v._1, versionStrKey -> v._2).asDBObject)
    val version = incrVersion(getLatestPublishedVersion(subsystem, compNameOpt))
    val now = new DateTime(DateTimeZone.UTC)
    val user = System.getProperty("user.name") // XXX TODO Which user name to use for web app? (Need user login...)
    val obj = Map(
      versionStrKey -> version,
      "user" -> user,
      "comment" -> comment,
      "date" -> now,
      "parts" -> parts).asDBObject
    val path = compNameOpt.fold(subsystem)(compName ⇒ s"$subsystem.$compName")
    db(versionCollectionName(path)).insert(obj, WriteConcern.SAFE)
  }

  //  /**
  //   * Increments the version for the components in the list.
  //   * This creates a Mongo collection named "name.v" that contains the subsystem or component version (starting with "1.0"),
  //   * the user and date as well as a list of the names and versions of each of the subsystem or component parts.
  //   *
  //   * @param list list of standard subsystem or component config files
  //   * @param comment change comment
  //   * @param majorVersion if true, increment the subsystem or component's major version
  //   */
  //  def newVersion(list: List[StdConfig], comment: String, majorVersion: Boolean): Unit = {
  //    getComponentNames(list).foreach(newVersion(_, comment, majorVersion))
  //  }

  //  /**
  //   * Returns the list of unique component names found in the given list.
  //   * @param list list of subsystem or component model files packaged as StdConfig object
  //   * @return the list of component names found
  //   */
  //  private def getComponentNames(list: List[StdConfig]): List[String] = {
  //    list.flatMap { stdConfig ⇒
  //      if (stdConfig.stdName.isSubsystemModel)
  //        None
  //      else
  //        Some(BaseModel(stdConfig.config).component)
  //    }.distinct
  //  }

  /**
   * Returns a list of information about the versions of the subsystem
   * @param subsystem the name of the subsystem
   */
  def getVersions(subsystem: String): List[VersionInfo] = {
    val current = getVersion(subsystem, None, None).toList
    val collName = versionCollectionName(subsystem)
    if (db.collectionExists(collName)) {
      val published = for (obj ← db(collName).find().sort(idKey -> -1)) yield VersionInfo(obj)
      current ::: published.toList
    } else current
  }

  /**
   * Returns a list of published version names of the subsystem or component
   * @param path the name of the subsystem (or subsystem.component)
   */
  def getVersionNames(path: String): List[String] = {
    val collName = versionCollectionName(path)
    if (db.collectionExists(collName)) {
      val result = for (obj ← db(collName).find().sort(idKey -> -1)) yield obj(versionStrKey).toString
      result.toList
    } else Nil
  }

  /**
   * Returns information about the given version of the given subsystem or component
   * @param subsystem the name of the subsystem (XXX TODO? or subsystem.component)
   * @param versionOpt the version of interest (None for the current, unpublished version)
   * @param compNameOpt if defined, return the models for the component, otherwise for the subsystem
   */
  def getVersion(subsystem: String, versionOpt: Option[String], compNameOpt: Option[String]): Option[VersionInfo] = {
    val path = compNameOpt.fold(subsystem)(compName ⇒ s"$subsystem.$compName")
    println(s"XXX getVersion $subsystem $versionOpt $compNameOpt path = $path")
    versionOpt match {
      case Some(version) ⇒ // published version
        val collName = versionCollectionName(path)
        if (db.collectionExists(collName)) {
          db(collName).findOne(versionStrKey -> version).map(VersionInfo(_))
        } else {
          println(s"XXX collection $collName does not exist")
          None // not found
        }
      case None ⇒ // current, unpublished version
        def getPartVersion(path: String): Int = db(path).head(versionKey).asInstanceOf[Int]
        def filter(p: IcdPath) = p.subsystem == subsystem && compNameOpt.fold(true)(_ ⇒ p.component == path)
        val paths = db.collectionNames().filter(isStdSet).map(IcdPath).filter(filter).map(_.path).toList
        val now = new DateTime(DateTimeZone.UTC)
        val user = System.getProperty("user.name")
        val comment = "Working version, unpublished"
        val parts = paths.map(p ⇒ (p, getPartVersion(p))).map(x ⇒ PartInfo(x._1, x._2))
        println(s"XXX path = $path, paths = $paths, parts = $parts")
        Some(VersionInfo(None, user, comment, now, parts))
    }
  }

  /**
   * Returns the version name of the latest, published version of the given subsystem or component, if found
   * @param subsystem the name of the subsystem
   * @param compNameOpt if defined, the name of the component
   */
  def getLatestPublishedVersion(subsystem: String, compNameOpt: Option[String]): Option[String] = {
    val path = compNameOpt.fold(subsystem)(compName ⇒ s"$subsystem.$compName")
    val collName = versionCollectionName(path)
    if (db.collectionExists(collName))
      Some(db(collName).find().sort(idKey -> -1).one().get(versionStrKey).toString)
    else None
  }

  /**
   * Compares all of the named subsystem or component parts and returns a list of patches describing any differences.
   * @param name the root subsystem or component name
   * @param v1 the first version to compare (None for the current, unpublished version)
   * @param v2 the second version to compare (None for the current, unpublished version)
   * @return a list of diffs, one for each subsystem or component part
   */
  def diff(name: String, v1: Option[String], v2: Option[String]): List[VersionDiff] = {
    val v1Info = getVersion(name, v1, None)
    val v2Info = getVersion(name, v2, None)
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

  // Returns the contents of the given version of the collection path
  private def getVersionOf(path: String, version: Int): String = {
    val coll = db(path)
    val currentVersion = coll.head(versionKey).asInstanceOf[Int]
    val v = coll.getCollection(versionColl)
    if (version == currentVersion) {
      coll.head.toString
    } else {
      v.find(versionKey -> version).one().toString
    }
  }

  // Returns the JSON for the given version of the collection path
  private def getJson(path: String, version: Int): JValue = {
    parseNoVersionOrId(getVersionOf(path, version))
  }

  // Returns the diff of the given versions of the given collection path, if they are different
  private def diffPart(path: String, v1: Int, v2: Int): Option[VersionDiff] = {
    diffJson(path, getJson(path, v1), getJson(path, v2))
  }

  // Compares the two json values, returning None if equal, otherwise some VersionDiff
  private def diffJson(path: String, json1: JValue, json2: JValue): Option[VersionDiff] = {
    if (json1 == json2) None else Some(VersionDiff(path, JsonDiff.diff(json1, json2)))
  }

  // Compares the given object with the current (head) version in the collection
  // (ignoring version and id values)
  def diff(coll: MongoCollection, obj: DBObject): Option[VersionDiff] = {
    val json1 = parseNoVersionOrId(coll.head.toString)
    val json2 = parseNoVersionOrId(obj.toString)
    diffJson(coll.name, json1, json2)
  }

  /**
   * Returns a list of all the component names in the DB belonging to the given subsystem version
   */
  def getComponentNames(subsystem: String, versionOpt: Option[String]): List[String] = {
    getVersion(subsystem, versionOpt, None) match {
      case Some(versionInfo) ⇒
        versionInfo.parts.map(_.path)
          .map(IcdPath)
          .filter(p ⇒ p.parts.length == 3)
          .map(_.parts.tail.head)
          .distinct.
          sorted
      case None ⇒ Nil
    }
  }

  // Returns a list of IcdEntry objects for the given parts (one part for each originally ingested file)
  private def getEntries(parts: List[PartInfo]): List[IcdEntry] = {
    val paths = parts.map(_.path).map(IcdPath)
    val compMap = paths.map(p ⇒ (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key ⇒ getEntry(key, compMap(key))).toList
    entries.sortBy(entry ⇒ (IcdPath(entry.name).parts.length, entry.name))
  }

  /**
   * Returns a list of models for the given subsystem version or component,
   * based on the data in the database.
   * The list includes the model for the subsystem, followed
   * by any models for components that were defined in subdirectories
   * in the original files that were ingested into the database
   * (In this case the definitions are stored in sub-collections in the DB).
   *
   * @param subsystem the subsystem containing the component
   * @param versionOpt the subsystem version (None for the current, unpublished version)
   * @param compNameOpt if defined, return the models for the component, otherwise for the subsystem
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  def getModels(subsystem: String, versionOpt: Option[String], compNameOpt: Option[String]): List[IcdModels] = {

    // Holds all the model classes associated with a single ICD entry.
    case class Models(versionMap: Map[String, Int], entry: IcdEntry) extends IcdModels {
      println(s"XXX Models: versionMap = $versionMap, entry = $entry")

      // Parses the data from collection s (or an older version of it) and returns a Config object for it
      private def parse(s: String): Config = getConfig(getVersionOf(s, versionMap(s)))

      override val subsystemModel = entry.subsystem.map(s ⇒ SubsystemModel(parse(s)))
      override val publishModel = entry.publish.map(s ⇒ PublishModel(parse(s)))
      override val subscribeModel = entry.subscribe.map(s ⇒ SubscribeModel(parse(s)))
      override val commandModel = entry.command.map(s ⇒ CommandModel(parse(s)))
      override val componentModel = entry.component.map(s ⇒ ComponentModel(parse(s)))
    }

    getVersion(subsystem, versionOpt, compNameOpt) match {
      case Some(versionInfo) ⇒
        val versionMap = versionInfo.parts.map(v ⇒ v.path -> v.version).toMap
        val entries = getEntries(versionInfo.parts)
        for (entry ← entries) yield Models(versionMap, entry)
      case None ⇒ Nil
    }
  }

  /**
   * Publishes the given subsystem
   * @param subsystem the name of subsystem
   * @param comment change comment
   * @param majorVersion if true, increment the subsystem's major version
   */
  def publishApi(subsystem: String, comment: String, majorVersion: Boolean = false): Unit = {
    // Save any of the subsystem's collections that changed
    val icdPaths = db.collectionNames().filter(isStdSet).map(IcdPath).filter(_.subsystem == subsystem)
    val paths = icdPaths.map(_.path).toList
    val versions = for (path ← paths) yield {
      val coll = db(path)
      val obj = coll.head
      val versionCollName = versionCollectionName(path)
      val version = obj(versionKey).asInstanceOf[Int]
      if (!db.collectionExists(versionCollName) || diff(db(versionCollName), obj).isDefined) {
        val v = db(versionCollName)
        v.insert(obj, WriteConcern.SAFE)
        obj.put(versionKey, version + 1)
      }
      (path, version)
    }

    newVersion(subsystem, None, versions, comment, majorVersion)

    // XXX needed?
    getComponentNames(subsystem, None).foreach { name ⇒
      val prefix = s"$subsystem.$name."
      val compVersions = versions.filter(p ⇒ p._1.startsWith(prefix))
      newVersion(subsystem, Some(name), compVersions, comment, majorVersion)
    }
  }

  /**
   * Publishes an ICD from the given version of the given subsystem to the target subsystem and version
   */
  def publishIcd(subsystem: String, version: String, target: String, targetVersion: String, comment: String): Unit = {

  }
}
