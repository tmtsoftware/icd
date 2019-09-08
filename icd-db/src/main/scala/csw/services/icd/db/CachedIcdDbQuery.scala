package csw.services.icd.db

import com.mongodb.casbah.MongoDB
import icd.web.shared.IcdModels.{SubscribeModel, PublishModel, CommandModel, ComponentModel}

/**
 * Adds caching to IcdDbQuery for better performance when creating documents or web pages that
 * require access to all subsystems and components.
 *
 * @param db the MongoDB handle
 */
class CachedIcdDbQuery(db: MongoDB) extends IcdDbQuery(db) {

  import IcdDbQuery._

  // --- Cached values ---
  private val collectionNames = db.getCollectionNames().toSet

  override def getCollectionNames: Set[String] = collectionNames

  // Note: this was 99% of the bottleneck: db.collectionExists calls db.getCollectionNames every time!
  override def collectionExists(name: String): Boolean = collectionNames.contains(name)

  private val entries        = super.getEntries
  private val components     = super.getComponents
  private val subsystemNames = super.getSubsystemNames

  private val subscribeModelMap = getSubscribeModelMap(components)
  private val publishModelMap   = getPublishModelMap(components)
  private val commandModelMap   = getCommandModelMap(components)

  private val subscribeInfo  = components.map(c => super.getSubscribeInfo(c))
  private val publishInfoMap = getPublishInfoMap

  /**
   * Returns a map from subsystem name to list of PublishInfo for the subsystem
   */
  private def getPublishInfoMap: Map[String, List[PublishInfo]] = {
    val list = for (s <- subsystemNames) yield s -> super.getPublishInfo(s)
    list.toMap
  }

  /**
   * Returns a map of component to subscribe model, for each component in the list that defines one
   *
   * @param components a list of component models
   */
  private def getSubscribeModelMap(components: List[ComponentModel]): Map[Component, SubscribeModel] = {
    val list = for {
      componentModel <- components
      subscribeModel <- super.getSubscribeModel(componentModel)
    } yield Component(componentModel.subsystem, componentModel.component) -> subscribeModel
    list.toMap
  }

  /**
   * Returns a map of component to publish model, for each component in the list that defines one
   *
   * @param components a list of component models
   */
  private def getPublishModelMap(components: List[ComponentModel]): Map[Component, PublishModel] = {
    val list = for {
      componentModel <- components
      publishModel   <- super.getPublishModel(componentModel)
    } yield Component(componentModel.subsystem, componentModel.component) -> publishModel
    list.toMap
  }

  /**
   * Returns a map of component to command model, for each component in the list that defines one
   *
   * @param components a list of component models
   */
  private def getCommandModelMap(components: List[ComponentModel]): Map[Component, CommandModel] = {
    val list = for {
      componentModel <- components
      commandModel   <- super.getCommandModel(componentModel)
    } yield Component(componentModel.subsystem, componentModel.component) -> commandModel
    list.toMap
  }

  // --- Override these to use the cached values ---
  override def getEntries: List[IcdEntry] = entries

  override def getComponents: List[ComponentModel] = components

  override def getPublishInfo(subsystem: String): List[PublishInfo] =
    publishInfoMap.getOrElse(subsystem, Nil)

  override def getPublishModel(component: ComponentModel): Option[PublishModel] =
    publishModelMap.get(Component(component.subsystem, component.component))

  override def getSubscribeModel(component: ComponentModel): Option[SubscribeModel] =
    subscribeModelMap.get(Component(component.subsystem, component.component))

  override def getCommandModel(component: ComponentModel): Option[CommandModel] =
    commandModelMap.get(Component(component.subsystem, component.component))

  override def getSubscribeInfo: List[SubscribeInfo] = subscribeInfo
}
