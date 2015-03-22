package csw.services.icd.model

/**
 * Holds the set of models associated with the set of standard ICD files
 * (the files found in each directory of an ICD definition. Each file is optional).
 */
trait IcdModels {
  val icdModel: Option[IcdModel]
  val componentModel: Option[ComponentModel]
  val publishModel: Option[PublishModel]
  val subscribeModel: Option[SubscribeModel]
  val commandModel: Option[CommandModel]
}
