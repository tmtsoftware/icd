package csw.services.icd.db

import csw.services.icd._
import icd.web.shared.IcdModels.{ComponentModel, SubsystemModel}
import icd.web.shared.{IcdModels, IcdVersion, IcdVersionInfo, SubsystemWithVersion}
import org.joda.time.{DateTime, DateTimeZone}
import diffson.playJson._
import diffson.lcs._
import diffson.jsonpatch._
import diffson.jsonpatch.lcsdiff.remembering._
import csw.services.icd.db.parser.{
  ComponentModelBsonParser,
  PublishModelBsonParser,
  SubscribeModelBsonParser,
  SubsystemModelBsonParser
}
import play.api.libs.json.{JsObject, JsValue, Json}
import reactivemongo.api.bson.{BSONDateTime, BSONDocument, BSONString}
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.api.bson.collection.BSONCollection

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages Subsystem and component versioning in the database.
 * Previous versions of a DefaultDB collection coll are stored in coll.v.
 * In addition, a top level collection keeps track of which versions of each collection belong
 * to a given "top level" version for the subsystem (or component).
 */
object IcdVersionManager {

  implicit val lcs: Patience[JsValue] = new Patience[JsValue]

  /** The id key inserted into all documents */
  val idKey = "_id"

  /** The version key inserted into all documents */
  val versionKey = "_version"

  /** The version key used for top level subsystems or components */
  val versionStrKey = "version"

  /** The name of the sub-collection containing the previous versions or version information */
  val versionColl = "v"

  val versionSuffix = ".v"

  val partsKey = "parts"

  /** Name of collection with information about published ICDs */
  val icdCollName = "icds"

  val subsystemKey        = "subsystem"
  val subsystemVersionKey = "subsystemVersion"
  val targetKey           = "target"
  val targetVersionKey    = "targetVersion"
  val userKey             = "user"
  val dateKey             = "date"
  val commentKey          = "comment"

  val queryAny: BSONDocument = BSONDocument()

  /**
   * Holds a collection path for a component or subsystem and it's version
   */
  case class PartInfo(path: String, version: Int)

  // Name of version history collection for the given subsystem or component
  def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Describes a version of a subsystem or component
   *
   * @param maybeVersion the subsystem or component version (major.minor), if published
   * @param user         the user that created the version
   * @param comment      a change comment
   * @param date         the date of the change
   * @param parts        names and versions of the subsystem or component parts
   */
  case class VersionInfo(maybeVersion: Option[String], user: String, comment: String, date: DateTime, parts: List[PartInfo]) {
    // Gets the version of the part with the given path
    def getPartVersion(path: String): Option[Int] = {
      val list = for (part <- parts if part.path == path) yield part.version
      list.headOption
    }
  }

  // XXX TODO: Use automatic JSON conversion for reading and writing this?
  object VersionInfo {
    // Creates a VersionInfo instance from an object in the database
    def apply(doc: BSONDocument): VersionInfo = {
      import reactivemongo.api.bson._
      val maybeVersion = doc.getAsOpt[String](versionStrKey)
      val user         = doc.getAsOpt[String](userKey).get
      val comment      = doc.getAsOpt[String](commentKey).get
      val date         = new DateTime(doc.getAsOpt[BSONDateTime](dateKey).get.value, DateTimeZone.UTC)
      val partDocs     = doc.getAsOpt[Array[BSONDocument]](partsKey).get.toList
      val parts = partDocs.map { part =>
        val name    = part.getAsOpt[String]("name").get
        val version = part.getAsOpt[Int](versionStrKey).get
        PartInfo(name, version)
      }

      VersionInfo(maybeVersion, user, comment, date, parts)
    }
  }

