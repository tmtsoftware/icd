package csw.services.icd.db

import icd.web.shared.IcdModels.{CommandModel, ComponentModel, PublishModel, SubscribeModel}
import reactivemongo.api.DefaultDB

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

/**
 * Adds caching to IcdDbQuery for better performance when creating documents or web pages that
 * require access to all subsystems and components.
 *
 * @param db the DefaultDB handle
 * @param maybeSubsystems limit the database searches to the given subsystems
 */
class CachedIcdDbQuery(db: DefaultDB, maybeSubsystems: Option[List[String]]) extends IcdDbQuery(db, maybeSubsystems) {
  import IcdDbQuery._

  // XXX TODO FIXME: Pass in timeout or use async lib and make everything async
  private val timeout = 60.seconds

  // --- Cached values ---
  private val collectionNames = Await.result(db.collectionNames, timeout).filter(collectionNameFilter).toSet

  // Note: this was 99% of the bottleneck: db.collectionExists calls db.getCollectionNames every time!
  override def collectionExists(name: String): Boolean = collectionNames.contains(name)

  override def getCollectionNames: Set[String] = collectionNames

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
