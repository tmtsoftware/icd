package csw.services.icd.db

import com.mongodb.casbah.MongoDB
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
  case class IcdPath(path: String) {
    lazy val parts = path.split("\\.").toList

    // The common path for an assembly, HCD, sequencer, etc.
    lazy val component = parts.dropRight(1).mkString(".")

    // The top level subsystem collection name
    lazy val subsystem = parts.head
  }

  // Contains db collection names related to an ICD
  case class IcdEntry(name: String, subsystem: Option[String], component: Option[String],
                      publish: Option[String], subscribe: Option[String], command: Option[String])

  // Returns an IcdEntry for the given collection path
  def getEntry(name: String, paths: List[String]): IcdEntry = {
    IcdEntry(name = name,
      subsystem = paths.find(_.endsWith(".subsystem")),
      component = paths.find(_.endsWith(".component")),
      publish = paths.find(_.endsWith(".publish")),
      subscribe = paths.find(_.endsWith(".subscribe")),
      command = paths.find(_.endsWith(".command")))
  }

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
   * @param componentName component (HCD, assembly, ...) name
   * @param subscribesTo list of types and names (with prefix) of items the component subscribes to
   */
  case class SubscribeInfo(componentName: String, subscribesTo: List[Subscribed])

  /**
   * Describes a subscription
   *
   * @param componentName the name of the component that subscribes to the item
   * @param subscribeType one of Telemetry, Events, Alarms, etc.
   * @param name the name of the item being subscribed to
   * @param subsystem the subsystem to which the named item belongs
   */
  case class Subscribed(componentName: String, subscribeType: PublishType, name: String, subsystem: String)

  implicit def toDbObject(query: (String, Any)): DBObject = MongoDBObject(query)
}

/**
 * Support for querying the ICD database
 */
