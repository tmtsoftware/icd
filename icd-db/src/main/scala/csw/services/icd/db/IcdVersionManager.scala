package csw.services.icd.db

import csw.services.icd.*
import csw.services.icd.StdName.serviceFileNames
import icd.web.shared.IcdModels.{ComponentModel, IcdModel, ServiceModel, SubsystemModel}
import icd.web.shared.{IcdModels, IcdVersion, IcdVersionInfo, PdfOptions, SubsystemWithVersion}
import org.joda.time.{DateTime, DateTimeZone}
import diffson.playJson.*
import diffson.lcs.*
import diffson.jsonpatch.*
import diffson.jsonpatch.lcsdiff.remembering.*
import csw.services.icd.db.parser.{
  AlarmsModelBsonParser,
  ComponentModelBsonParser,
  IcdModelBsonParser,
  PublishModelBsonParser,
  ServiceModelBsonParser,
  SubscribeModelBsonParser,
  SubsystemModelBsonParser
}
import play.api.libs.json.{DefaultWrites, JsObject, JsValue, Json}
import reactivemongo.api.bson.{BSONDateTime, BSONDocument, BSONObjectID}
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.api.bson.collection.BSONCollection
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.github.IcdGitManager
import icd.web.shared.ComponentInfo.PublishType
//import lax.*
import reactivemongo.play.json.compat.*
import bson2json.*
import json2bson.*

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages Subsystem and component versioning in the database.
 * Previous versions of a DB collection coll are stored in coll.v.
 * In addition, a top level collection keeps track of which versions of each collection belong
 * to a given "top level" version for the subsystem (or component).
 */
object IcdVersionManager extends DefaultWrites {

  implicit val lcs: Patience[JsValue] = new Patience[JsValue]

  /** The id key inserted into all documents */
  private val idKey = "_id"

  /** The version key inserted into all documents */
  val versionKey = "_version"

  /** The version key used for top level subsystems or components */
  private val versionStrKey = "version"

  /** The name of the sub-collection containing the previous versions or version information */
  private val versionColl = "v"

  val versionSuffix = ".v"

  private val partsKey = "parts"

  /** Name of collection with information about published ICDs */
  private val icdCollName = "icds"

  private val subsystemKey        = "subsystem"
  private val subsystemVersionKey = "subsystemVersion"
  private val targetKey           = "target"
  private val targetVersionKey    = "targetVersion"
  private val userKey             = "user"
  private val dateKey             = "date"
  private val commentKey          = "comment"
  private val commitKey           = "commit"

  private val queryAny: BSONDocument = BSONDocument()

  /**
   * Holds a collection path for a component or subsystem and it's version
   */
  case class PartInfo(path: String, version: Int)

  // Name of version history collection for the given subsystem or component
  private def versionCollectionName(name: String): String = s"$name.$versionColl"

  /**
   * Describes a version of a subsystem or component
   *
   * @param maybeVersion the subsystem or component version (major.minor), if published
   * @param user         the user that created the version
   * @param comment      a change comment
   * @param date         the date of the change
   * @param parts        names and versions of the subsystem or component parts
   */
  case class VersionInfo(
      maybeVersion: Option[String],
      user: String,
      comment: String,
      date: DateTime,
      commit: String,
      parts: List[PartInfo]
  )
//  {
//    // Gets the version of the part with the given path
//    def getPartVersion(path: String): Option[Int] = {
//      val list = for (part <- parts if part.path == path) yield part.version
//      list.headOption
//    }
//  }

  // XXX TODO: Use automatic JSON conversion for reading and writing this?
  object VersionInfo {
    // Creates a VersionInfo instance from an object in the database
    def apply(doc: BSONDocument): VersionInfo = {
      import reactivemongo.api.bson.*
      val maybeVersion = doc.string(versionStrKey)
      val user         = doc.string(userKey).get
      val comment      = doc.string(commentKey).get
      val date         = new DateTime(doc.getAsOpt[BSONDateTime](dateKey).get.value, DateTimeZone.UTC)
      val commit       = doc.string(commitKey).get
      val partDocs     = doc.children(partsKey)
      val parts = partDocs.map { part =>
        val name    = part.string("name").get
        val version = part.int(versionStrKey).get
        PartInfo(name, version)
      }

      VersionInfo(maybeVersion, user, comment, date, commit, parts)
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

    override def toString: String =
      maybeVersion match {
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
      }
      else SubsystemAndVersion(s, None)
    }

