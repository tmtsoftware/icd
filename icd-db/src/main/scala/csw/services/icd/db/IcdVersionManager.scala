package csw.services.icd.db

import com.mongodb.{DBObject, WriteConcern}
import com.mongodb.casbah.Imports._
import com.typesafe.config.{Config, ConfigFactory}
import icd.web.shared.IcdModels.SubsystemModel
import icd.web.shared.{IcdModels, IcdVersion, IcdVersionInfo}
import org.joda.time.{DateTime, DateTimeZone}
import csw.services.icd.model._
import gnieh.diffson.{JsonDiff, JsonPatch}
import spray.json.{JsValue, JsonParser}

/**
 * Manages Subsystem and component versioning in the database.
 * Previous versions of a MongoDB collection coll are stored in coll.v.
 * In addition, a top level collection keeps track of which versions of each collection belong
 * to a given "top level" version for the subsystem (or component).
 */
object IcdVersionManager {

  import com.mongodb.casbah.commons.conversions.scala._

  RegisterJodaTimeConversionHelpers()

  /** The id key inserted into all documents */
  val idKey = "_id"

  /** The version key inserted into all documents */
  val versionKey = "_version"

  /** The version key used for top level subsystems or components */
  val versionStrKey = "version"

  /** The name of the sub-collection containing the previous versions or version information */
  val versionColl = "v"

  /** Name of collection with information about published ICDs */
  val icdCollName = "icds"

  val subsystemKey = "subsystem"
  val subsystemVersionKey = "subsystemVersion"
  val targetKey = "target"
  val targetVersionKey = "targetVersion"
  val userKey = "user"
  val dateKey = "date"
  val commentKey = "comment"

  /**
   * A list of all known TMT subsystems (read from the same resources file used in validating the ICDs)
   */
  val allSubsystems: Set[String] = {
    import scala.collection.JavaConverters._
    val config = ConfigFactory.parseResources("subsystem.conf")
    config.getStringList("enum").asScala.toSet
  }

  /**
   * Holds a collection path for a component or subsystem and it's version
   */
  case class PartInfo(path: String, version: Int)

  // Name of version history collection for the given subsystem or component
  def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Describes a version of a subsystem or component
   *
   * @param versionOpt the subsystem or component version (major.minor), if published
   * @param user the user that created the version
   * @param comment a change comment
   * @param date the date of the change
   * @param parts names and versions of the subsystem or component parts
   */
  case class VersionInfo(versionOpt: Option[String], user: String, comment: String, date: DateTime, parts: List[PartInfo]) {
    // Gets the version of the part with the given path
    def getPartVersion(path: String): Option[Int] = {
      val list = for (part <- parts if part.path == path) yield part.version
      list.headOption
    }
  }

  // XXX TODO: Use automatic JSON conversion for reading and writing this?
  object VersionInfo {
    // Creates a VersionInfo instance from an object in the database
    def apply(obj: DBObject): VersionInfo =
      VersionInfo(
        versionOpt = Some(obj(versionStrKey).toString),
        user = obj(userKey).toString,
        comment = obj(commentKey).toString,
        date = obj(dateKey).asInstanceOf[DateTime].withZone(DateTimeZone.UTC),
        parts = for (part <- obj("parts").asInstanceOf[BasicDBList].toList) yield {
          val partObj = part.asInstanceOf[DBObject]
          PartInfo(partObj("name").toString, partObj(versionStrKey).asInstanceOf[Int])
        }
      )
  }

  /**
   * Represents the difference between two versions of an subsystem or component part in the db
   * (parts have names that end with "icd", "component", "publish", "subscribe", "command")
   *
   * @param path the path to a part of the subsystem or component (for example: "NFIRAOS.lgsWfs.publish")
   * @param patch an object describing the difference for the subsystem or component part
   */
  case class VersionDiff(path: String, patch: JsonPatch)

  /**
   * An ICD from subsystem to target subsystem
   */
  case class IcdName(subsystem: String, target: String)

  // Define sorting for IcdName
  object IcdName {
    implicit def orderingByName[A <: IcdName]: Ordering[A] = Ordering.by(e => (e.subsystem, e.target))
  }

  /**
   * Wraps a subsystem name and optional version
   */
  case class SubsystemAndVersion(subsystem: String, versionOpt: Option[String]) extends Ordered[SubsystemAndVersion] {
    if (!allSubsystems.contains(subsystem)) {
      throw new IllegalArgumentException(s"Unknown subsystem: $subsystem")
    }

    versionOpt.foreach(SubsystemAndVersion.checkVersion)

    override def toString: String = versionOpt match {
      case Some(v) => s"$subsystem-$v"
      case None    => subsystem
    }

    // Used to sort subsystems alphabetically, to avoid duplicates, since A->B should be the same as B->A
    override def compare(that: SubsystemAndVersion): Int = {
      subsystem.compare(that.subsystem)
    }
  }

