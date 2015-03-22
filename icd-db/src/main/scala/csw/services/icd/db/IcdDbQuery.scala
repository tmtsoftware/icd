package csw.services.icd.db

import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.{ ConfigFactory, Config }
import csw.services.icd.StdName._
import csw.services.icd.model._
import scala.language.implicitConversions

object IcdDbQuery {
  // Set of standard ICD model parts: icd, component, publish, subscribe, command
  val stdSet = stdNames.map(_.modelBaseName).toSet

  //for working with dot separated paths
  case class IcdPath(path: String) {
    lazy val parts = path.split("\\.").toList
    lazy val component = parts.dropRight(1).mkString(".")
  }

  // Contains db collection names related to an ICD
  case class IcdEntry(name: String, icd: Option[String], component: Option[String],
                      publish: Option[String], subscribe: Option[String], command: Option[String])

  implicit def toDbObject(query: (String, String)): DBObject = MongoDBObject(query)
}

/**
 * Support for querying the ICD database
 */
case class IcdDbQuery(db: MongoDB) {

  import IcdDbQuery._

  // Returns a list of IcdEntry for the ICDs (based on the collection names)
  // (XXX Should the return value be cached?)
  private def getEntries: List[IcdEntry] = {
    val paths = db.collectionNames().filter(_ != "system.indexes").map(IcdPath).toList
    val map = paths.map(p ⇒ (p.component, paths.filter(_.component == p.component).map(_.path))).toMap
    val entries = map.keys.map(key ⇒ getEntry(key, map(key))).toList
    entries.sortBy(entry ⇒ (IcdPath(entry.name).parts.length, entry.name))
  }

  // Returns an IcdEntry for the given collection path
  private def getEntry(name: String, paths: List[String]): IcdEntry = {
    IcdEntry(name = name,
      icd = paths.find(_.endsWith(".icd")),
      component = paths.find(_.endsWith(".component")),
      publish = paths.find(_.endsWith(".publish")),
      subscribe = paths.find(_.endsWith(".subscribe")),
      command = paths.find(_.endsWith(".command")))

  }

  // Gets a Config object from a JSON string
  private def getConfig(json: String): Config = {
    ConfigFactory.parseString(json)
  }

  // --- Components ---

  // Parses the given json and returns a componnet model object
  private def jsonToComponentModel(json: String): ComponentModel = {
    ComponentModel(getConfig(json))
  }

  /**
   * Returns a list of all component model objects, one for each component ICD in the database
   */
  def getComponents: List[ComponentModel] = {
    for (entry ← getEntries if entry.component.isDefined)
      yield jsonToComponentModel(db(entry.component.get).head.toString)
  }

  // Returns an IcdEntry object for the given component name, if found
  private def entryForComponentName(name: String): Option[IcdEntry] = {
    val list = for (entry ← getEntries if entry.component.isDefined) yield {
      val coll = db(entry.component.get)
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
   * Returns a list of all the component names in the DB
   */
  def getComponentNames: List[String] = getComponents.map(_.name)

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getAssemblyNames: List[String] = getComponents("Assembly").map(_.name)

  /**
   * Returns a list of all the assembly ICDs in the database
   */
  def getHcdNames: List[String] = getComponents("HCD").map(_.name)

  // --- Get model objects, given a component name ---

  /**
   * Returns the model object for the component with the given name
   */
  def getComponentModel(name: String): Option[ComponentModel] = {
    queryComponents("name" -> name).headOption
  }

  /**
   * Returns an object describing the "commands" defined for the named component
   */
  def getCommandModel(name: String): Option[CommandModel] = {
    for (entry ← entryForComponentName(name) if entry.command.isDefined)
      yield CommandModel(getConfig(db(entry.command.get).head.toString))
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
   * Returns an object describing the ICD for the named component
   */
  def getIcdModel(name: String): Option[IcdModel] = {
    for (entry ← entryForComponentName(name) if entry.icd.isDefined)
      yield IcdModel(getConfig(db(entry.icd.get).head.toString))
  }

  // ---

  /**
   * Returns a list of ICD models for the given component name,
   * based on the data in the database.
   * The list includes the ICD models for the component's ICD followed
   * by any ICD models for components that were defined in subdirectories
   * in the original files that were ingested into the database
   * (In this case the definitions are stored in sub-collections in the DB).
   */
  def getModels(componentName: String): List[IcdModels] = {
    // Holds all the model classes associated with a single ICD entry.
    case class Models(entry: IcdEntry) extends IcdModels {
      override val icdModel: Option[IcdModel] = entry.icd.map(s ⇒ IcdModel(getConfig(db(s).head.toString)))
      override val publishModel: Option[PublishModel] = entry.publish.map(s ⇒ PublishModel(getConfig(db(s).head.toString)))
      override val subscribeModel: Option[SubscribeModel] = entry.subscribe.map(s ⇒ SubscribeModel(getConfig(db(s).head.toString)))
      override val commandModel: Option[CommandModel] = entry.command.map(s ⇒ CommandModel(getConfig(db(s).head.toString)))
      override val componentModel: Option[ComponentModel] = entry.component.map(s ⇒ ComponentModel(getConfig(db(s).head.toString)))
    }

    val compEntry = entryForComponentName(componentName)
    if (compEntry.isDefined) {
      // Get the prefix for the related db sub-collections
      val prefix = compEntry.get.name + "."
      val list = for (entry ← getEntries if entry.name.startsWith(prefix)) yield new Models(entry)
      Models(compEntry.get) :: list
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
}
