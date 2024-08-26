package csw.services.icd.db

import csw.services.icd.*
import csw.services.icd.StdName.*
import csw.services.icd.db.parser.{
  AlarmsModelBsonParser,
  CommandModelBsonParser,
  ComponentModelBsonParser,
  IcdModelBsonParser,
  PublishModelBsonParser,
  ServiceModelBsonParser,
  SubscribeModelBsonParser,
  SubsystemModelBsonParser
}
import icd.web.shared.ComponentInfo.*
import icd.web.shared.{IcdModels, PdfOptions, SubsystemWithVersion}
import icd.web.shared.IcdModels.*
import play.api.libs.json.JsObject
import reactivemongo.api.DB
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.play.json.compat.*
import bson2json.*
import csw.services.icd.db.IcdDbDefaults.{backupCollSuffix, tmpCollSuffix}
import csw.services.icd.db.IcdVersionManager.versionSuffix
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import lax.*
import json2bson.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions

object IcdDbQuery {
  // Set of standard ICD model parts: subsystem, component, publish, subscribe, command
  private val stdSet: Set[String] = stdNames.map(_.modelBaseName).toSet

  // True if the named collection represents an ICD model (has one of the standard names)
  def isStdSet(name: String): Boolean =
    stdSet.exists(s => name.endsWith(s".$s"))

  // for working with dot separated paths
  private[db] case class IcdPath(path: String) {
    // The logical parts of a collection path: [subsystem, component, modelBaseName?, openApiName?]
    val parts: List[String] = {
      // allow embedded dots in component name
      val l         = path.split("\\.").toList
      val subsystem = l.head
      if (!path.contains(".service.")) {
        // For example: $subsystem.$component.publish
        val component     = l.tail.dropRight(1).mkString(".")
        val modelBaseName = l.reverse.head
        if (component.nonEmpty)
          List(subsystem, component, modelBaseName)
        else
          List(subsystem, modelBaseName)
      }
      else {
        // OpenApi collections referred to in service model
        // For example: $subsystem.$component.service.$name (component can contain dots)
        val modelBaseName = "service"
        val i             = l.indexOf(modelBaseName)
        val component     = l.tail.dropRight(l.size - i).mkString(".")
        // XXX TODO FIXME: concat rest of parts?
        val openApiName   = l(i + 1)
        List(subsystem, component, modelBaseName, openApiName)
      }
    }

    // XXX TODO FIXME
    // The common path for an assembly, HCD, sequencer, etc.
    val component: String = if (parts.size < 4) parts.dropRight(1).mkString(".") else s"${parts.head}.${parts.tail.head}"

    // The top level subsystem collection name
    val subsystem: String = parts.head
  }

  // Returns a list of OpenApi collections that store the JSON (possibly converted from YAML files)
  // referenced in the service-model.conf file
  def getOpenApiCollectionPaths(collectionNames: Set[String], icdPaths: Set[IcdPath]): List[String] = {
    val maybeServiceModelPath = icdPaths.filter(_.parts.reverse.head == serviceFileNames.modelBaseName)
    maybeServiceModelPath.toList.flatMap { serviceModelPath =>
      collectionNames.toList.filter(s => s.startsWith(s"${serviceModelPath.path}.") && !s.endsWith(versionSuffix))
    }
  }

  // Lists available db collections related to an API
  private[db] case class ApiCollections(
      name: String,
      subsystem: Option[BSONCollection],
      component: Option[BSONCollection],
      publish: Option[BSONCollection],
      subscribe: Option[BSONCollection],
      command: Option[BSONCollection],
      alarms: Option[BSONCollection],
      services: Option[BSONCollection],
      serviceMap: Map[String, BSONCollection],
      icds: List[BSONCollection]
  ) {

    // Returns all collections belonging to this entry
    def getCollections: List[BSONCollection] = {
      (subsystem ++ component ++ publish ++ subscribe ++ command ++ alarms ++ services ++ serviceMap.values.toList).toList
    }
  }

