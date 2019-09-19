package csw.services.icd.db

import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd.StdName._
import csw.services.icd.model._
import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels
import icd.web.shared.IcdModels._
import play.api.libs.json.Json
import reactivemongo.api.DefaultDB
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument
import reactivemongo.play.json._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.language.implicitConversions

object IcdDbQuery {
  // Set of standard ICD model parts: subsystem, component, publish, subscribe, command
  val stdSet: Set[String] = stdNames.map(_.modelBaseName).toSet

  // True if the named collection represents an ICD model (has one of the standard names)
  def isStdSet(name: String): Boolean =
    stdSet.exists(s => name.endsWith(s".$s"))

  // for working with dot separated paths
  private[db] case class IcdPath(path: String) {
    lazy val parts: List[String] = path.split("\\.").toList

    // The common path for an assembly, HCD, sequencer, etc.
    lazy val component: String = parts.dropRight(1).mkString(".")

    // The top level subsystem collection name
    lazy val subsystem: String = parts.head
  }

  // Lists available db collections related to an ICD
  private[db] case class IcdEntry(
      name: String,
      subsystem: Option[BSONCollection],
      component: Option[BSONCollection],
      publish: Option[BSONCollection],
      subscribe: Option[BSONCollection],
      command: Option[BSONCollection]
  ) {

    // Returns all collections belonging to this entry
    def getCollections: List[BSONCollection] = (subsystem ++ component ++ publish ++ subscribe ++ command).toList
  }

  // Returns an IcdEntry for the given collection path
  private[db] def getEntry(db: DefaultDB, name: String, paths: List[String]): IcdEntry = {
    IcdEntry(
      name = name,
      subsystem = paths.find(_.endsWith(".subsystem")).map(db(_)),
      component = paths.find(_.endsWith(".component")).map(db(_)),
      publish = paths.find(_.endsWith(".publish")).map(db(_)),
      subscribe = paths.find(_.endsWith(".subscribe")).map(db(_)),
      command = paths.find(_.endsWith(".command")).map(db(_))
    )
  }

  private[db] def getSubsystemCollectionName(subsystem: String): String = s"$subsystem.subsystem"

  private[db] def getComponentCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.component"

  private[db] def getPublishCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.publish"

  private[db] def getSubscribeCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.subscribe"

  private[db] def getCommandCollectionName(subsystem: String, component: String): String = s"$subsystem.$component.command"

  // Gets a Config object from a JSON string
  def getConfig(json: String): Config = {
    ConfigFactory.parseString(json)
  }

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

//  implicit def toBSONDocument(elements: Producer[BSONElement]*): BSONDocument = BSONDocument(elements: _*)

  // Parses the given json and returns a componnet model object
  def jsonToComponentModel(json: String): ComponentModel = {
    ComponentModelParser(getConfig(json))
  }

  // Parses the given json and returns a subsystem model object
  def jsonToSubsystemModel(json: String): SubsystemModel = {
    SubsystemModelParser(getConfig(json))
  }
}

/**
 * Support for querying the ICD database
 * (Note: This class works on the current, unpublished versions. See IcdVersionManager for use with versions.)
 *
 * @param db the DefaultDB handle
 */