case class IcdDbQuery(db: MongoDB) {

  import IcdDbQuery._

  // Returns a list of IcdEntry for the ICDs (based on the collection names)
  // (XXX Should the return value be cached?)
  private[db] def getEntries: List[IcdEntry] = {
    val paths = db.collectionNames().filter(isStdSet).map(IcdPath).toList
    val compMap = paths.map(p ⇒ (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = compMap.keys.map(key ⇒ getEntry(key, compMap(key))).toList
    entries.sortBy(entry ⇒ (IcdPath(entry.name).parts.length, entry.name))
  }

  // --- Components ---

  // Parses the given json and returns a componnet model object
  private def jsonToComponentModel(json: String): ComponentModel = {
    ComponentModel(getConfig(json))
  }

  // Parses the given json and returns a subsystem model object
  private def jsonToSubsystemModel(json: String): SubsystemModel = {
    SubsystemModel(getConfig(json))
  }

  // Returns an IcdEntry object for the given component name, if found
  private def entryForComponentName(name: String): Option[IcdEntry] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = db(entry.component.get)
      val data = coll.findOne(BaseModel.componentKey -> name)
      if (data.isDefined) Some(entry) else None
    }
    list.flatten.headOption
  }

  // Returns an IcdEntry object for the given subsystem name, if found
  // XXX FIXME TODO: If the subsystem-model.conf file is not required, need to add some code
  private def entryForSubsystemName(name: String): Option[IcdEntry] = {
    val list = for (entry ← getEntries if entry.subsystem.isDefined) yield {
      val coll = db(entry.subsystem.get)
      val data = coll.findOne("name" -> name)
      if (data.isDefined) Some(entry) else None
    }
    list.flatten.headOption
  }

  /**
   * Returns a list of component model objects, one for each component ICD matching the given condition in the database
   * @param query restricts the components returned (a MongoDBObject, for example)
   */
  def queryComponents(query: DBObject): List[ComponentModel] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = db(entry.component.get)
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
   * Returns a list of all component model objects, one for each component ICD in the database
   */
  def getComponents: List[ComponentModel] = {
    for (entry ← getEntries if entry.component.isDefined)
      yield jsonToComponentModel(db(entry.component.get).head.toString)
  }

  /**
   * Returns a list of all the component names in the DB
   * XXX TODO FIXME (Can simplify after changes made in ingesting)
   */
  def getComponentNames: List[String] = getComponents.map(_.component)

  /**
   * Returns a list of all the component names in the DB belonging to the given subsystem.
   * Note: This method assumes the current version of the subsystem.
   * Use IcdVersionManager.getComponentNames to access any version of the subsystem.
   */
  def getComponentNames(subsystem: String): List[String] = {
    db.collectionNames()
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
        Some(jsonToSubsystemModel(db(entry.subsystem.get).head.toString).name)
      } else if (entry.component.isDefined) {
        Some(jsonToComponentModel(db(entry.component.get).head.toString).subsystem)
      } else None
    }
    result.flatten.distinct
  }

  // --- Get model objects, given a component name ---

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(component: String): Option[ComponentModel] = {
    queryComponents(BaseModel.componentKey -> component).headOption
  }

  /**
   * Returns an object describing the "commands" defined for the named component
   */
  def getCommandModel(component: String): Option[CommandModel] = {
    for (entry ← entryForComponentName(component) if entry.command.isDefined)
      yield CommandModel(getConfig(db(entry.command.get).head.toString))
  }

  /**
   * Returns an object describing the "commands" defined for the named component in the named subsystem
   */
  def getCommandModel(subsystem: String, component: String): Option[CommandModel] = {
    // XXX TODO: Use the subsystem and component name to more efficiently get to the command model
    getCommandModel(component)
  }

  /**
   * Returns an object describing the named command, defined for the named component in the named subsystem
   */
  def getCommand(subsystem: String, component: String, commandName: String): Option[ReceiveCommandModel] = {
    getCommandModel(subsystem, component).flatMap(_.receive.find(_.name == commandName))
  }

  /**
   * Returns a list of the names of components that send the given command to the given component/subsystem
   */
  def getCommandSenders(subsystem: String, component: String, commandName: String): List[ComponentModel] = {
    for {
      componentModel ← getComponents
      commandModel ← getCommandModel(componentModel.subsystem, componentModel.component)
      sendCommandModel ← commandModel.send.find(s ⇒
        s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield componentModel
  }

  /**
   * Returns an object describing the items published by the named component
   */
  def getPublishModel(name: String): Option[PublishModel] = {
    for (entry ← entryForComponentName(name) if entry.publish.isDefined)
      yield PublishModel(getConfig(db(entry.publish.get).head.toString))
  }

  /**
   * Returns an object describing the items subscribed to by the named component
   */
  def getSubscribeModel(name: String): Option[SubscribeModel] = {
    for (entry ← entryForComponentName(name) if entry.subscribe.isDefined)
      yield SubscribeModel(getConfig(db(entry.subscribe.get).head.toString))
  }

  /**
   * Returns an object describing the ICD subsystem for the named component
   */
  def getSubsystemModel(name: String): Option[SubsystemModel] = {
    for (entry ← entryForComponentName(name) if entry.subsystem.isDefined)
      yield SubsystemModel(getConfig(db(entry.subsystem.get).head.toString))
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
   */
  def getModels(name: String): List[IcdModels] = {
    // Holds all the model classes associated with a single ICD entry.
    case class Models(entry: IcdEntry) extends IcdModels {
      override val subsystemModel = entry.subsystem.map(s ⇒ SubsystemModel(getConfig(db(s).head.toString)))
      override val publishModel = entry.publish.map(s ⇒ PublishModel(getConfig(db(s).head.toString)))
      override val subscribeModel = entry.subscribe.map(s ⇒ SubscribeModel(getConfig(db(s).head.toString)))
      override val commandModel = entry.command.map(s ⇒ CommandModel(getConfig(db(s).head.toString)))
      override val componentModel = entry.component.map(s ⇒ ComponentModel(getConfig(db(s).head.toString)))
    }

    val e = entryForSubsystemName(name).orElse(entryForComponentName(name))
    if (e.isDefined) {
      // Get the prefix for the related db sub-collections
      val prefix = e.get.name + "."
      val list = for (entry ← getEntries if entry.name.startsWith(prefix)) yield new Models(entry)
      Models(e.get) :: list
    } else Nil
  }

  /**
   * Deletes the given component hierarchy. Use with caution!
   */
  def dropComponent(name: String): Unit = {
    val compEntry = entryForComponentName(name)
    if (compEntry.isDefined) {
      // Get the prefix for the related db sub-collections
      val topLevelPrefix = compEntry.get.name + "."
      val list = for (entry ← getEntries if entry.name.startsWith(topLevelPrefix)) yield entry.name
      (compEntry.get.name :: list).foreach { prefix ⇒
        stdSet.foreach { s ⇒
          val collName = s"$prefix.$s"
          if (db.collectionExists(collName)) db(collName).drop()
        }
      }
    }
  }

  /**
   * Returns a list of items published by the given component
   * @param name the component name
   */
  def getPublished(name: String): List[Published] = {
    getPublishModel(name) match {
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
  def getPublishInfo: List[PublishInfo] = {
    def getPublishInfo(compName: String, prefix: String): PublishInfo =
      PublishInfo(compName, prefix, getPublished(compName))

    getComponents.map(c ⇒ getPublishInfo(c.component, c.prefix))
  }

  /**
   * Returns a list describing which components publish the given value.
   * @param path full path name of value (prefix + name)
   * @param publishType telemetry, alarm, etc...
   */
  def publishes(path: String, publishType: PublishType): List[PublishedItem] = {
    for {
      i ← getPublishInfo
      p ← i.publishes.filter(p ⇒ s"${i.prefix}.${p.name}" == path && publishType == p.publishType)
    } yield PublishedItem(i.componentName, i.prefix, p)
  }

  /**
   * Returns a list of items the given component subscribes to
   * @param name the component name
   */
  def getSubscribedTo(name: String): List[Subscribed] = {
    getSubscribeModel(name) match {
      case Some(subscribeModel) ⇒
        List(subscribeModel.telemetryList.map(i ⇒ Subscribed(name, Telemetry, i.name, i.subsystem)),
          subscribeModel.eventList.map(i ⇒ Subscribed(name, Events, i.name, i.subsystem)),
          subscribeModel.eventStreamList.map(i ⇒ Subscribed(name, EventStreams, i.name, i.subsystem)),
          subscribeModel.alarmList.map(i ⇒ Subscribed(name, Alarms, i.name, i.subsystem)),
          subscribeModel.healthList.map(i ⇒ Subscribed(name, Health, i.name, i.subsystem))).flatten
      case None ⇒ Nil
    }
  }

  /**
   * Returns an object describing what the given component subscribes to
   */
  def getSubscribeInfo(name: String): SubscribeInfo = SubscribeInfo(name, getSubscribedTo(name))

  /**
   * Returns a list describing what each component subscribes to
   */
  def getSubscribeInfo: List[SubscribeInfo] = {
    getComponents.map(c ⇒ getSubscribeInfo(c.component))
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