  // Returns an IcdEntry for the given collection path
  private[db] def getEntry(db: DB, name: String, paths: List[String]): ApiCollections = {
    ApiCollections(
      name = name,
      subsystem = paths.find(_.endsWith(".subsystem")).map(db(_)),
      component = paths.find(_.endsWith(".component")).map(db(_)),
      publish = paths.find(_.endsWith(".publish")).map(db(_)),
      subscribe = paths.find(_.endsWith(".subscribe")).map(db(_)),
      command = paths.find(_.endsWith(".command")).map(db(_)),
      alarms = paths.find(_.endsWith(".alarm")).map(db(_)),
      services = paths.find(_.endsWith(".service")).map(db(_)),
      serviceMap = paths.filter(_.contains(".service.")).map(s => s -> db(s)).toMap,
      icds = paths.filter(_.endsWith("-icd")).map(db(_))
    )
  }

  private[db] def getSubsystemCollectionName(subsystem: String): String = s"$subsystem.subsystem"

  private[db] def getComponentCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.component"

  private[db] def getPublishCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.publish"

  private[db] def getAlarmsCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.alarm"

  private[db] def getServiceCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.service"

  private[db] def getSubscribeCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.subscribe"

  private[db] def getCommandCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.command"

  private[db] def getIcdCollectionNames(subsystem: String): List[String] =
    Subsystems.allSubsystems.filter(_ != subsystem).map(s => s"$subsystem.$s-icd")

  /**
   * Describes a component in a subsystem
   */
  case class Component(componentName: String, subsystem: String)

  /**
   * Describes a published item
   *
   * @param publishType one of ObserveEvents, Events, Alarms, etc.
   * @param name        the name of the item being published
   * @param description description of the published item
   */
  case class Published(publishType: PublishType, name: String, description: String)

  /**
   * Describes what values a component publishes
   *
   * @param componentName component (HCD, assembly, ...) name
   * @param prefix        component prefix
   * @param publishes     list of names (without prefix) of published items (events, alarms, etc.)
   */
  case class PublishInfo(componentName: String, prefix: String, publishes: List[Published])

  /**
   * Describes what values a component subscribes to
   *
   * @param component    component (HCD, assembly, ...) model
   * @param subscribesTo list of types and names (with prefix) of items the component subscribes to
   */
  case class SubscribeInfo(component: ComponentModel, subscribesTo: List[Subscribed])

  /**
   * Describes a subscription
   *
   * @param component          The subscriber's component model
   * @param subscribeModelInfo from the subscribe model
   * @param subscribeType      one of ObserveEvents, Events, Alarms, etc.
   * @param path               the path name (component-prefix.name) of the item being subscribed to
   */
  case class Subscribed(
      component: ComponentModel,
      subscribeModelInfo: SubscribeModelInfo,
      subscribeType: PublishType,
      path: String
  )
}

/**
 * Support for querying the ICD database
 * (Note: This class works on the current, unpublished versions. See IcdVersionManager for use with versions.)
 *
 * @param db the icd DB handle
 * @param admin the admin DB handle
 * @param maybeSubsystems if defined, limit the list of subsystems searched
 */