    /**
     * Validates the format of the given version string
     */
    def checkVersion(v: String): Unit = {
      if (v != "master" && !v.matches("\\d+\\.\\d+")) throw new IllegalArgumentException(s"Invalid subsystem version: $v")
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
//noinspection DuplicatedCode,SpellCheckingInspection
case class IcdVersionManager(icdDb: IcdDb) {

  import IcdVersionManager.*
  import IcdDbQuery.*

  val query: IcdDbQuery = icdDb.query
  private val db        = query.db

  // Performance can be improved by caching these values in some cases (redefine in a subclass)
  private[db] def collectionExists(name: String): Boolean = query.collectionExists(name)

  private[db] def getCollectionNames: Set[String] = query.getCollectionNames

  /**
   * Increments the version for the named subsystem or component.
   * This creates a Mongo collection named "name.v" that contains the subsystem or component version (starting with "1.0"),
   * the user and date as well as a list of the collection names and versions of each of the subsystem or component parts.
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
      date: DateTime,
      commit: String
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
      commitKey     -> commit,
      partsKey      -> parts
    )
    val path = maybeComponent.fold(subsystem)(compName => s"$subsystem.$compName")
    val coll = db.collection[BSONCollection](versionCollectionName(path))
    coll.delete().one(BSONDocument(versionStrKey -> version)).await
    coll.insert.one(obj).await
  }

  private def sortCollectionById(collName: String): List[BSONDocument] = {
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
    }
    else current
  }

//  /**
//   * Returns a list of published version names of the subsystem
//   *
//   * @param subsystem the name of the subsystem
//   */
//  def getVersionNames(subsystem: String): List[String] = {
//    val collName = versionCollectionName(subsystem)
//    if (collectionExists(collName)) {
//      val docs = sortCollectionById(collName)
//      docs.map { doc =>
//        doc.string(versionStrKey).get
//      }
//    }
//    else Nil
//  }

  /**
   * Returns information about the given version of the given subsystem or component
   *
   * @param sv the subsystem
   */
  def getVersion(sv: SubsystemWithVersion): Option[VersionInfo] = {
    val path = sv.maybeComponent.fold(sv.subsystem)(compName => s"${sv.subsystem}.$compName")
    sv.maybeVersion match {
      case Some(version) => // published version
        val collName = versionCollectionName(path)
        def getVersionInfo: Option[VersionInfo] = {
          if (collectionExists(collName)) {
            val coll     = db.collection[BSONCollection](collName)
            val query    = BSONDocument(versionStrKey -> version)
            val maybeDoc = coll.find(query, Option.empty[JsObject]).one[BSONDocument].await
            maybeDoc.map(VersionInfo(_))
          }
          else None
        }
        val versionInfo = getVersionInfo
        if (versionInfo.nonEmpty) versionInfo
        else {
          // This version hasn't been ingested yet?
          IcdGitManager.ingest(
            icdDb,
            SubsystemAndVersion(sv.subsystem, sv.maybeVersion),
            s => println(s"XXX (auto-ingest) $s"),
            IcdGitManager.allApiVersions
          )
          getVersionInfo
        }
      case None => // current, unpublished version
        def getPartVersion(path: String): Option[Int] = {
          val coll     = db.collection[BSONCollection](path)
          val maybeDoc = coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await
          maybeDoc.flatMap(_.int(versionKey))
        }

        def filter(p: IcdPath) = p.subsystem == sv.subsystem && sv.maybeComponent.fold(true)(_ => p.component == path)

        val collectionNames = getCollectionNames
        val icdPaths        = collectionNames.filter(isStdSet).map(IcdPath.apply).filter(filter)
        val paths           = icdPaths.map(_.path).toList ++ getOpenApiCollectionPaths(collectionNames, icdPaths)
        val now             = new DateTime(DateTimeZone.UTC)
        val user            = ""
        val comment         = "Working version, unpublished"
        val commit          = ""
        val parts = paths.map(p => (p, getPartVersion(p))).flatMap(pair => pair._2.map(version => PartInfo(pair._1, version)))
        Some(VersionInfo(None, user, comment, now, commit, parts))
    }
  }

  /**
   * Returns the version name of the latest, published version of the given subsystem or component, if found
   *
   * @param collectionNames list of collection names (for better performance)
   * @param subsystem       the name of the subsystem
   * @param maybeComponent  if defined, the name of the component
   */
  private def getLatestPublishedVersion(
      collectionNames: Set[String],
      subsystem: String,
      maybeComponent: Option[String]
  ): Option[String] = {
    val path     = maybeComponent.fold(subsystem)(compName => s"$subsystem.$compName")
    val collName = versionCollectionName(path)
    if (collectionNames.contains(collName)) {
      val coll = db.collection[BSONCollection](collName)

      coll
        .find(queryAny, Option.empty[JsObject])
        .sort(BSONDocument(idKey -> -1))
        .one[BSONDocument]
        .await
        .map(_.string(versionStrKey))
        .head
    }
    else None
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
  private[db] def getVersionOf(coll: BSONCollection, version: Int): BSONDocument = {
    // Get a previously published version from $coll.v
    def getPublishedDoc: BSONDocument = {
      val v     = db.collection[BSONCollection](versionCollectionName(coll.name))
      val query = BSONDocument(versionKey -> version)
      v.find(query, Option.empty[JsObject]).one[BSONDocument].await.getOrElse(queryAny)
    }
    // Note: Doc might not exist in current version, but exist in an older, published version
    coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await match {
      case Some(doc) =>
        val currentVersion = doc.int(versionKey).get
        if (version == currentVersion) doc else getPublishedDoc

      case None =>
        getPublishedDoc
    }
  }

  // Returns the JSON for the given version of the collection path
  private def getJsonWithoutVersionOrId(path: String, version: Int): JsValue = {
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
          .map(IcdPath.apply)
          .filter(p => p.parts.length == 3)
          .map(_.parts.tail.head)
          .distinct
          .sorted
      case None => Nil
    }
  }

  // Returns a list of IcdEntry objects for the given parts (one part for each component or subsystem)
  // The result is sorted so that the subsystem comes first.
  private def getEntries(parts: List[PartInfo]): List[ApiCollections] = {
    val paths = parts.map(_.path).map(IcdPath.apply)
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
   * @param maybePdfOptions options for generating HTML for PDF doc
   * @param fitsKeyMap FITS key info, if needed
   * @param includeOnly if not empty, include only the named models in the result ("subsystemModel", "publishModel", etc.)
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  def getModels(
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions] = None,
      fitsKeyMap: FitsKeyMap = Map.empty,
      includeOnly: Set[String] = Set.empty
  ): List[IcdModels] = {

    // XXX TODO FIXME: For Scala3: Use enum for includeOnly

    // Return an object that holds all the model classes associated with a single ICD entry.
    def makeModels(versionMap: Map[String, Int], entry: ApiCollections): IcdModels = {
      def getDocVersion(coll: BSONCollection): BSONDocument = getVersionOf(coll, versionMap(coll.name))

      val subsystemModel: Option[SubsystemModel] =
        if (includeOnly.isEmpty || includeOnly.contains("subsystemModel"))
          entry.subsystem.flatMap(coll => SubsystemModelBsonParser(getDocVersion(coll), maybePdfOptions))
        else None

      val publishModel: Option[IcdModels.PublishModel] =
        if (includeOnly.isEmpty || includeOnly.contains("publishModel"))
          entry.publish.flatMap(coll =>
            PublishModelBsonParser(
              getDocVersion(coll),
              maybePdfOptions,
              query.getAllObserveEvents(maybePdfOptions),
              fitsKeyMap,
              Some(sv)
            )
          )
        else None

      val subscribeModel: Option[IcdModels.SubscribeModel] =
        if (includeOnly.isEmpty || includeOnly.contains("subscribeModel"))
          entry.subscribe.flatMap(coll => SubscribeModelBsonParser(getDocVersion(coll), maybePdfOptions))
        else None

      val commandModel: Option[IcdModels.CommandModel] =
        if (includeOnly.isEmpty || includeOnly.contains("commandModel"))
          entry.command.flatMap(coll => parser.CommandModelBsonParser(getDocVersion(coll), maybePdfOptions))
        else None

      val componentModel: Option[IcdModels.ComponentModel] =
        if (includeOnly.isEmpty || includeOnly.contains("componentModel"))
          entry.component.flatMap(coll => ComponentModelBsonParser(getDocVersion(coll), maybePdfOptions, sv.maybeVersion))
        else None

      val serviceMap = entry.serviceMap.view.mapValues(getDocVersion).toMap
      val serviceModel: Option[IcdModels.ServiceModel] =
        if (includeOnly.isEmpty || includeOnly.contains("serviceModel"))
          entry.services.flatMap(coll => ServiceModelBsonParser(db, getDocVersion(coll), serviceMap, maybePdfOptions))
        else None

      val alarmsModel: Option[IcdModels.AlarmsModel] =
        if (includeOnly.isEmpty || includeOnly.contains("alarmsModel"))
          entry.alarms.flatMap(coll => AlarmsModelBsonParser(getDocVersion(coll), maybePdfOptions))
        else None

      val icdModels: List[IcdModels.IcdModel] =
        if (includeOnly.isEmpty || includeOnly.contains("icdModels"))
          entry.icds.flatMap(coll => IcdModelBsonParser(getDocVersion(coll), maybePdfOptions))
        else Nil

      IcdModels(subsystemModel, componentModel, publishModel, subscribeModel, commandModel, alarmsModel, serviceModel, icdModels)
    }

    getVersion(sv) match {
      case Some(versionInfo) =>
        val versionMap = versionInfo.parts.map(v => v.path -> v.version).toMap
        val allEntries = getEntries(versionInfo.parts)
        // Note: allEntries is sorted by parts length, so subsystem model comes first
        val entries = if (includeOnly.contains("subsystemModel")) allEntries.take(1) else allEntries
        entries.map(makeModels(versionMap, _))
      case None =>
        println(s"$sv not found in the icd database.")
        Nil
    }
  }

  /**
   * Returns allModelsList if sv.component is empty, otherwise a list with just the given component models
   * @param allModelsList a list of all component models in the subsystem
   * @param sv            the subsystem
   * @param maybePdfOptions options for html/pdf gen
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  private def getModelsForComponents(
      allModelsList: List[IcdModels],
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions]
  ): List[IcdModels] = {
    sv.maybeComponent match {
      case None => allModelsList
      case Some(compName) =>
        allModelsList.filter(x => x.componentModel.exists(_.component == compName))
    }
  }

  /**
   * Query the database for information about all the subsystem's components
   * (Or the given component)
   * and return the icd models, resolving any refs.
   *
   * @param sv the subsystem
   * @param maybePdfOptions used for formatting
   * @return a list of IcdModels for the given version of the subsystem or component
   */
  def getResolvedModels(
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): List[IcdModels] = {
    val allComponentNames =
      if (sv.maybeComponent.nonEmpty) sv.maybeComponent.toList
      else getComponentNames(SubsystemWithVersion(sv.subsystem, sv.maybeVersion, None))
    val allComponentSvs = allComponentNames.map(component => SubsystemWithVersion(sv.subsystem, sv.maybeVersion, Some(component)))
    val allIcdModels = allComponentSvs.flatMap(compSv =>
      getModels(
        compSv,
        maybePdfOptions,
        fitsKeyMap
      )
    )
    val icdModels = getModelsForComponents(allIcdModels, sv, maybePdfOptions)
    Resolver(allIcdModels).resolve(icdModels)
  }

  /**
   * Returns the model for the given (or current) version of the given subsystem
   *
   * @param sv the subsystem and version
   * @param maybePdfOptions used for formatting
   * @return the subsystem model
   */
  def getSubsystemModel(
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions]
  ): Option[SubsystemModel] = {
    getModels(sv, maybePdfOptions, Map.empty, Set("subsystemModel")).headOption.flatMap(_.subsystemModel)
  }

  /**
   * Gets information about the ICD between the two subsystems
   *
   * @param sv the subsystem and version
   * @param tv the target subsystem and version
   * @param maybePdfOptions used for formatting
   * @return the list of icd models that correspond to the two subsystems
   */
  def getIcdModels(sv: SubsystemWithVersion, tv: SubsystemWithVersion, maybePdfOptions: Option[PdfOptions]): List[IcdModel] = {
    val svIcds = getModels(sv, maybePdfOptions, includeOnly = Set("icdModels")).headOption.toList
      .flatMap(_.icdModels)
      .filter(_.targetSubsystem == tv.subsystem)
    val tvIcds = getModels(tv, maybePdfOptions, includeOnly = Set("icdModels")).headOption.toList
      .flatMap(_.icdModels)
      .filter(_.targetSubsystem == sv.subsystem)
    svIcds ::: tvIcds
  }

  /**
   * Returns the component model for the given (or current) version of the given subsystem
   * (Called when sv.maybeComponent is defined, so only one component is in the models list)
   *
   * @param sv the subsystem and version
   * @return the subsystem model
   */
  def getComponentModel(sv: SubsystemWithVersion, maybePdfOptions: Option[PdfOptions]): Option[ComponentModel] = {
    val componentModel = getModels(sv, maybePdfOptions = maybePdfOptions, includeOnly = Set("componentModel")).headOption
      .flatMap(_.componentModel)
    if (componentModel.isEmpty && sv.maybeComponent.isDefined) {
      // Add a dummy component description if none was found
      val name = sv.maybeComponent.get
      Some(
        ComponentModel(
          "?",
          sv.subsystem,
          name,
          s"$name: Missing component title",
          s"$name: Missing component description",
          IcdValidator.currentSchemaVersion,
          ""
        )
      )
    }
    else componentModel
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
   * @param commit       the Git commit id (used to check for changes in master branch)
   */
  def publishApi(
      subsystem: String,
      maybeVersion: Option[String],
      majorVersion: Boolean,
      comment: String,
      username: String,
      date: DateTime,
      commit: String
  ): Unit = {
    val collectionNames = getCollectionNames

    // Save any of the subsystem's collections that changed
    def saveChangedCollections(): List[(String, Int)] = {
      val icdPaths = collectionNames.filter(isStdSet).map(IcdPath.apply).filter(_.subsystem == subsystem)
      val paths    = icdPaths.map(_.path).toList ++ getOpenApiCollectionPaths(collectionNames, icdPaths)
      for (path <- paths) yield {
        val coll    = db.collection[BSONCollection](path)
        val obj     = coll.find(queryAny, Option.empty[JsObject]).one[BSONDocument].await.get
        val version = obj.int(versionKey).get
//        val id              = obj.getAsOpt[BSONObjectID](idKey).get
        val id              = obj.get(idKey).get
        val versionCollName = versionCollectionName(path)
        val exists          = collectionNames.contains(versionCollName)
        val lastVersion = if (!exists || diff(db(versionCollName), obj).isDefined) {
          // New or modified: Update version history
          val v = db.collection[BSONCollection](versionCollName)
          if (exists) {
            // avoid duplicate key
            v.findAndRemove(BSONDocument(idKey -> id), None, None, WriteConcern.Default, None, None, Nil).await
          }
          v.insert.one(obj).await
          // increment version for unpublished working copy
          val mod = BSONDocument("$set" -> BSONDocument(versionKey -> (version + 1)))
          coll.update.one(queryAny, mod).await
          // return previous version
          version
        }
        else {
          // return previous version (version number was incremented when ingesting the model file)
          version - 1
        }
        (path, lastVersion)
      }
    }

    // Add to collection of published subsystem versions
    val versions = saveChangedCollections()
    newVersion(collectionNames, subsystem, maybeVersion, None, versions, comment, username, majorVersion, date, commit)
    getComponentNames(SubsystemWithVersion(subsystem, None, None)).foreach { name =>
      val prefix       = s"$subsystem.$name."
      val compVersions = versions.filter(p => p._1.startsWith(prefix))
      newVersion(
        collectionNames,
        subsystem,
        maybeVersion,
        Some(name),
        compVersions,
        comment,
        username,
        majorVersion,
        date,
        commit
      )
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
   * @param date             publish date for this version
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

//    // Only add an ICD version if the referenced subsystem and target versions exist
//    val subsystemVersions = getVersions(subsystem)
//    val targetVersions    = getVersions(target)
//    if (
//      subsystemVersions.exists(_.maybeVersion.contains(subsystemVersion)) && targetVersions.exists(
//        _.maybeVersion.contains(targetVersion)
//      )
//    ) {
      val obj = BSONDocument(
        versionStrKey       -> icdVersion,
        subsystemKey        -> subsystem,
        subsystemVersionKey -> subsystemVersion,
        targetKey           -> target,
        targetVersionKey    -> targetVersion,
        userKey             -> user,
        commentKey          -> comment,
        dateKey             -> BSONDateTime(date.getMillis)
      )
      db.collection[BSONCollection](icdCollName).insert.one(obj).await
//    }
//    else {
//      println(
//        s"Warning: Not adding ICD version $icdVersion between $subsystem-$subsystemVersion and $target-$targetVersion, since not all referenced subsystem versions exist"
//      )
//    }
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
          val subsystem = doc.string(subsystemKey).get
          val target    = doc.string(targetKey).get
          IcdName(subsystem, target)
        }
        .distinct
        .sorted
    }
    else Nil
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
          import reactivemongo.api.bson.*
          val icdVersion       = doc.string(versionStrKey).get
          val subsystem        = doc.string(subsystemKey).get
          val subsystemVersion = doc.string(subsystemVersionKey).get
          val target           = doc.string(targetKey).get
          val targetVersion    = doc.string(targetVersionKey).get
          val user             = doc.string(userKey).get
          val comment          = doc.string(commentKey).get
          val date             = new DateTime(doc.getAsOpt[BSONDateTime](dateKey).get.value, DateTimeZone.UTC).toString()
          IcdVersionInfo(
            IcdVersion(icdVersion, subsystem, subsystemVersion, target, targetVersion),
            user,
            comment,
            date
          )
        }
    }
    else Nil
  }

