package csw.services.icd.db

import com.mongodb.casbah.{ MongoCollection, MongoDB }
import com.mongodb.casbah.commons.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.{ ConfigFactory, Config }
import csw.services.icd.StdName._
import csw.services.icd.model._
import scala.language.implicitConversions

object IcdDbQuery {
  // Set of standard ICD model parts: subsystem, component, publish, subscribe, command
  val stdSet = stdNames.map(_.modelBaseName).toSet

  // True if the named collection represents an ICD model (has one of the standard names)
  def isStdSet(name: String): Boolean =
    stdSet.exists(s ⇒ name.endsWith(s".$s"))

  // for working with dot separated paths
  private[db] case class IcdPath(path: String) {
    lazy val parts = path.split("\\.").toList

    // The common path for an assembly, HCD, sequencer, etc.
    lazy val component = parts.dropRight(1).mkString(".")

    // The top level subsystem collection name
    lazy val subsystem = parts.head
  }

  // Lists available db collections related to an ICD
  private[db] case class IcdEntry(name: String, subsystem: Option[MongoCollection], component: Option[MongoCollection],
                                  publish: Option[MongoCollection], subscribe: Option[MongoCollection], command: Option[MongoCollection]) {

    // Returns all collections belonging to this entry
    def getCollections: List[MongoCollection] = (subsystem ++ component ++ publish ++ subscribe ++ command).toList
  }

  // Returns an IcdEntry for the given collection path
  private[db] def getEntry(db: MongoDB, name: String, paths: List[String]): IcdEntry = {
    IcdEntry(name = name,
      subsystem = paths.find(_.endsWith(".subsystem")).map(db(_)),
      component = paths.find(_.endsWith(".component")).map(db(_)),
      publish = paths.find(_.endsWith(".publish")).map(db(_)),
      subscribe = paths.find(_.endsWith(".subscribe")).map(db(_)),
      command = paths.find(_.endsWith(".command")).map(db(_)))
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

  // Types of published items
  sealed trait PublishType

  case object Telemetry extends PublishType

  case object Events extends PublishType

  case object EventStreams extends PublishType

  case object Alarms extends PublishType

  case object Health extends PublishType

  /**
   * Describes a component in a subsystem
   */
  case class Component(componentName: String, subsystem: String)

  /**
   * Describes a published item
   * @param publishType one of Telemetry, Events, Alarms, etc.
   * @param name the name of the item being published
   * @param description description of the published item
   */
  case class Published(publishType: PublishType, name: String, description: String)

  /**
   * Describes a published item along with the component that publishes it
   * @param componentName the publishing component
   * @param prefix the component's prefix
   * @param item description of the published item
   */
  case class PublishedItem(componentName: String, prefix: String, item: Published)

  /**
   * Describes what values a component publishes
   * @param componentName component (HCD, assembly, ...) name
   * @param prefix component prefix
   * @param publishes list of names (without prefix) of published items (telemetry, events, alarms, etc.)
   */
  case class PublishInfo(componentName: String, prefix: String, publishes: List[Published])

  /**
   * Describes what values a component subscribes to
   * @param component component (HCD, assembly, ...) model
   * @param subscribesTo list of types and names (with prefix) of items the component subscribes to
   */
  case class SubscribeInfo(component: ComponentModel, subscribesTo: List[Subscribed])

  /**
   * Describes a subscription
   *
   * @param componentName the name of the component that subscribes to the item
   * @param subsystem the component's subsystem
   * @param subscribeType one of Telemetry, Events, Alarms, etc.
   * @param name the name of the item being subscribed to
   */
  case class Subscribed(componentName: String, subsystem: String, subscribeType: PublishType, name: String)

  implicit def toDbObject(query: (String, Any)): DBObject = MongoDBObject(query)

  // Parses the given json and returns a componnet model object
  def jsonToComponentModel(json: String): ComponentModel = {
    ComponentModel(getConfig(json))
  }

  // Parses the given json and returns a subsystem model object
  def jsonToSubsystemModel(json: String): SubsystemModel = {
    SubsystemModel(getConfig(json))
  }
}

/**
 * Support for querying the ICD database
 * (Note: This class works on the current, unpublished versions. See IcdVersionManager for use with versions.)
 *
 * @param db the MongoDB handle
 */
case class IcdDbQuery(db: MongoDB) {

  import IcdDbQuery._

  private[db] def collectionExists(name: String): Boolean = db.collectionExists(name)
  private[db] def getCollectionNames: Set[String] = db.getCollectionNames().toSet

  private[db] def getEntries: List[IcdEntry] = {
    val paths = getCollectionNames.filter(isStdSet).map(IcdPath).toList
    val compMap = paths.map(p ⇒ (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key ⇒ getEntry(db, key, compMap(key))).toList
    entries.sortBy(entry ⇒ (IcdPath(entry.name).parts.length, entry.name))
  }

  /**
   * Returns a list of models, one for each component in the db
   */
  def getComponents: List[ComponentModel] = {
    for (entry ← getEntries if entry.component.isDefined)
      yield jsonToComponentModel(entry.component.get.head.toString)
  }

  // Returns an IcdEntry object for the given component name, if found
  private[db] def entryForComponentName(subsystem: String, component: String): IcdEntry = {
    IcdEntry(name = s"$subsystem.$component",
      subsystem = getSubsystemCollection(subsystem),
      component = getComponentCollection(subsystem, component),
      publish = getPublishCollection(subsystem, component),
      subscribe = getSubscribeCollection(subsystem, component),
      command = getCommandCollection(subsystem, component))
  }

  // Returns an IcdEntry object for the given subsystem name, if found
  private[db] def entryForSubsystemName(subsystem: String): IcdEntry = {
    IcdEntry(subsystem, getSubsystemCollection(subsystem), None, None, None, None)
  }

  /**
   * Returns a list of component model objects, one for each component ICD matching the given condition in the database
   * @param query restricts the components returned (a MongoDBObject, for example)
   */
  def queryComponents(query: DBObject): List[ComponentModel] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = entry.component.get
      val data = coll.findOne(query)
      if (data.isDefined) Some(jsonToComponentModel(data.get.toString)) else None
    }
    list.flatten
  }