  /**
   * Represents the difference between two versions of a subsystem or component part in the db
   * (parts have names that end with "icd", "component", "publish", "subscribe", "command")
   *
   * @param path  the path to a part of the subsystem or component (for example: "NFIRAOS.lgsWfs.publish")
   * @param patch an object describing the difference for the subsystem or component part
   */
  case class VersionDiff(path: String, patch: JsonPatch[JsValue])

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
  case class SubsystemAndVersion(subsystem: String, maybeVersion: Option[String]) extends Ordered[SubsystemAndVersion] {
    if (!Subsystems.allSubsystems.contains(subsystem)) {
      throw new IllegalArgumentException(s"Unknown subsystem: $subsystem")
    }

    maybeVersion.foreach(SubsystemAndVersion.checkVersion)

    override def toString: String = maybeVersion match {
      case Some(v) => s"$subsystem-$v"
      case None    => subsystem
    }

    // Used to sort subsystems alphabetically, to avoid duplicates, since A->B should be the same as B->A
    override def compare(that: SubsystemAndVersion): Int = {
      Subsystems.compare(subsystem, that.subsystem)
    }
  }

  object SubsystemAndVersion {

    /**
     * Extracts the subsystem and optional version, if defined
     *
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
  def incrVersion(maybeVersion: Option[String], majorVersion: Boolean): String = {
    maybeVersion match {
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
 * @param query may be used to share caching of collection names (see CachedIcdDbQuery)
 */
//noinspection DuplicatedCode
case class IcdVersionManager(query: IcdDbQuery) {

  import IcdVersionManager._
  import IcdDbQuery._

  private val db = query.db

  // Performance can be improved by caching these values in some cases (redefine in a subclass)
  private[db] def collectionExists(name: String): Boolean = query.collectionExists(name)

  private[db] def getCollectionNames: Set[String] = query.getCollectionNames

  /**
   * Increments the version for the named subsystem or component.
   * This creates a Mongo collection named "name.v" that contains the subsystem or component version (starting with "1.0"),
   * the user and date as well as a list of the names and versions of each of the subsystem or component parts.
   *
   * @param collectionNames list of collection names (for better performance)
   * @param subsystem       the subsystem
   * @param maybeVersion    optional version string in the form "1.0" (used when importing specific release from github)
   * @param maybeComponent  if defined, publish a new version of the component, otherwise the subsystem
   * @param versions        list of (name, version) pairs for the collections belonging to the subsystem or component
   * @param comment         change comment
   * @param majorVersion    if true, increment the subsystem or component's major version
   * @param date            the UTC date the version was created
   */
  private def newVersion(
      collectionNames: Set[String],
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      versions: List[(String, Int)],
      comment: String,
      username: String,
      majorVersion: Boolean,
      date: DateTime
  ): Unit = {

    val parts = versions.map(v => BSONDocument("name" -> v._1, versionStrKey -> v._2))
    val version =
      maybeVersion.getOrElse(incrVersion(getLatestPublishedVersion(collectionNames, subsystem, maybeComponent), majorVersion))
    val user = if (username.nonEmpty) username else System.getProperty("user.name")
    val obj = BSONDocument(
      versionStrKey -> version,
      userKey       -> user,
      commentKey    -> comment,
      dateKey       -> BSONDateTime(date.getMillis),
      partsKey      -> parts
    )
    val path = maybeComponent.fold(subsystem)(compName => s"$subsystem.$compName")
    val coll = db.collection[BSONCollection](versionCollectionName(path))
    coll.insert.one(obj).await
  }

  private def sortCollectionById(collName: String): List[BSONDocument] = {
    import reactivemongo.play.json.compat._
    val coll = db.collection[BSONCollection](collName)
    coll
      .find(queryAny, Option.empty[JsObject])
      .sort(BSONDocument(idKey -> -1))
      .cursor[BSONDocument]()
      .collect[Array](-1, Cursor.FailOnError[Array[BSONDocument]]())
      .await
      .toList
  }

  /**
   * Returns a list of information about the versions of the subsystem
   *
   * @param subsystem the name of the subsystem
   */
  def getVersions(subsystem: String): List[VersionInfo] = {
    val current  = getVersion(SubsystemWithVersion(subsystem, None, None)).toList
    val collName = versionCollectionName(subsystem)
    if (collectionExists(collName)) {
      val docs      = sortCollectionById(collName)
      val published = docs.map(doc => VersionInfo(doc))
      current ::: published
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
      val docs = sortCollectionById(collName)
      docs.map { doc =>
        doc.getAsOpt[String](versionStrKey).get
      }
    } else Nil
  }

  /**
   * Returns information about the given version of the given subsystem or component
   *
   * @param sv the subsystem
   */
  def getVersion(sv: SubsystemWithVersion): Option[VersionInfo] = {
    import reactivemongo.api.bson._
    import reactivemongo.play.json.compat._
    val path = sv.maybeComponent.fold(sv.subsystem)(compName => s"${sv.subsystem}.$compName")
    sv.maybeVersion match {
      case Some(version) => // published version
        val collName = versionCollectionName(path)
        if (collectionExists(collName)) {
          val coll     = db.collection[BSONCollection](collName)
          val query    = BSONDocument(versionStrKey -> version)
          val maybeDoc = coll.find(query, Option.empty[JsObject]).one[BSONDocument].await
          maybeDoc.map(VersionInfo(_))
        } else {
          None // not found
        }
      case None => // current, unpublished version
        def getPartVersion(path: String): Option[Int] = {
          val coll     = db.collection[BSONCollection](path)
          val maybeDoc = coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await
          maybeDoc.flatMap(_.getAsOpt[BSONInteger](versionKey).map(_.value))
        }

        def filter(p: IcdPath) = p.subsystem == sv.subsystem && sv.maybeComponent.fold(true)(_ => p.component == path)

        val paths   = getCollectionNames.filter(isStdSet).map(IcdPath).filter(filter).map(_.path).toList
        val now     = new DateTime(DateTimeZone.UTC)
        val user    = ""
        val comment = "Working version, unpublished"
        val parts   = paths.map(p => (p, getPartVersion(p))).flatMap(pair => pair._2.map(version => PartInfo(pair._1, version)))
        Some(VersionInfo(None, user, comment, now, parts))
    }
  }

  /**
   * Returns the version name of the latest, published version of the given subsystem or component, if found
   *
   * @param collectionNames list of collection names (for better performance)
   * @param subsystem       the name of the subsystem
   * @param maybeComponent  if defined, the name of the component
   */
  def getLatestPublishedVersion(
      collectionNames: Set[String],
      subsystem: String,
      maybeComponent: Option[String]
  ): Option[String] = {
    import reactivemongo.play.json.compat._
    val path     = maybeComponent.fold(subsystem)(compName => s"$subsystem.$compName")
    val collName = versionCollectionName(path)
    if (collectionNames.contains(collName)) {
      val coll = db.collection[BSONCollection](collName)

      coll
        .find(queryAny, Option.empty[JsObject])
        .sort(BSONDocument(idKey -> -1))
        .one[BSONDocument]
        .await
        .map(_.getAsOpt[String](versionStrKey))
        .head
    } else None
  }

  /**
   * Compares all of the named subsystem parts and returns a list of patches describing any differences.
   *
   * @param subsystem the root subsystem
   * @param v1        the first version to compare (None for the current, unpublished version)
   * @param v2        the second version to compare (None for the current, unpublished version)
   * @return a list of diffs, one for each subsystem or component part
   */
  def diff(subsystem: String, v1: Option[String], v2: Option[String]): List[VersionDiff] = {
    val sv1    = SubsystemWithVersion(subsystem, v1, None)
    val sv2    = SubsystemWithVersion(subsystem, v2, None)
    val v1Info = getVersion(sv1)
    val v2Info = getVersion(sv2)
    if (v1Info.isEmpty || v2Info.isEmpty) Nil
    else {
      val result = for {
        p1 <- v1Info.get.parts
        p2 <- v2Info.get.parts if p1.path == p2.path
      } yield diffPart(p1.path, p1.version, p2.version)
      result.flatten
    }
  }

  // Remove _id and _version keys for comparing, since they change each time
  private def withoutVersionOrId(jsValue: JsValue): JsValue = {
    val obj = jsValue.as[JsObject]
    obj - idKey - versionKey
  }

  // Returns the contents of the given version of the given collection
  private def getVersionOf(coll: BSONCollection, version: Int): BSONDocument = {
    import reactivemongo.api.bson._
    import reactivemongo.play.json.compat._
    // Get a previously published version from $coll.v
    def getPublishedDoc: BSONDocument = {
      val v     = db.collection[BSONCollection](versionCollectionName(coll.name))
      val query = BSONDocument(versionKey -> version)
      v.find(query, Option.empty[JsObject]).one[BSONDocument].await.getOrElse(queryAny)
    }
    // Note: Doc might not exist in current version, but exist in an older, published version
    coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await match {
      case Some(doc) =>
        val currentVersion = doc.getAsOpt[BSONInteger](versionKey).get.value
        if (version == currentVersion) doc else getPublishedDoc

      case None =>
        getPublishedDoc
    }
  }

  // Returns the JSON for the given version of the collection path
  private def getJsonWithoutVersionOrId(path: String, version: Int): JsValue = {
    import reactivemongo.api.bson._
    import reactivemongo.play.json.compat._
    val jsValue = Json.toJson(getVersionOf(db(path), version))
    withoutVersionOrId(jsValue)
  }

  // Returns the diff of the given versions of the given collection path, if they are different
  private def diffPart(path: String, v1: Int, v2: Int): Option[VersionDiff] = {
    diffJson(path, getJsonWithoutVersionOrId(path, v1), getJsonWithoutVersionOrId(path, v2))
  }

  // Compares the two json values, returning None if equal, otherwise some VersionDiff
  private def diffJson(path: String, json1: JsValue, json2: JsValue): Option[VersionDiff] = {
    if (json1 == json2) None
    else {
      val diff = diffson.diff(json1, json2)
      Some(VersionDiff(path, diff))
    }
  }

  // Compares the given object with the current (head) version in the collection
  // (ignoring version and id values)
  def diff(coll: BSONCollection, obj: BSONDocument): Option[VersionDiff] = {
    import reactivemongo.api.bson._
    import reactivemongo.play.json.compat._
    val headDoc = coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await.get
    val json1   = withoutVersionOrId(Json.toJson(headDoc))
    val json2   = withoutVersionOrId(Json.toJson(obj))
    diffJson(coll.name, json1, json2)
  }

  /**
   * Returns a list of all the component names in the DB belonging to the given subsystem version
   */
  def getComponentNames(sv: SubsystemWithVersion): List[String] = {
    getVersion(sv) match {
      case Some(versionInfo) =>
        versionInfo.parts
          .map(_.path)
          .map(IcdPath)
          .filter(p => p.parts.length == 3)
          .map(_.parts.tail.head)
          .distinct
          .sorted
      case None => Nil
    }
  }

  // Returns a list of IcdEntry objects for the given parts (one part for each originally ingested file)
  // The result is sorted so that the subsystem comes first.
  private def getEntries(parts: List[PartInfo]): List[IcdEntry] = {
    val paths = parts.map(_.path).map(IcdPath)
    query.getEntries(paths)
  }

  /**
   * Returns a list of models for the given subsystem version or component,
   * based on the data in the database.
   * The list includes the model for the subsystem, followed
   * by any models for components that were defined in subdirectories
   * in the original files that were ingested into the database
   * (In this case the definitions are stored in sub-collections in the DB).
   *
   * @param sv            the subsystem
   * @param subsystemOnly if true, return only the model for the subsystem
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  private[db] def getModels(sv: SubsystemWithVersion, subsystemOnly: Boolean = false): List[IcdModels] = {

    // Holds all the model classes associated with a single ICD entry.
    case class Models(versionMap: Map[String, Int], entry: IcdEntry) extends IcdModels {

      private def getDocVersion(coll: BSONCollection): BSONDocument = getVersionOf(coll, versionMap(coll.name))

      override val subsystemModel: Option[SubsystemModel] =
        entry.subsystem.flatMap(coll => SubsystemModelBsonParser(getDocVersion(coll)))
      override val publishModel: Option[IcdModels.PublishModel] =
        entry.publish.flatMap(coll => PublishModelBsonParser(getDocVersion(coll)))
      override val subscribeModel: Option[IcdModels.SubscribeModel] =
        entry.subscribe.flatMap(coll => SubscribeModelBsonParser(getDocVersion(coll)))
      override val commandModel: Option[IcdModels.CommandModel] =
        entry.command.flatMap(coll => parser.CommandModelBsonParser(getDocVersion(coll)))
      override val componentModel: Option[IcdModels.ComponentModel] =
        entry.component.flatMap(coll => ComponentModelBsonParser(getDocVersion(coll)))
    }

    getVersion(sv) match {
      case Some(versionInfo) =>
        val versionMap = versionInfo.parts.map(v => v.path -> v.version).toMap
        val allEntries = getEntries(versionInfo.parts)
        val entries    = if (subsystemOnly) allEntries.take(1) else allEntries
        entries.map(Models(versionMap, _))
      case None =>
        val v = sv.maybeVersion.map("-" + _).getOrElse("")
        println(s"${sv.subsystem}$v not found in the icd database.")
        Nil
    }
  }

  /**
   * Returns the model for the given (or current) version of the given subsystem
   *
   * @param sv the subsystem and version
   * @return the subsystem model
   */
  def getSubsystemModel(sv: SubsystemWithVersion): Option[SubsystemModel] = {
    getModels(sv, subsystemOnly = true).headOption.flatMap(_.subsystemModel)
  }

  /**
   * Returns the component model for the given (or current) version of the given subsystem
   * (Called when sv.maybeComponent is defined, so only one component is in the models list)
   *
   * @param sv the subsystem and version
   * @return the subsystem model
   */
  def getComponentModel(sv: SubsystemWithVersion): Option[ComponentModel] = {
    getModels(sv).headOption.flatMap(_.componentModel)
  }

  /**
   * Publishes the given version of the subsystem in the database.
   * (For use when importing releases of APIs and ICDs from GitHub.)
   *
   * @param subsystem    the name of subsystem
   * @param maybeVersion optional version string in the form "1.0" (used when importing specific release from github)
   * @param majorVersion if true (and no subsystem version was given), increment the subsystem's major version
   * @param comment      change comment
   * @param date         the publish date (UTC)
   */
  def publishApi(
      subsystem: String,
      maybeVersion: Option[String],
      majorVersion: Boolean,
      comment: String,
      username: String,
      date: DateTime
  ): Unit = {
    import reactivemongo.api.bson._
    import reactivemongo.play.json.compat._
    val collectionNames = getCollectionNames

    // Save any of the subsystem's collections that changed
    val icdPaths = collectionNames.filter(isStdSet).map(IcdPath).filter(_.subsystem == subsystem)
    val paths    = icdPaths.map(_.path).toList
    val versions = for (path <- paths) yield {
      val coll            = db.collection[BSONCollection](path)
      val obj             = coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await.get
      val version         = obj.getAsOpt[BSONInteger](versionKey).get.value
      val id              = obj.getAsOpt[BSONObjectID](idKey).get
      val versionCollName = versionCollectionName(path)
      val exists          = collectionNames.contains(versionCollName)
      val lastVersion = if (!exists || diff(db(versionCollName), obj).isDefined) {
        // Update version history
        val v = db.collection[BSONCollection](versionCollName)
        if (exists) {
          // avoid duplicate key (needed?)
          v.findAndRemove(BSONDocument(idKey -> id), None, None, WriteConcern.Default, None, None, Nil).await
        }
        v.insert.one(obj).await
        // increment version for unpublished working copy
        val mod = BSONDocument("$set" -> BSONDocument(versionKey -> (version + 1)))
        coll.update.one(queryAny, mod).await
        version
      } else
        version - 1
      (path, lastVersion)
    }

    // Add to collection of published subsystem versions
    newVersion(collectionNames, subsystem, maybeVersion, None, versions, comment, username, majorVersion, date)

    // Add to collection of published subsystem component versions
    getComponentNames(SubsystemWithVersion(subsystem, None, None)).foreach { name =>
      val prefix       = s"$subsystem.$name."
      val compVersions = versions.filter(p => p._1.startsWith(prefix))
      newVersion(collectionNames, subsystem, maybeVersion, Some(name), compVersions, comment, username, majorVersion, date)
    }
  }

  /**
   * Adds an entry for a published ICD with the given version,
   * from the given subsystem and version to the target subsystem and version.
   *
   * @param icdVersion       the new ICD version
   * @param subsystem        the source subsystem
   * @param subsystemVersion the source subsystem version
   * @param target           the target subsystem
   * @param targetVersion    the target subsystem version
   * @param user             the user who made the release
   * @param comment          comment to go with this version
   */
  def addIcdVersion(
      icdVersion: String,
      subsystem: String,
      subsystemVersion: String,
      target: String,
      targetVersion: String,
      user: String,
      comment: String,
      date: DateTime
  ): Unit = {

    // Only add an ICD version if the referenced subsystem and target versions exist
    val subsystemVersions = getVersions(subsystem)
    val targetVersions    = getVersions(target)
    if (subsystemVersions.exists(_.maybeVersion.contains(subsystemVersion)) && targetVersions.exists(
          _.maybeVersion.contains(targetVersion)
        )) {
      val obj = BSONDocument(
        versionStrKey       -> icdVersion,
        subsystemKey        -> subsystem,
        subsystemVersionKey -> subsystemVersion,
        targetKey           -> target,
        targetVersionKey    -> targetVersion,
        userKey             -> user,
        dateKey             -> BSONDateTime(date.getMillis),
        commentKey          -> comment
      )
      db.collection[BSONCollection](icdCollName).insert.one(obj).await
    } else {
      println(
        s"Warning: Not adding ICD version $icdVersion between $subsystem-$subsystemVersion and $target-$targetVersion, since not all referenced subsystem versions exist"
      )
    }
  }

  /**
   * Removes all entries for published ICDs with the given subsystem and target subsystem.
   *
   * @param subsystem the source subsystem
   * @param target    the target subsystem
   */
  def removeIcdVersions(subsystem: String, target: String): Unit = {
    val query = BSONDocument(subsystemKey -> subsystem, targetKey -> target)
    db.collection[BSONCollection](icdCollName).delete.one(query).await
  }

  /**
   * Returns a list of published ICDs
   */
  def getIcdNames: List[IcdName] = {
    import reactivemongo.play.json.compat._
    if (collectionExists(icdCollName)) {
      val coll = db.collection[BSONCollection](icdCollName)
      val docs =
        coll
          .find(queryAny, Option.empty[JsObject])
          .cursor[BSONDocument]()
          .collect[Array](-1, Cursor.FailOnError[Array[BSONDocument]]())
          .await
          .toList
      docs
        .map { doc =>
          val subsystem = doc.getAsOpt[BSONString](subsystemKey).map(_.value).get
          val target    = doc.getAsOpt[BSONString](targetKey).map(_.value).get
          IcdName(subsystem, target)
        }
        .distinct
        .sorted
    } else Nil
  }

  /**
   * Returns a list of published ICD versions
   *
   * @param subsystem the ICD's source subsystem
   * @param target    the ICD's target subsystem
   */
  def getIcdVersions(subsystem: String, target: String): List[IcdVersionInfo] = {
    val subsystems = List(subsystem, target)
    val sorted     = Subsystems.sorted(subsystems)
    val s          = sorted.head
    val t          = sorted.tail.head

    if (collectionExists(icdCollName)) {
      val coll = db.collection[BSONCollection](icdCollName)
      val docs = {
        import reactivemongo.play.json.compat._
        coll
          .find(BSONDocument(subsystemKey -> s, targetKey -> t), Option.empty[JsObject])
          .sort(BSONDocument(idKey -> -1))
          .cursor[BSONDocument]()
          .collect[Array](-1, Cursor.FailOnError[Array[BSONDocument]]())
          .await
          .toList
      }
      docs
        .map { doc =>
          import reactivemongo.api.bson._
          val icdVersion       = doc.getAsOpt[String](versionStrKey).get
          val subsystem        = doc.getAsOpt[String](subsystemKey).get
          val subsystemVersion = doc.getAsOpt[String](subsystemVersionKey).get
          val target           = doc.getAsOpt[String](targetKey).get
          val targetVersion    = doc.getAsOpt[String](targetVersionKey).get
          val user             = doc.getAsOpt[String](userKey).get
          val comment          = doc.getAsOpt[String](commentKey).get
          val date             = new DateTime(doc.getAsOpt[BSONDateTime](dateKey).get.value, DateTimeZone.UTC).toString()
          IcdVersionInfo(
            IcdVersion(icdVersion, subsystem, subsystemVersion, target, targetVersion),
            user,
            comment,
            date
          )
        }
    } else Nil
  }
}