//noinspection DuplicatedCode
case class IcdDbQuery(db: DefaultDB) {

  import IcdDbQuery._

  // XXX TODO FIXME: Pass in timeout or use async lib and make everything async
  private val timeout = 60.seconds

  // XXX TODO FIXME: Make more efficient (cache names?)
  private[db] def collectionExists(name: String): Boolean = getCollectionNames.contains(name)

  private[db] def getCollectionNames: Set[String] = Await.result(db.collectionNames, timeout).toSet

  private[db] def getEntries: List[IcdEntry] = {
    val paths   = getCollectionNames.filter(isStdSet).map(IcdPath).toList
    val compMap = paths.map(p => (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key => getEntry(db, key, compMap(key))).toList
    entries.sortBy(entry => (IcdPath(entry.name).parts.length, entry.name))
  }

  // XXX TODO FIXME: parsing to JSON and back to string and then to object!
   def collectionHeadToJson(collName: String): String = {
    val coll = db.collection[BSONCollection](collName)
    collectionHeadToJson(coll)
  }

  // XXX TODO FIXME
   def collectionHeadToJson(coll: BSONCollection): String = {
    val doc = Await.result(coll.find(BSONDocument(), None).one[BSONDocument], timeout).get
    Json.toJson(doc).toString()
  }

  // XXX TODO FIXME: Use play-json reads/writes instead of custonm parsers?
  /**
   * Returns a list of models, one for each component in the db
   */
  def getComponents: List[ComponentModel] = {
    for (entry <- getEntries if entry.component.isDefined)
      yield {
        val coll = entry.component.get
        val doc  = Await.result(coll.find(BSONDocument(), None).one[BSONDocument], timeout).get
        jsonToComponentModel(Json.toJson(doc).toString())
      }
  }

  // Returns an IcdEntry object for the given component name, if found
  private[db] def entryForComponentName(subsystem: String, component: String): IcdEntry = {
    IcdEntry(
      name = s"$subsystem.$component",
      subsystem = getSubsystemCollection(subsystem),
      component = getComponentCollection(subsystem, component),
      publish = getPublishCollection(subsystem, component),
      subscribe = getSubscribeCollection(subsystem, component),
      command = getCommandCollection(subsystem, component)
    )
  }

  // Returns an IcdEntry object for the given subsystem name, if found
  private[db] def entryForSubsystemName(subsystem: String): IcdEntry = {
    IcdEntry(subsystem, getSubsystemCollection(subsystem), None, None, None, None)
  }

  /**
   * Returns a list of component model objects, one for each component ICD matching the given condition in the database
   *
   * @param query restricts the components returned (a MongoDBObject, for example)
   */
  def queryComponents(query: BSONDocument): List[ComponentModel] = {
    val list = for (entry <- getEntries if entry.component.isDefined) yield {
      val coll = entry.component.get
      val data = Await.result(coll.find(query, None).one[BSONDocument], timeout)
      if (data.isDefined) {
        // XXX TODO FIXME: Parse using play-json rather then going back to String etc.?
        val json = Json.toJson(data.get).toString()
        Some(jsonToComponentModel(json))
      } else None
    }
    list.flatten
  }

  /**
   * Returns a list of component model objects, one for each component ICD of the given type in the database
   *
   * @param componentType restricts the type of components returned (one of: Assembly, HCD, Sequencer, etc.)
   */
  def getComponents(componentType: String): List[ComponentModel] =
    queryComponents(BSONDocument("componentType" -> componentType))

  /**
   * Returns a list of all the component names in the DB
   */
  def getComponentNames: List[String] = getComponents.map(_.component)

  /**
   * Returns a list of all the component names in the DB belonging to the given subsystem.
   * Note: This method assumes the current version of the subsystem.
   * Use IcdVersionManager.getComponentNames to access any version of the subsystem.
   */
  def getComponentNames(subsystem: String): List[String] = {
    getCollectionNames
      .filter(name => name.startsWith(s"$subsystem.") && !name.endsWith(s".${IcdVersionManager.versionColl}"))
      .map(IcdPath)
      .filter(p => p.parts.length == 3)
      .map(_.parts.tail.head)
      .toList
      .sorted
  }

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getAssemblyNames: List[String] = getComponents("Assembly").map(_.component)

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getHcdNames: List[String] = getComponents("HCD").map(_.component)

  /**
   * Returns a list of all subsystem names in the database.
   */
  def getSubsystemNames: List[String] = {
    val result = for (entry <- getEntries) yield {
      if (entry.subsystem.isDefined) {
        val coll = entry.subsystem.get
        val doc  = Await.result(coll.find(BSONDocument(), None).one[BSONDocument], timeout).get
        // XXX TODO FIXME: parsing to JSON and back to string and then to object!
        val json = Json.toJson(doc).toString()
        Some(jsonToSubsystemModel(json).subsystem)
      } else None
    }
    result.flatten.distinct
  }

  // --- Get collections for (unpublished) ICD parts ---

  private def getSubsystemCollection(subsystem: String): Option[BSONCollection] = {
    val collName = getSubsystemCollectionName(subsystem)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getComponentCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getComponentCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getPublishCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getPublishCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getSubscribeCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getSubscribeCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getCommandCollection(subsystem: String, component: String): Option[BSONCollection] = {
    val collName = getCommandCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  /**
   * Returns the model object for the subsystem with the given name
   */
  def getSubsystemModel(subsystem: String): Option[SubsystemModel] = {
    val collName = getSubsystemCollectionName(subsystem)
    if (collectionExists(collName)) {
      val json = collectionHeadToJson(collName)
      Some(SubsystemModelParser(getConfig(json)))
    } else None
  }

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(subsystem: String, componentName: String): Option[ComponentModel] = {
    val collName = getComponentCollectionName(subsystem, componentName)
    if (collectionExists(collName)) {
      val json = collectionHeadToJson(collName)
      Some(ComponentModelParser(getConfig(json)))
    } else None
  }

  /**
   * Returns an object describing the items published by the named component
   */
  def getPublishModel(component: ComponentModel): Option[PublishModel] = {
    val collName = getPublishCollectionName(component.subsystem, component.component)
    if (collectionExists(collName)) {
      val json = collectionHeadToJson(collName)
      Some(PublishModelParser(getConfig(json)))
    } else None
  }

  /**
   * Returns an object describing the items subscribed to by the given component
   *
   * @param component the model for the component
   */
  def getSubscribeModel(component: ComponentModel): Option[SubscribeModel] = {
    val collName = getSubscribeCollectionName(component.subsystem, component.component)
    if (collectionExists(collName)) {
      val json = collectionHeadToJson(collName)
      Some(SubscribeModelParser(getConfig(json)))
    } else None
  }

  /**
   * Returns an object describing the "commands" defined for the named component in the named subsystem
   */
  def getCommandModel(component: ComponentModel): Option[CommandModel] =
    getCommandModel(component.subsystem, component.component)

  def getCommandModel(subsystem: String, component: String): Option[CommandModel] = {
    val collName = getCommandCollectionName(subsystem, component)
    if (collectionExists(collName)) {
      val json = collectionHeadToJson(collName)
      Some(CommandModelParser(getConfig(json)))
    } else None
  }

  /**
   * Returns an object describing the named command, defined for the named component in the named subsystem
   */
  def getCommand(subsystem: String, component: String, commandName: String): Option[ReceiveCommandModel] = {
    getCommandModel(subsystem, component).flatMap(_.receive.find(_.name == commandName))
  }

  /**
   * Returns a list of components that send the given command to the given component/subsystem
   *
   * @param subsystem   the target component's subsystem
   * @param component   the target component
   * @param commandName the name of the command being sent
   * @return list containing one item for each component that sends the command
   */
  def getCommandSenders(subsystem: String, component: String, commandName: String): List[ComponentModel] = {
    for {
      componentModel <- getComponents
      commandModel   <- getCommandModel(componentModel)
      _              <- commandModel.send.find(s => s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield componentModel
  }

  // ---

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
  def getModels(subsystem: String, component: Option[String] = None): List[IcdModels] = {
    // Holds all the model classes associated with a single ICD entry.
    case class Models(entry: IcdEntry) extends IcdModels {
      override val subsystemModel: Option[SubsystemModel] =
        entry.subsystem.map(s => SubsystemModelParser(getConfig(collectionHeadToJson(s))))
      override val publishModel: Option[PublishModel] =
        entry.publish.map(s => PublishModelParser(getConfig(collectionHeadToJson(s))))
      override val subscribeModel: Option[SubscribeModel] =
        entry.subscribe.map(s => SubscribeModelParser(getConfig(collectionHeadToJson(s))))
      override val commandModel: Option[CommandModel] =
        entry.command.map(s => CommandModelParser(getConfig(collectionHeadToJson(s))))
      override val componentModel: Option[ComponentModel] =
        entry.component.map(s => ComponentModelParser(getConfig(collectionHeadToJson(s))))
    }

    val e =
      if (component.isDefined)
        entryForComponentName(subsystem, component.get)
      else entryForSubsystemName(subsystem)

    // Get the prefix for the related db sub-collections
    val prefix = e.name + "."
    val list   = for (entry <- getEntries if entry.name.startsWith(prefix)) yield Models(entry)
    Models(e) :: list
  }

  /**
   * Deletes the given component hierarchy. Use with caution!
   *
   * @param subsystem the component's subsystem
   * @param component the component to delete
   */
  def dropComponent(subsystem: String, component: String): Unit = {
    for (coll <- entryForComponentName(subsystem, component).getCollections) {
      coll.drop(failIfNotFound = false)
    }
  }

  /**
   * Returns a list of items published by the given component
   *
   * @param component the component's model
   */
  def getPublished(component: ComponentModel): List[Published] = {
    getPublishModel(component) match {
      case Some(publishModel) =>
        List(
          publishModel.eventList.map(i => Published(Events, i.name, i.description)),
          publishModel.observeEventList.map(i => Published(ObserveEvents, i.name, i.description)),
          publishModel.currentStateList.map(i => Published(CurrentStates, i.name, i.description)),
          publishModel.alarmList.map(i => Published(Alarms, i.name, i.description))
        ).flatten
      case None => Nil
    }
  }

  /**
   * Returns a list describing what each component publishes
   */
  def getPublishInfo(subsystem: String): List[PublishInfo] = {
    def getPublishInfo(c: ComponentModel): PublishInfo =
      PublishInfo(c.component, c.prefix, getPublished(c))

    getComponents.filter(m => m.subsystem == subsystem).map(c => getPublishInfo(c))
  }

  /**
   * Returns a list of items the given component subscribes to
   *
   * @param component the component model
   */
  private def getSubscribedTo(component: ComponentModel): List[Subscribed] = {
    // Gets the full path of the subscribed item
    def getPath(i: SubscribeModelInfo): String = {
      val pubComp = getComponentModel(i.subsystem, i.component)
      val prefix  = pubComp.map(_.prefix).getOrElse("")
      s"$prefix.${i.name}"
    }

    getSubscribeModel(component) match {
      case Some(subscribeModel) =>
        List(
          subscribeModel.eventList.map(i => Subscribed(component, i, Events, getPath(i))),
          subscribeModel.observeEventList.map(i => Subscribed(component, i, ObserveEvents, getPath(i))),
          subscribeModel.currentStateList.map(i => Subscribed(component, i, CurrentStates, getPath(i))),
          subscribeModel.alarmList.map(i => Subscribed(component, i, Alarms, getPath(i)))
        ).flatten
      case None => Nil
    }
  }

  /**
   * Returns an object describing what the given component subscribes to
   */
  private[db] def getSubscribeInfo(c: ComponentModel): SubscribeInfo = SubscribeInfo(c, getSubscribedTo(c))

  /**
   * Returns a list describing what each component subscribes to
   */
  def getSubscribeInfo: List[SubscribeInfo] = {
    getComponents.map(c => getSubscribeInfo(c))
  }

  /**
   * Returns a list describing the components that subscribe to the given value.
   *
   * @param path          full path name of value (prefix + name)
   * @param subscribeType events, alarm, etc...
   */
  def subscribes(path: String, subscribeType: PublishType): List[Subscribed] = {
    for {
      i <- getSubscribeInfo
      s <- i.subscribesTo.filter(sub => sub.path == path && sub.subscribeType == subscribeType)
    } yield s
  }
}
