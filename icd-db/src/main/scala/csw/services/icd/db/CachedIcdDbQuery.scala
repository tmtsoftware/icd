package csw.services.icd.db

import icd.web.shared.IcdModels.{AlarmsModel, CommandModel, ComponentModel, PublishModel, SubscribeModel}
import csw.services.icd._
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import icd.web.shared.PdfOptions
import reactivemongo.api.DB

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Adds caching to IcdDbQuery for better performance when creating documents or web pages that
 * require access to all subsystems and components.
 *
 * @param db the DB handle
 * @param maybeSubsystems limit the database searches to the given subsystems
 */
class CachedIcdDbQuery(
    db: DB,
    admin: DB,
    maybeSubsystems: Option[List[String]],
    maybePdfOptions: Option[PdfOptions],
    fitsKeyMap: FitsKeyMap
) extends IcdDbQuery(db, admin, maybeSubsystems) {
  import IcdDbQuery._

  // --- Cached values ---
  private val allCollectionNames = db.collectionNames.await

  private val collectionNames = allCollectionNames.filter(collectionNameFilter).toSet

  // Note: this was 99% of the bottleneck: db.collectionExists calls db.getCollectionNames every time!
  override def collectionExists(name: String): Boolean = collectionNames.contains(name)

  override def getAllCollectionNames: List[String] = allCollectionNames

  override def getCollectionNames: Set[String] = collectionNames

  private val entries        = super.getEntries
  private val components     = super.getComponents(maybePdfOptions)
  private val subsystemNames = super.getSubsystemNames

  private val subscribeModelMap = getSubscribeModelMap(components)
  private val publishModelMap   = getPublishModelMap(components)
  private val commandModelMap   = getCommandModelMap(components, maybePdfOptions)
  private val alarmsModelMap    = getAlarmsModelMap(components, maybePdfOptions)

  private val subscribeInfo  = components.map(c => super.getSubscribeInfo(c, maybePdfOptions))
  private val publishInfoMap = getPublishInfoMap

  /**
   * Returns a map from subsystem name to list of PublishInfo for the subsystem
   */
  private def getPublishInfoMap: Map[String, List[PublishInfo]] = {
    val list = for (s <- subsystemNames) yield s -> super.getPublishInfo(s, maybePdfOptions, fitsKeyMap)
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
      subscribeModel <- super.getSubscribeModel(componentModel, maybePdfOptions)
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
      publishModel   <- super.getPublishModel(componentModel, maybePdfOptions, fitsKeyMap)
    } yield Component(componentModel.subsystem, componentModel.component) -> publishModel
    list.toMap
  }

  /**
   * Returns a map of component to command model, for each component in the list that defines one
   *
   * @param components a list of component models
   */
  private def getCommandModelMap(
      components: List[ComponentModel],
      maybePdfOptions: Option[PdfOptions]
  ): Map[Component, CommandModel] = {
    val list = for {
      componentModel <- components
      commandModel   <- super.getCommandModel(componentModel, maybePdfOptions)
    } yield Component(componentModel.subsystem, componentModel.component) -> commandModel
    list.toMap
  }

  /**
   * Returns a map of component to alarms model, for each component in the list that defines one
   *
   * @param components a list of component models
   */
  private def getAlarmsModelMap(
      components: List[ComponentModel],
      maybePdfOptions: Option[PdfOptions]
  ): Map[Component, AlarmsModel] = {
    val list = for {
      componentModel <- components
      alarmsModel    <- super.getAlarmsModel(componentModel, maybePdfOptions)
    } yield Component(componentModel.subsystem, componentModel.component) -> alarmsModel
    list.toMap
  }

  // --- Override these to use the cached values ---
  override def getEntries: List[ApiCollections] = entries

  override def getComponents(maybePdfOptions: Option[PdfOptions]): List[ComponentModel] = components

  override def getPublishInfo(subsystem: String, maybePdfOptions: Option[PdfOptions], fitsKeyMap: FitsKeyMap): List[PublishInfo] =
    publishInfoMap.getOrElse(subsystem, Nil)

  override def getPublishModel(
      component: ComponentModel,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): Option[PublishModel] =
    publishModelMap.get(Component(component.subsystem, component.component))

  override def getAlarmsModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[AlarmsModel] =
    alarmsModelMap.get(Component(component.subsystem, component.component))

  override def getSubscribeModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[SubscribeModel] =
    subscribeModelMap.get(Component(component.subsystem, component.component))

  override def getCommandModel(component: ComponentModel, maybePdfOptions: Option[PdfOptions]): Option[CommandModel] =
    commandModelMap.get(Component(component.subsystem, component.component))

  override def getSubscribeInfo(maybePdfOptions: Option[PdfOptions]): List[SubscribeInfo] = subscribeInfo
}