  /**
   * Returns a list of components not belonging to the given subsystems with versions.
   *
   * @param versionedSubsystems    a list of subsystems whose versions should be used to search
   *                      (For other subsystems, the latest version is used)
   */
  private def getUnversionedComponents(
      versionedSubsystems: List[SubsystemWithVersion],
      maybePdfOptions: Option[PdfOptions]
  ): List[ComponentModel] = {
    query
      .getComponents(maybePdfOptions)
      .filter(c => !versionedSubsystems.exists(sv => sv.subsystem == c.subsystem))
  }

  /**
   * Returns a list describing the components that subscribe to the given event, currentState, etc.
   * This function takes a list of subsystems that need to have the given version (The rest are
   * assumed to be the current versions).
   *
   * @param path          full path name of value (prefix + name)
   * @param subscribeType events, currentState, etc...
   * @param subsystems    a list of subsystems whose versions should be used to search
   *                      (For other subsystems, the latest version is used)
   */
  def subscribes(
      path: String,
      subscribeType: PublishType,
      maybePdfOptions: Option[PdfOptions],
      subsystems: List[SubsystemWithVersion]
  ): List[Subscribed] = {
    val versionedSubsystems = subsystems.filter(_.maybeVersion.isDefined)
    val components1         = getUnversionedComponents(versionedSubsystems, maybePdfOptions)
    val subscribeInfo1      = components1.map(c => query.getSubscribeInfo(c, maybePdfOptions))
    val subscribeInfo2 = for {
      sv            <- versionedSubsystems
      componentName <- getComponentNames(sv)
      models <- getModels(
        sv.copy(maybeComponent = Some(componentName)),
        maybePdfOptions = maybePdfOptions,
        includeOnly = Set("componentModel", "subscribeModel")
      )
      componentModel <- models.componentModel
      subscribeModel <- models.subscribeModel
    } yield {
      SubscribeInfo(componentModel, query.getSubscribedTo(componentModel, Some(subscribeModel)))
    }
    val subscribeInfo = (subscribeInfo1 ++ subscribeInfo2).distinct

    for {
      i <- subscribeInfo
      s <- i.subscribesTo.filter(sub => sub.path == path && sub.subscribeType == subscribeType)
    } yield s
  }