//noinspection DuplicatedCode
case class IcdDbQuery(db: DB, admin: DB, maybeSubsystems: Option[List[String]]) {
  import IcdDbQuery.*

  // Search only the given subsystems, or all subsystems, if maybeSubsystems is empty
  private[db] def collectionNameFilter(collName: String): Boolean = {
    if (maybeSubsystems.isEmpty) true
    else {
      val baseName = collName.split('.').head
      maybeSubsystems.get.contains(baseName)
    }
  }
  private[db] def collectionExists(name: String): Boolean = getCollectionNames.contains(name)

  private[db] def getAllCollectionNames: List[String] = {
    db.collectionNames.await
  }

  private[db] def getCollectionNames: Set[String] = {
    getAllCollectionNames.filter(collectionNameFilter).toSet
  }

  // List of predefined observe events (defined in ESW model files)
  private[db] def getAllObserveEvents(maybePdfOptions: Option[PdfOptions]): Map[String, IcdModels.EventModel] = {
    getAllCollectionNames
      .filter(s => s.startsWith("ESW.") && s.endsWith("Lib.publish"))
      .flatMap(collName =>
        collectionHead(db(collName)).flatMap(PublishModelBsonParser(_, maybePdfOptions, Map.empty, Map.empty, None))
      )
      .flatMap(publishModel => publishModel.eventList)
      .map(eventModel => eventModel.name -> eventModel)
      .toMap
  }

  private[db] def getEntries(paths: List[IcdPath]): List[ApiCollections] = {
    val compMap = paths.map(p => (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key => getEntry(db, key, compMap(key))).toList
    // added ".v" below since IdbPath expects something after $subsystem.$component (component can have embedded dots)
    entries.sortBy(entry => (IcdPath(entry.name + ".v").parts.length, entry.name))
  }

  private[db] def getEntries: List[ApiCollections] = {
    val collectionNames = getCollectionNames
    val icdPaths        = collectionNames.filter(isStdSet).map(IcdPath.apply)
    val openApiPaths = getOpenApiCollectionPaths(collectionNames, icdPaths).map(IcdPath.apply)
    val paths        = icdPaths.toList ++ openApiPaths
    getEntries(paths)
  }

  private def collectionHead(coll: BSONCollection): Option[BSONDocument] = {
    coll.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
  }

  /**
   * Returns a list of models, one for each component in the db
   */
  def getComponents(maybePdfOptions: Option[PdfOptions]): List[ComponentModel] = {
    val x =
      for (entry <- getEntries if entry.component.isDefined)
        yield {
          val coll = entry.component.get
          val doc  = coll.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await.get
          ComponentModelBsonParser(doc, maybePdfOptions)
        }
    x.flatten
  }

  // Returns an IcdEntry object for the given component name, if found
  private[db] def entryForComponentName(subsystem: String, component: String): ApiCollections = {
    ApiCollections(
      name = s"$subsystem.$component",
      subsystem = getSubsystemCollection(subsystem),
      component = getComponentCollection(subsystem, component),
      publish = getPublishCollection(subsystem, component),
      subscribe = getSubscribeCollection(subsystem, component),
      command = getCommandCollection(subsystem, component),
      alarms = getAlarmsCollection(subsystem, component),
      services = getServiceCollection(subsystem, component),
      serviceMap = Map.empty,
      icds = Nil
    )
  }

  // Returns an IcdEntry object for the given subsystem name, if found
  private[db] def entryForSubsystemName(subsystem: String): ApiCollections = {
    ApiCollections(
      name = subsystem,
      subsystem = getSubsystemCollection(subsystem),
      component = None,
      publish = None,
      subscribe = None,
      command = None,
      alarms = None,
      services = None,
      serviceMap = Map.empty,
      icds = getIcdCollections(subsystem)
    )
  }

  /**
   * Returns a list of component model objects, one for each component matching the given condition in the database
   *
   * @param query restricts the components returned (a MongoDBObject, for example)
   */
  private def queryComponents(query: BSONDocument, maybePdfOptions: Option[PdfOptions]): List[ComponentModel] = {
    getEntries.flatMap {
      _.component.flatMap { coll =>
        val maybeDoc = coll.find(query, Option.empty[JsObject]).one[BSONDocument].await
        maybeDoc.flatMap(ComponentModelBsonParser(_, maybePdfOptions))
      }
    }
  }

  /**
   * Returns a list of component model objects, one for each component ICD of the given type in the database
   *
   * @param componentType restricts the type of components returned (one of: Assembly, HCD, Sequencer, etc.)
   */
  def getComponents(componentType: String, maybePdfOptions: Option[PdfOptions]): List[ComponentModel] =
    queryComponents(BSONDocument("componentType" -> componentType), maybePdfOptions)

  /**
   * Returns a list of all the component names in the DB
   */
  def getComponentNames(maybeSubsystem: Option[String]): List[String] = {
    getComponents(None)
      .filter(m => maybeSubsystem.isEmpty || maybeSubsystem.contains(m.subsystem))
      .map(_.component)
      .sorted
  }

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getAssemblyNames(maybeSubsystem: Option[String]): List[String] = {
    getComponents("Assembly", None)
      .filter(m => maybeSubsystem.isEmpty || maybeSubsystem.contains(m.subsystem))
      .map(_.component)
      .sorted
  }

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getHcdNames(maybeSubsystem: Option[String]): List[String] = {
    getComponents("HCD", None)
      .filter(m => maybeSubsystem.isEmpty || maybeSubsystem.contains(m.subsystem))
      .map(_.component)
      .sorted
  }

  /**
   * Returns a list of all subsystem names in the database.
   */
  def getSubsystemNames: List[String] = {
    getEntries.flatMap { e =>
      e.subsystem.flatMap { coll =>
        val doc = coll.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await.get
        SubsystemModelBsonParser(doc, None).map(_.subsystem)
      }
    }
  }

  // --- Get collections for (unpublished) ICD parts ---

  private def getCollection(collName: String): Option[BSONCollection] = {
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getSubsystemCollection(subsystem: String): Option[BSONCollection] = {
    val collName = getSubsystemCollectionName(subsystem)
    getCollection(collName)
  }

  private def getComponentCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getComponentCollectionName(subsystem, component)
    getCollection(collName)
  }

  private def getPublishCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getPublishCollectionName(subsystem, component)
    getCollection(collName)
  }

  private def getAlarmsCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getAlarmsCollectionName(subsystem, component)
    getCollection(collName)
  }

  private def getServiceCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getServiceCollectionName(subsystem, component)
    getCollection(collName)
  }

  private def getIcdCollections(subsystem: String): List[BSONCollection] = {
    val collNames = getIcdCollectionNames(subsystem)
    collNames.flatMap { collName =>
      getCollection(collName)
    }
  }

  private def getSubscribeCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getSubscribeCollectionName(subsystem, component)
    getCollection(collName)
  }

  private def getCommandCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getCommandCollectionName(subsystem, component)
    getCollection(collName)
  }

