package csw.services.icd.db

import csw.services.icd.model._

/**
 * Holds all the model classes associated with a single ICD part (directory).
 * @param query used to query the database
 * @param componentName the name of the top level component in the ICD
 */
case class IcdDbModels(query: IcdDbQuery, componentName: String) extends IcdModels {
  override val icdModel: Option[IcdModel] = query.getIcdModel(componentName)
  override val publishModel: Option[PublishModel] = query.getPublishModel(componentName)
  override val subscribeModel: Option[SubscribeModel] = query.getSubscribeModel(componentName)
  override val commandModel: Option[CommandModel] = query.getCommandModel(componentName)
  override val componentModel: Option[ComponentModel] = query.getComponentModel(componentName)
}