  /**
   * Returns a list of components that send the given command to the given component/subsystem
   * This function takes a list of subsystems that need to have the given version (The rest are
   * assumed to be the current versions).
   *
   * @param subsystem   the target component's subsystem
   * @param component   the target component
   * @param commandName the name of the command being sent
   * @param subsystems    a list of subsystems whose versions should be used to search
   *                      (For other subsystems, the latest version is used)
   * @return list containing one item for each component that sends the command
   */
  def getCommandSenders(
      subsystem: String,
      component: String,
      commandName: String,
      maybePdfOptions: Option[PdfOptions],
      subsystems: List[SubsystemWithVersion]
  ): List[ComponentModel] = {
    val versionedSubsystems = subsystems.filter(_.maybeVersion.isDefined)
    val components1 = for {
      componentModel <- getUnversionedComponents(versionedSubsystems, maybePdfOptions)
      commandModel   <- query.getCommandModel(componentModel, maybePdfOptions)
      _              <- commandModel.send.find(s => s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield componentModel

    val components2 = for {
      sv            <- versionedSubsystems
      componentName <- getComponentNames(sv)
      models <- getModels(
        sv.copy(maybeComponent = Some(componentName)),
        maybePdfOptions = maybePdfOptions,
        includeOnly = Set("componentModel", "commandModel")
      )
      componentModel <- models.componentModel
      commandModel   <- models.commandModel
      _              <- commandModel.send.find(s => s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield {
      componentModel
    }

    (components1 ++ components2).distinct
  }

  /**
   * Returns a list of components that require the given service from the given component/subsystem
   * This function takes a list of subsystems that need to have the given version (The rest are
   * assumed to be the current versions).
   *
   * @param subsystem   the service provider subsystem
   * @param component   the service provider component
   * @param serviceName the name of the service
   * @param subsystems    a list of subsystems whose versions should be used to search
   *                      (For other subsystems, the latest version is used)
   * @return list containing one item for each component that requires the service
   */
  def getServiceClients(
      subsystem: String,
      component: String,
      serviceName: String,
      maybePdfOptions: Option[PdfOptions],
      subsystems: List[SubsystemWithVersion]
  ): List[ComponentModel] = {
    val versionedSubsystems = subsystems.filter(_.maybeVersion.isDefined)
    val components1 = for {
      componentModel <- getUnversionedComponents(versionedSubsystems, maybePdfOptions)
      serviceModel   <- query.getServiceModel(componentModel, maybePdfOptions)
      _ <- serviceModel.requires.find(s => s.subsystem == subsystem && s.component == component && s.name == serviceName)
    } yield componentModel

    val components2 = for {
      sv            <- versionedSubsystems
      componentName <- getComponentNames(sv)
      models <- getModels(
        sv.copy(maybeComponent = Some(componentName)),
        maybePdfOptions = maybePdfOptions,
        includeOnly = Set("componentModel", "serviceModel")
      )
      componentModel <- models.componentModel
      serviceModel   <- models.serviceModel
      _ <- serviceModel.requires.find(s => s.subsystem == subsystem && s.component == component && s.name == serviceName)
    } yield {
      componentModel
    }

    (components1 ++ components2).distinct
  }

  /**
   * Gets the given ServiceModel, using the given subsystem versions, if needed
   * @param subsystem   the service provider subsystem
   * @param componentName   the service provider component
   * @param subsystems    a list of subsystems whose versions should be used to search
   *                      (For other subsystems, the latest version is used)
   * @return the service model if found
   */
  def getServiceModel(
      subsystem: String,
      componentName: String,
      maybePdfOptions: Option[PdfOptions],
      subsystems: List[SubsystemWithVersion]
  ): Option[ServiceModel] = {
    val versionedSubsystems = subsystems.filter(_.maybeVersion.isDefined)
    versionedSubsystems.find(_.subsystem == subsystem) match {
      case Some(sv) =>
        getModels(
          sv.copy(maybeComponent = Some(componentName)),
          maybePdfOptions = maybePdfOptions,
          includeOnly = Set("serviceModel")
        ).flatMap(_.serviceModel).headOption
      case None =>
        query.getServiceModel(subsystem, componentName, maybePdfOptions)
    }
  }

  /**
   * Returns the model object for the component with the given name.
   * This function takes a list of subsystems that need to have the given version (The rest are
   * assumed to be the current versions).
   */
  def getComponentModel(
      subsystem: String,
      componentName: String,
      maybePdfOptions: Option[PdfOptions],
      subsystems: List[SubsystemWithVersion]
  ): Option[ComponentModel] = {
    val versionedSubsystems = subsystems.filter(_.maybeVersion.isDefined)
    versionedSubsystems.find(_.subsystem == subsystem) match {
      case Some(sv) =>
        getModels(
          sv.copy(maybeComponent = Some(componentName)),
          maybePdfOptions = maybePdfOptions,
          includeOnly = Set("componentModel")
        ).flatMap(_.componentModel).headOption
      case None =>
        query.getComponentModel(subsystem, componentName, maybePdfOptions)
    }

  }

}