  /**
   * Returns a list of component model objects, one for each component ICD of the given type in the database
   * @param componentType restricts the type of components returned (one of: Assembly, HCD, Sequencer, etc.)
   */
  def getComponents(componentType: String): List[ComponentModel] =
    queryComponents("componentType" -> componentType)

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
      .filter(name ⇒ name.startsWith(s"$subsystem.") && !name.endsWith(s".${IcdVersionManager.versionColl}"))
      .map(IcdPath)
      .filter(p ⇒ p.parts.length == 3)
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
   * If a subsystem-model.conf was included, it is used, otherwise the
   * subsystem names defined by the components are used.
   */
  def getSubsystemNames: List[String] = {
    val result = for (entry ← getEntries) yield {
      if (entry.subsystem.isDefined) {
        Some(jsonToSubsystemModel(entry.subsystem.get.head.toString).subsystem)
      } else if (entry.component.isDefined) {
        Some(jsonToComponentModel(entry.component.get.head.toString).subsystem)
      } else None
    }
    result.flatten.distinct
  }

  // --- Get collections for (unpublished) ICD parts ---

  private def getSubsystemCollection(subsystem: String): Option[MongoCollection] = {
    val collName = getSubsystemCollectionName(subsystem)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getComponentCollection(subsystem: String, component: String): Option[MongoCollection] = {
    val collName = getComponentCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getPublishCollection(subsystem: String, component: String): Option[MongoCollection] = {
    val collName = getPublishCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getSubscribeCollection(subsystem: String, component: String): Option[MongoCollection] = {
    val collName = getSubscribeCollectionName(subsystem, component)
    if (collectionExists(collName))
      Some(db(collName))
    else None
  }

  private def getCommandCollection(subsystem: String, component: String): Option[MongoCollection] = {
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
    if (collectionExists(collName))
      Some(SubsystemModel(getConfig(db(collName).head.toString)))
    else None
  }

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(subsystem: String, componentName: String): Option[ComponentModel] = {
    val collName = getComponentCollectionName(subsystem, componentName)
    if (collectionExists(collName))
      Some(ComponentModel(getConfig(db(collName).head.toString)))
    else None
  }

  /**
   * Returns an object describing the items published by the named component
   */
  def getPublishModel(component: ComponentModel): Option[PublishModel] = {
    val collName = getPublishCollectionName(component.subsystem, component.component)
    if (collectionExists(collName))
      Some(PublishModel(getConfig(db(collName).head.toString)))
    else None
  }

  /**
   * Returns an object describing the items subscribed to by the given component
   * @param component the model for the component
   */
  def getSubscribeModel(component: ComponentModel): Option[SubscribeModel] = {
    val collName = getSubscribeCollectionName(component.subsystem, component.component)
    if (collectionExists(collName))
      Some(SubscribeModel(getConfig(db(collName).head.toString)))
    else None
  }

  /**
   * Returns an object describing the "commands" defined for the named component in the named subsystem
   */
  def getCommandModel(subsystem: String, componentName: String): Option[CommandModel] = {
    val collName = getCommandCollectionName(subsystem, componentName)
    if (collectionExists(collName))
      Some(CommandModel(getConfig(db(collName).head.toString)))
    else None
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
   * @param subsystem the target component's subsystem
   * @param component the target component
   * @param commandName the name of the command being sent
   * @return list containing one item for each component that sends the command
   */
  def getCommandSenders(subsystem: String, component: String, commandName: String): List[ComponentModel] = {
    for {
      componentModel ← getComponents
      commandModel ← getCommandModel(componentModel.subsystem, componentModel.component)
      sendCommandModel ← commandModel.send.find(s ⇒
        s.subsystem == subsystem && s.component == component && s.name == commandName)
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
      override val subsystemModel = entry.subsystem.map(s ⇒ SubsystemModel(getConfig(s.head.toString)))
      override val publishModel = entry.publish.map(s ⇒ PublishModel(getConfig(s.head.toString)))
      override val subscribeModel = entry.subscribe.map(s ⇒ SubscribeModel(getConfig(s.head.toString)))
      override val commandModel = entry.command.map(s ⇒ CommandModel(getConfig(s.head.toString)))
      override val componentModel = entry.component.map(s ⇒ ComponentModel(getConfig(s.head.toString)))
    }

    val e = if (component.isDefined)
      entryForComponentName(subsystem, component.get)
    else entryForSubsystemName(subsystem)

    // Get the prefix for the related db sub-collections
    val prefix = e.name + "."
    val list = for (entry ← getEntries if entry.name.startsWith(prefix)) yield new Models(entry)
    Models(e) :: list
  }

  /**
   * Deletes the given component hierarchy. Use with caution!
   *
   * @param subsystem the component's subsystem
   * @param component the component to delete
   */
  def dropComponent(subsystem: String, component: String): Unit = {
    for (coll ← entryForComponentName(subsystem, component).getCollections) {
      coll.drop()
    }
  }

  /**
   * Returns a list of items published by the given component
   * @param component the component's model
   */
  def getPublished(component: ComponentModel): List[Published] = {
    getPublishModel(component) match {
      case Some(publishModel) ⇒
        List(publishModel.telemetryList.map(i ⇒ Published(Telemetry, i.name, i.description)),
          publishModel.eventList.map(i ⇒ Published(Events, i.name, i.description)),
          publishModel.eventStreamList.map(i ⇒ Published(EventStreams, i.name, i.description)),
          publishModel.alarmList.map(i ⇒ Published(Alarms, i.name, i.description)),
          publishModel.healthList.map(i ⇒ Published(Health, i.name, i.description))).flatten
      case None ⇒ Nil
    }
  }

  /**
   * Returns a list describing what each component publishes
   */
  def getPublishInfo(subsystem: String): List[PublishInfo] = {
    def getPublishInfo(c: ComponentModel): PublishInfo =
      PublishInfo(c.component, c.prefix, getPublished(c))

    getComponents.map(c ⇒ getPublishInfo(c))
  }

  /**
   * Returns a list describing which components publish the given value.
   * @param path full path name of value (prefix + name)
   * @param publishType telemetry, alarm, etc...
   */
  def publishes(path: String, subsystem: String, publishType: PublishType): List[PublishedItem] = {
    for {
      i ← getPublishInfo(subsystem)
      p ← i.publishes.filter(p ⇒ s"${i.prefix}.${p.name}" == path && publishType == p.publishType)
    } yield PublishedItem(i.componentName, i.prefix, p)
  }

  /**
   * Returns a list of items the given component subscribes to
   * @param component the component model
   */
  private def getSubscribedTo(component: ComponentModel): List[Subscribed] = {
    getSubscribeModel(component) match {
      case Some(subscribeModel) ⇒
        List(subscribeModel.telemetryList.map(i ⇒ Subscribed(subscribeModel.component, subscribeModel.subsystem, Telemetry, i.name)),
          subscribeModel.eventList.map(i ⇒ Subscribed(subscribeModel.component, subscribeModel.subsystem, Events, i.name)),
          subscribeModel.eventStreamList.map(i ⇒ Subscribed(subscribeModel.component, subscribeModel.subsystem, EventStreams, i.name)),
          subscribeModel.alarmList.map(i ⇒ Subscribed(subscribeModel.component, subscribeModel.subsystem, Alarms, i.name)),
          subscribeModel.healthList.map(i ⇒ Subscribed(subscribeModel.component, subscribeModel.subsystem, Health, i.name))).flatten
      case None ⇒ Nil
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
    getComponents.map(c ⇒ getSubscribeInfo(c))
  }

  /**
   * Returns a list describing the components that subscribe to the given value.
   * @param path full path name of value (prefix + name)
   * @param subscribeType telemetry, alarm, etc...
   */
  def subscribes(path: String, subscribeType: PublishType): List[Subscribed] = {
    for {
      i ← getSubscribeInfo
      s ← i.subscribesTo.filter(sub ⇒ sub.name == path && sub.subscribeType == subscribeType)
    } yield s
  }
}