  object SubsystemAndVersion {
    /**
     * Extracts the subsystem and optional version, if defined
     * @param s a string containing the subsystem, possibly followed by a ':' and the version
     */
    def apply(s: String): SubsystemAndVersion = {
      if (s.contains(':')) {
        val ar = s.split(':')
        SubsystemAndVersion(ar(0), Some(ar(1)))
      } else SubsystemAndVersion(s, None)
    }

    /**
     * Validates the format of the given version string
     */
    def checkVersion(v: String): Unit = {
      if (!v.matches("\\d+\\.\\d+")) throw new IllegalArgumentException(s"Invalid subsystem version: $v")
    }
  }

  // Start with "1.0" as the subsystem or component version, then increment the minor version automatically each time.
  // If the user requests a new major version, increment that and reset minor version to 0.
  def incrVersion(versionOpt: Option[String], majorVersion: Boolean): String = {
    versionOpt match {
      case Some(v) =>
        val Array(maj, min) = v.split("\\.")
        if (majorVersion) s"${maj.toInt + 1}.0" else s"$maj.${min.toInt + 1}"
      case None => "1.0"
    }
  }
}

/**
 * Provides access to current and previous versions of ICD collections.
 *
 * @param db the MongoDB handle
 * @param query may be used to share caching of collection names (see CachedIcdDbQuery)
 */