//  /**
//   * Returns the model object for the subsystem with the given name
//   */
//  def getSubsystemModel(subsystem: String, maybePdfOptions: Option[PdfOptions]): Option[SubsystemModel] = {
//    val collName = getSubsystemCollectionName(subsystem)
//    if (collectionExists(collName)) {
//      val coll = db.collection[BSONCollection](collName)
//      collectionHead(coll).flatMap(SubsystemModelBsonParser(_, maybePdfOptions))
//    }
//    else None
//  }

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(subsystem: String, componentName: String, maybePdfOptions: Option[PdfOptions]): Option[ComponentModel] = {
    val collName = getComponentCollectionName(subsystem, componentName)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      collectionHead(coll).flatMap(ComponentModelBsonParser(_, maybePdfOptions))
    }
    else None
  }

  /**
   * Returns an object describing the items published by the named component
   */
  def getPublishModel(
      component: ComponentModel,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): Option[PublishModel] = {
    val collName = getPublishCollectionName(component.subsystem, component.component)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      val sv   = SubsystemWithVersion(component.subsystem, None, Some(component.component))
      collectionHead(coll).flatMap(
        PublishModelBsonParser(_, maybePdfOptions, getAllObserveEvents(maybePdfOptions), fitsKeyMap, Some(sv))
      )
    }
    else None
  }

  /**
   * Returns an object describing the alarms published by the named component
   */
  def getAlarmsModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[AlarmsModel] = {
    val collName = getAlarmsCollectionName(component.subsystem, component.component)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      collectionHead(coll).flatMap(AlarmsModelBsonParser(_, maybePdfOptions))
    }
    else None
  }

  /**
   * Returns an object describing the items subscribed to by the given component
   *
   * @param component the model for the component
   */
  def getSubscribeModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[SubscribeModel] = {
    val collName = getSubscribeCollectionName(component.subsystem, component.component)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      collectionHead(coll).flatMap(SubscribeModelBsonParser(_, maybePdfOptions))
    }
    else None
  }

  /**
   * Returns an object describing the "commands" defined for the named component in the named subsystem
   */
  def getCommandModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[CommandModel] =
    getCommandModel(component.subsystem, component.component, maybePdfOptions)

  def getCommandModel(subsystem: String, component: String, maybePdfOptions: Option[PdfOptions]): Option[CommandModel] = {
    val collName = getCommandCollectionName(subsystem, component)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      collectionHead(coll).flatMap(CommandModelBsonParser(_, maybePdfOptions))
    }
    else None
  }

  /**
   * Returns an object describing the "services" provided or required by the named component in the named subsystem
   */
  def getServiceModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[ServiceModel] =
    getServiceModel(component.subsystem, component.component, maybePdfOptions)

  def getServiceModel(subsystem: String, component: String, maybePdfOptions: Option[PdfOptions]): Option[ServiceModel] = {
    val collName = getServiceCollectionName(subsystem, component)
    if (collectionExists(collName)) {
      val coll = db.collection[BSONCollection](collName)
      collectionHead(coll).flatMap(ServiceModelBsonParser(db, _, Map.empty, maybePdfOptions))
    }
    else None
  }

  /**
   * Returns an object describing the named command, defined for the named component in the named subsystem
   */
  def getCommand(
      subsystem: String,
      component: String,
      commandName: String,
      maybePdfOptions: Option[PdfOptions]
  ): Option[ReceiveCommandModel] = {
    getCommandModel(subsystem, component, maybePdfOptions: Option[PdfOptions]).flatMap(_.receive.find(_.name == commandName))
  }

  /**
   * Returns a list of models for the given subsystem or component name,
   * based on the data in the database.
   * The list includes the model for the subsystem, followed
   * by any models for components that were defined in subdirectories
   * in the original files that were ingested into the database
   * (In this case the definitions are stored in sub-collections in the DB).
   *
   * Note: Use IcdVersionManager.getModels() to access a specific version of the data.
   *
   * @param subsystem the name of the subsystem (or component's subsystem)
   * @param component optional name of the component
   */
  def getModels(
      subsystem: String,
      component: Option[String] = None,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): List[IcdModels] = {

    // Holds all the model classes associated with a single API.
    def makeModels(entry: ApiCollections): IcdModels = {
      val subsystemModel: Option[SubsystemModel] =
        entry.subsystem.flatMap(coll => collectionHead(coll).flatMap(SubsystemModelBsonParser(_, maybePdfOptions)))
      val publishModel: Option[PublishModel] =
        entry.publish.flatMap(coll =>
          collectionHead(coll).flatMap(
            PublishModelBsonParser(
              _,
              maybePdfOptions,
              getAllObserveEvents(maybePdfOptions),
              fitsKeyMap,
              Some(SubsystemWithVersion(subsystem, None, component))
            )
          )
        )
      val subscribeModel: Option[SubscribeModel] =
        entry.subscribe.flatMap(coll => collectionHead(coll).flatMap(SubscribeModelBsonParser(_, maybePdfOptions)))
      val commandModel: Option[CommandModel] =
        entry.command.flatMap(coll => collectionHead(coll).flatMap(CommandModelBsonParser(_, maybePdfOptions)))
      val componentModel: Option[ComponentModel] =
        entry.component.flatMap(coll => collectionHead(coll).flatMap(ComponentModelBsonParser(_, maybePdfOptions)))
      val alarmsModel: Option[AlarmsModel] =
        entry.alarms.flatMap(coll => collectionHead(coll).flatMap(AlarmsModelBsonParser(_, maybePdfOptions)))
      val serviceModel: Option[ServiceModel] =
        entry.services.flatMap(coll =>
          collectionHead(coll).flatMap(ServiceModelBsonParser(db, _, Map.empty, maybePdfOptions))
        )
      val icdModels: List[IcdModel] =
        entry.icds.flatMap(coll => collectionHead(coll).flatMap(IcdModelBsonParser(_, maybePdfOptions)))
      IcdModels(subsystemModel, componentModel, publishModel, subscribeModel, commandModel, alarmsModel, serviceModel, icdModels)
    }

    val e =
      if (component.isDefined)
        entryForComponentName(subsystem, component.get)
      else entryForSubsystemName(subsystem)

    // Get the prefix for the related db sub-collections
    val prefix = e.name + "."
    val list   = for (entry <- getEntries if entry.name.startsWith(prefix)) yield makeModels(entry)
    makeModels(e) :: list
  }

  /**
   * Deletes the given component hierarchy.
   *
   * @param subsystem the component's subsystem
   * @param component the component to delete
   */
  def dropComponent(subsystem: String, component: String): Unit = {
    for (coll <- entryForComponentName(subsystem, component).getCollections) {
      coll.drop(failIfNotFound = false).await
    }
  }

  /**
   * Deletes the given subsystem hierarchy.
   *
   * @param subsystem the component's subsystem
   */
  def dropSubsystem(subsystem: String): Unit = {
    val paths = getCollectionNames.filter(name => name.startsWith(s"$subsystem."))
    paths.foreach { collName =>
      val coll = db.collection[BSONCollection](collName)
      coll.drop(failIfNotFound = false).await
    }
  }

  private def baseName(s: String, suffix: String) = s.dropRight(suffix.length)

  // Rename the given set of collections by adding or removing the given suffix.
  private[db] def renameCollections(
      paths: Set[String],
      suffix: String,
      removeSuffix: Boolean,
      dbName: String
  ): Set[String] = {
    paths.map { path =>
      val collName = if (removeSuffix) baseName(path, suffix) else s"$path$suffix"
      admin.renameCollection(dbName, path, collName, dropExisting = true).await
      collName
    }
  }

  // Delete the given collections
  private[db] def deleteCollections(paths: Set[String]): Unit = {
    paths.foreach { path =>
      val coll = db.collection[BSONCollection](path)
      coll.drop(failIfNotFound = false).await
    }
  }

  private[db] def getSubsystemCollectionNames(collectionNames: Set[String], subsystem: String): Set[String] = {
    collectionNames
      .filter(name =>
        name.startsWith(s"$subsystem.") && !name.endsWith(versionSuffix) && !name.endsWith(tmpCollSuffix) && !name.endsWith(
          backupCollSuffix
        )
      )
  }

  /**
   * If there were errors, delete the temp collections (that were created while ingesting the model files).
   * Otherwise, make backups of the existing collections before renaming the temp collections.
   * Returns a pair of (newCollections, backupCollections) so that post-ingest validation can clean up
   * on error.
   */
  def afterIngest(
      maybeSubsystem: Option[String],
      errors: Boolean,
      dbName: String,
      makeBackups: Boolean = true
  ): (Set[String], Set[String]) = {
    val collectionNames = getCollectionNames
    val tmpPaths = maybeSubsystem match {
      case Some(subsystem) => collectionNames.filter(name => name.startsWith(s"$subsystem.") && name.endsWith(tmpCollSuffix))
      case None            => collectionNames.filter(_.endsWith(tmpCollSuffix))
    }
    if (tmpPaths.nonEmpty) {
      if (errors) {
        // Delete the ingested collections and quit
        deleteCollections(tmpPaths)
        (Set.empty, Set.empty)
      }
      else {
        val paths = maybeSubsystem match {
          case Some(subsystem) => getSubsystemCollectionNames(collectionNames, subsystem)
          case None            => tmpPaths.map(baseName(_, tmpCollSuffix)).intersect(collectionNames)
        }
        // Backup all current collections for subsystem, so we can restore them in case of post-ingest validation error
        val backupCollections =
          if (makeBackups) {
            renameCollections(paths, backupCollSuffix, removeSuffix = false, dbName)
          }
          else {
            deleteCollections(paths)
            Set.empty[String]
          }
        // Rename the temp collection that were just ingested so that they are the current collections for the subsystem
        val newCollections = renameCollections(tmpPaths, tmpCollSuffix, removeSuffix = true, dbName)
        (newCollections, backupCollections)
      }
    }
    else (Set.empty, Set.empty)

  }

  /**
   * Returns a list of items published by the given component
   *
   * @param component the component's model
   */
  def getPublished(component: ComponentModel, maybePdfOptions: Option[PdfOptions], fitsKeyMap: FitsKeyMap): List[Published] = {
    val maybePublishModel = getPublishModel(component, maybePdfOptions, fitsKeyMap)
    val maybeAlarmsModel  = getAlarmsModel(component, maybePdfOptions)
    // TODO: Ignore alarms in publish-model.conf if alarm-model.conf is present? Or merge any alarms found?
//    val alarmList = maybeAlarmsModel.map(_.alarmList).getOrElse(maybePublishModel.map(_.alarmList).getOrElse(Nil))
    val alarmList = maybeAlarmsModel.toList.flatMap(_.alarmList) ++ maybePublishModel.toList.flatMap(_.alarmList)
    maybePublishModel match {
      case Some(publishModel) =>
        List(
          publishModel.eventList.map(i => Published(Events, i.name, i.description)),
          publishModel.observeEventList.map(i => Published(ObserveEvents, i.name, i.description)),
          publishModel.currentStateList.map(i => Published(CurrentStates, i.name, i.description)),
          alarmList.map(i => Published(Alarms, i.name, i.description))
        ).flatten
      case None => Nil
    }
  }

  /**
   * Returns a list describing what each component publishes
   */
  def getPublishInfo(subsystem: String, maybePdfOptions: Option[PdfOptions], fitsKeyMap: FitsKeyMap): List[PublishInfo] = {
    def getPublishInfo(c: ComponentModel): PublishInfo =
      PublishInfo(c.component, c.prefix, getPublished(c, maybePdfOptions, fitsKeyMap))

    getComponents(maybePdfOptions).filter(m => m.subsystem == subsystem).map(c => getPublishInfo(c))
  }

  /**
   * Returns a list of items the given component subscribes to
   */
  private[db] def getSubscribedTo(
      componentModel: ComponentModel,
      subscribeModel: Option[SubscribeModel]
  ): List[Subscribed] = {
    // Gets the full path of the subscribed item
    def getPath(i: SubscribeModelInfo): String = {
      val prefix = s"${i.subsystem}.${i.component}"
      s"$prefix.${i.name}"
    }

    subscribeModel match {
      case Some(subscribeModel) =>
        List(
          subscribeModel.eventList.map(i => Subscribed(componentModel, i, Events, getPath(i))),
          subscribeModel.observeEventList.map(i => Subscribed(componentModel, i, ObserveEvents, getPath(i))),
          subscribeModel.currentStateList.map(i => Subscribed(componentModel, i, CurrentStates, getPath(i))),
          subscribeModel.imageList.map(i => Subscribed(componentModel, i, Images, getPath(i)))
        ).flatten
      case None => Nil
    }
  }

  /**
   * Returns an object describing what the given component subscribes to
   */
  private[db] def getSubscribeInfo(c: ComponentModel, maybePdfOptions: Option[PdfOptions]): SubscribeInfo = {
    val maybeSubscribeModel = getSubscribeModel(c, maybePdfOptions)
    SubscribeInfo(c, getSubscribedTo(c, maybeSubscribeModel))
  }

  /**
   * Returns a list describing what each component subscribes to
   */
  def getSubscribeInfo(maybePdfOptions: Option[PdfOptions]): List[SubscribeInfo] = {
    getComponents(maybePdfOptions).map(c => getSubscribeInfo(c, maybePdfOptions))
  }
}
