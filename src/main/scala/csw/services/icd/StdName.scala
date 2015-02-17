package csw.services.icd

/**
 * Standard file names expected for a complete ICD
 */
object StdName {
  val icdFileNames = StdName("icd-model.conf", "icd-schema.conf")
  val componentFileNames = StdName("component-model.conf", "component-schema.conf")
  val publishFileNames = StdName("publish-model.conf", "publish-schema.conf")
  val subscribeFileNames = StdName("subscribe-model.conf", "subscribe-schema.conf")
  val commandFileNames = StdName("command-model.conf", "command-schema.conf")

  /**
   * List of standard ICD files and schemas
   */
  val stdNames = List(icdFileNames, componentFileNames, publishFileNames, subscribeFileNames, commandFileNames)
}

/**
 * Holds name of input file and matching schema
 */
case class StdName(name: String, schema: String)