case class IcdVersionManager(db: MongoDB, query: IcdDbQuery) {

  import IcdVersionManager._
  import IcdDbQuery._

  // Performance can be improved by caching these values in some cases (redefine in a subclass)
  private[db] def collectionExists(name: String): Boolean = query.collectionExists(name)

  private[db] def getCollectionNames: Set[String] = query.getCollectionNames

  /**
   * Increments the version for the named subsystem or component.
   * This creates a Mongo collection named "name.v" that contains the subsystem or component version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the subsystem or component parts.
   *
   * @param collectionNames list of collection names (for better performance)
   * @param subsystem the subsystem
   * @param versionOpt optional version string in the form "1.0" (used when importing specific release from github)
   * @param compNameOpt if defined, publish a new version of the component, otherwise the subsystem
   * @param versions list of (name, version) pairs for the collections belonging to the subsystem or component
   * @param comment change comment
   * @param majorVersion if true, increment the subsystem or component's major version
   * @param date the UTC date the version was created
   */
  private def newVersion(collectionNames: Set[String], subsystem: String, versionOpt: Option[String], compNameOpt: Option[String], versions: List[(String, Int)],
                         comment: String, userName: String, majorVersion: Boolean, date: DateTime): Unit = {

    val parts = versions.map(v => Map("name" -> v._1, versionStrKey -> v._2).asDBObject)
    val version = versionOpt.getOrElse(incrVersion(getLatestPublishedVersion(collectionNames, subsystem, compNameOpt), majorVersion))
    //    val now = new DateTime(DateTimeZone.UTC)
    val user = if (userName.nonEmpty) userName else System.getProperty("user.name")
    val obj = Map(
      versionStrKey -> version,
      userKey -> user,
      commentKey -> comment,
      dateKey -> date,
      "parts" -> parts
    ).asDBObject
    val path = compNameOpt.fold(subsystem)(compName => s"$subsystem.$compName")
    db(versionCollectionName(path)).insert(obj, WriteConcern.ACKNOWLEDGED)
  }

  /**
   * Returns a list of information about the versions of the subsystem
   *
   * @param subsystem the name of the subsystem
   */
  def getVersions(subsystem: String): List[VersionInfo] = {
    val current = getVersion(subsystem, None, None).toList
    val collName = versionCollectionName(subsystem)
    if (collectionExists(collName)) {
      val published = for (obj <- db(collName).find().sort(idKey -> -1)) yield VersionInfo(obj)
      current ::: published.toList
    } else current
  }

  /**
   * Returns a list of published version names of the subsystem
   *
   * @param subsystem the name of the subsystem
   */
  def getVersionNames(subsystem: String): List[String] = {
    val collName = versionCollectionName(subsystem)
    if (collectionExists(collName)) {
      val result = for (obj <- db(collName).find().sort(idKey -> -1)) yield obj(versionStrKey).toString
      result.toList
    } else Nil
  }

  /**
   * Returns information about the given version of the given subsystem or component
   *
   * @param subsystem the name of the subsystem
   * @param versionOpt the version of interest (None for the current, unpublished version)
   * @param compNameOpt if defined, return the models for the component, otherwise for the subsystem
   */
  def getVersion(subsystem: String, versionOpt: Option[String], compNameOpt: Option[String]): Option[VersionInfo] = {
    val path = compNameOpt.fold(subsystem)(compName => s"$subsystem.$compName")
    versionOpt match {
      case Some(version) => // published version
        val collName = versionCollectionName(path)
        if (collectionExists(collName)) {
          db(collName).findOne(versionStrKey -> version).map(VersionInfo(_))
        } else {
          None // not found
        }
      case None => // current, unpublished version
        def getPartVersion(path: String): Int = db(path).head(versionKey).asInstanceOf[Int]
        def filter(p: IcdPath) = p.subsystem == subsystem && compNameOpt.fold(true)(_ => p.component == path)
        val paths = getCollectionNames.filter(isStdSet).map(IcdPath).filter(filter).map(_.path).toList
        val now = new DateTime(DateTimeZone.UTC)
        val user = ""
        val comment = "Working version, unpublished"
        val parts = paths.map(p => (p, getPartVersion(p))).map(x => PartInfo(x._1, x._2))
        Some(VersionInfo(None, user, comment, now, parts))
    }
  }

  /**
   * Returns the version name of the latest, published version of the given subsystem or component, if found
   *
   * @param collectionNames list of collection names (for better performance)
   * @param subsystem the name of the subsystem
   * @param compNameOpt if defined, the name of the component
   */
  def getLatestPublishedVersion(collectionNames: Set[String], subsystem: String, compNameOpt: Option[String]): Option[String] = {
    val path = compNameOpt.fold(subsystem)(compName => s"$subsystem.$compName")
    val collName = versionCollectionName(path)
    if (collectionNames.contains(collName))
      Some(db(collName).find().sort(idKey -> -1).one().get(versionStrKey).toString)
    else None
  }

  /**
   * Compares all of the named subsystem parts and returns a list of patches describing any differences.
   *
   * @param subsystem the root subsystem
   * @param v1 the first version to compare (None for the current, unpublished version)
   * @param v2 the second version to compare (None for the current, unpublished version)
   * @return a list of diffs, one for each subsystem or component part
   */
  def diff(subsystem: String, v1: Option[String], v2: Option[String]): List[VersionDiff] = {
    val v1Info = getVersion(subsystem, v1, None)
    val v2Info = getVersion(subsystem, v2, None)
    if (v1Info.isEmpty || v2Info.isEmpty) Nil
    else {
      val result = for {
        p1 <- v1Info.get.parts
        p2 <- v2Info.get.parts if p1.path == p2.path
      } yield diffPart(p1.path, p1.version, p2.version)
      result.flatten
    }
  }

  // Parse string to JSON and Remove _id and _version keys for comparing, since they change each time
  private def parseNoVersionOrId(json: String): JsValue = {
    val obj = JsonParser(json).asJsObject
    val fields = obj.fields - idKey - versionKey
    obj.copy(fields)
    //    JsonParser(json).replace(idKey :: Nil, JsNull).replace(versionKey :: Nil, JsNull)
  }

  // Returns the contents of the given version of the collection path
  private def getVersionOf(coll: MongoCollection, version: Int): String = {
    val currentVersion = coll.head(versionKey).asInstanceOf[Int]
    val v = coll.getCollection(versionColl)
    if (version == currentVersion) {
      coll.head.toString
    } else {
      v.find(versionKey -> version).one().toString
    }
  }

  // Returns the JSON for the given version of the collection path
  private def getJson(path: String, version: Int): JsValue = {
    parseNoVersionOrId(getVersionOf(db(path), version))
  }

  // Returns the diff of the given versions of the given collection path, if they are different
  private def diffPart(path: String, v1: Int, v2: Int): Option[VersionDiff] = {
    diffJson(path, getJson(path, v1), getJson(path, v2))
  }

  // Compares the two json values, returning None if equal, otherwise some VersionDiff
  private def diffJson(path: String, json1: JsValue, json2: JsValue): Option[VersionDiff] = {
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
      case Some(versionInfo) =>
        versionInfo.parts.map(_.path)
          .map(IcdPath)
          .filter(p => p.parts.length == 3)
          .map(_.parts.tail.head)
          .distinct.
          sorted
      case None => Nil
    }
  }

  // Returns a list of IcdEntry objects for the given parts (one part for each originally ingested file)
  // The result is sorted so that the subsystem comes first.
  private def getEntries(parts: List[PartInfo]): List[IcdEntry] = {
    val paths = parts.map(_.path).map(IcdPath)
    val compMap = paths.map(p => (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key => getEntry(db, key, compMap(key))).toList
    entries.sortBy(entry => (IcdPath(entry.name).parts.length, entry.name))
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
   * @param subsystemOnly if true, return only the model for the subsystem
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  private[db] def getModels(subsystem: String, versionOpt: Option[String],
                            compNameOpt: Option[String], subsystemOnly: Boolean = false): List[IcdModels] = {

    // Holds all the model classes associated with a single ICD entry.
    case class Models(versionMap: Map[String, Int], entry: IcdEntry) extends IcdModels {

      // Parses the data from collection s (or an older version of it) and returns a Config object for it
      private def parse(coll: MongoCollection): Config = getConfig(getVersionOf(coll, versionMap(coll.name)))

      override val subsystemModel = entry.subsystem.map(coll => SubsystemModelParser(parse(coll)))
      override val publishModel = entry.publish.map(coll => PublishModelParser(parse(coll)))
      override val subscribeModel = entry.subscribe.map(coll => SubscribeModelParser(parse(coll)))
      override val commandModel = entry.command.map(coll => CommandModelParser(parse(coll)))
      override val componentModel = entry.component.map(coll => ComponentModelParser(parse(coll)))
    }

    getVersion(subsystem, versionOpt, compNameOpt) match {
      case Some(versionInfo) =>
        val versionMap = versionInfo.parts.map(v => v.path -> v.version).toMap
        val allEntries = getEntries(versionInfo.parts)
        val entries = if (subsystemOnly) allEntries.take(1) else allEntries
        entries.map(Models(versionMap, _))
      case None => Nil
    }
  }

  /**
   * Returns the model for the given (or current) version of the given subsystem
   *
   * @param subsystem the subsystem name
   * @param versionOpt optional version
   * @return the subsystem model
   */
  def getSubsystemModel(subsystem: String, versionOpt: Option[String]): Option[SubsystemModel] = {
    getModels(subsystem, versionOpt, None, subsystemOnly = true).headOption.flatMap(_.subsystemModel)
  }

  /**
   * Publishes the given subsystem.
   * If the subsystem string contains a version number, that is the version that is published.
   * (For use when importing from GitHub.)
   *
   * @param subsystem the name of subsystem
   * @param versionOpt optional version string in the form "1.0" (used when importing specific release from github)
   * @param majorVersion if true (and no subsystem version was given), increment the subsystem's major version
   * @param comment change comment
   * @param date the publish date (UTC)
   */
  def publishApi(subsystem: String, versionOpt: Option[String], majorVersion: Boolean, comment: String,
                 userName: String, date: DateTime): Unit = {
    val collectionNames = getCollectionNames

    // Save any of the subsystem's collections that changed
    val icdPaths = collectionNames.filter(isStdSet).map(IcdPath).filter(_.subsystem == subsystem)
    val paths = icdPaths.map(_.path).toList
    val versions = for (path <- paths) yield {
      val coll = db(path)
      val obj = coll.head
      val versionCollName = versionCollectionName(path)
      val version = obj(versionKey).asInstanceOf[Int]
      val id = obj(idKey)
      val exists = collectionNames.contains(versionCollName)
      if (!exists || diff(db(versionCollName), obj).isDefined) {
        // Update version history, avoid duplicate key error?
        val v = db(versionCollName)
        if (exists) v.findAndRemove(idKey -> id)
        v.insert(obj, WriteConcern.ACKNOWLEDGED)
        // increment version for unpublished working copy
        obj.put(versionKey, version + 1)
        coll.remove(coll.head)
        coll.insert(obj, WriteConcern.ACKNOWLEDGED)
      }
      (path, version)
    }

    // Add to collection of published subsystem versions
    newVersion(collectionNames, subsystem, versionOpt, None, versions, comment, userName, majorVersion, date)

    // Add to collection of published subsystem component versions
    getComponentNames(subsystem, None).foreach { name =>
      val prefix = s"$subsystem.$name."
      val compVersions = versions.filter(p => p._1.startsWith(prefix))
      newVersion(collectionNames, subsystem, versionOpt, Some(name), compVersions, comment, userName, majorVersion, date)
    }
  }

  /**
   * Returns the version name of the latest, published ICD from subsystem to target
   *
   * @param subsystem the source subsystem
   * @param target the target subsystem
   */
  def getLatestPublishedIcdVersion(subsystem: String, target: String): Option[String] = {
    if (collectionExists(icdCollName)) {
      val result = db(icdCollName).find(MongoDBObject(subsystemKey -> subsystem, targetKey -> target)).sort(idKey -> -1).one()
      try {
        if (result.isEmpty)
          None
        else
          Some(result.get(versionStrKey).toString)
      } catch {
        // Seems like a casbah/mongodb bug that result.isEmpty above can throw this...
        case e: NullPointerException => None
      }
    } else None
  }

  /**
   * Publishes an ICD from the given version of the given subsystem to the target subsystem and version
   *
   * @param subsystem the source subsystem
   * @param subsystemVersion the source subsystem version
   * @param target the target subsystem
   * @param targetVersion the target subsystem version
   * @param majorVersion if true, incr major version
   * @param comment comment to go with this version
   */
  def publishIcd(subsystem: String, subsystemVersion: String,
                 target: String, targetVersion: String,
                 majorVersion: Boolean, comment: String, userName: String): Unit = {
    val icdVersion = incrVersion(getLatestPublishedIcdVersion(subsystem, target), majorVersion)
    val date = new DateTime(DateTimeZone.UTC)
    val user = if (userName.nonEmpty) userName else System.getProperty("user.name")
    addIcdVersion(icdVersion, subsystem, subsystemVersion, target, targetVersion, user, comment, date)
  }

  /**
   * Adds an entry for a published ICD with the given version,
   * from the given subsystem and version to the target subsystem and version.
   *
   * @param icdVersion the new ICD version
   * @param subsystem the source subsystem
   * @param subsystemVersion the source subsystem version
   * @param target the target subsystem
   * @param targetVersion the target subsystem version
   * @param user the user who made the release
   * @param comment comment to go with this version
   */
  def addIcdVersion(
    icdVersion: String,
    subsystem:  String, subsystemVersion: String,
    target: String, targetVersion: String,
    user: String, comment: String, date: DateTime
  ): Unit = {

    // Only add an ICD version if the referenced subsystem and target versions exist
    val subsystemVersions = getVersions(subsystem)
    val targetVersions = getVersions(target)
    if (subsystemVersions.exists(_.versionOpt.contains(subsystemVersion)) && targetVersions.exists(_.versionOpt.contains(targetVersion))) {
      val obj = Map(
        versionStrKey -> icdVersion,
        subsystemKey -> subsystem,
        subsystemVersionKey -> subsystemVersion,
        targetKey -> target,
        targetVersionKey -> targetVersion,
        userKey -> user,
        dateKey -> date,
        commentKey -> comment
      ).asDBObject
      db(icdCollName).insert(obj, WriteConcern.ACKNOWLEDGED)
    } else {
      println(s"Warning: Not adding ICD version $icdVersion between $subsystem-$subsystemVersion and $target-$targetVersion, since not all referenced subsystem versions exist")
    }
  }

  /**
   * Removes all entries for published ICDs with the given subsystem and target subsystem.
   *
   * @param subsystem the source subsystem
   * @param target the target subsystem
   */
  def removeIcdVersions(subsystem: String, target: String): Unit = {
    db(icdCollName).remove(MongoDBObject(subsystemKey -> subsystem, targetKey -> target), WriteConcern.ACKNOWLEDGED)
  }

  /**
   * Returns a list of published ICDs
   */
  def getIcdNames: List[IcdName] = {
    if (collectionExists(icdCollName)) {
      db(icdCollName).map(obj => IcdName(obj(subsystemKey).toString, obj(targetKey).toString)).toList.distinct.sorted
    } else Nil
  }

  /**
   * Returns a list of published ICD versions
   *
   * @param subsystem the ICD's source subsystem
   * @param target the ICD's target subsystem
   */
  def getIcdVersions(subsystem: String, target: String): List[IcdVersionInfo] = {
    // ICDs are stored with the two subsystems sorted by name
    val subsystems = List(subsystem, target)
    val sorted = subsystems.sorted
    val s = sorted.head
    val t = sorted.tail.head

    if (collectionExists(icdCollName)) {
      db(icdCollName)
        .find(MongoDBObject(subsystemKey -> s, targetKey -> t))
        .sort(idKey -> -1)
        .map { obj =>
          IcdVersionInfo(
            icdVersion = IcdVersion(
              icdVersion = obj(versionStrKey).toString,
              subsystem = subsystem,
              subsystemVersion = obj(subsystemVersionKey).toString,
              target = target,
              targetVersion = obj(targetVersionKey).toString
            ),
            user = obj(userKey).toString,
            comment = obj(commentKey).toString,
            date = obj(dateKey).asInstanceOf[DateTime].withZone(DateTimeZone.UTC).toString
          )
        }.toList
    } else Nil
  }
}
