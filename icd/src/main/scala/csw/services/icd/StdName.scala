package csw.services.icd

import java.io.File

/**
 * Standard file names expected for a complete ICD API description
 */
object StdName {
  val subsystemFileNames = StdName("subsystem-model.conf", "subsystem-schema.conf")
  val componentFileNames = StdName("component-model.conf", "component-schema.conf")
  val publishFileNames = StdName("publish-model.conf", "publish-schema.conf")
  val subscribeFileNames = StdName("subscribe-model.conf", "subscribe-schema.conf")
  val commandFileNames = StdName("command-model.conf", "command-schema.conf")

  /**
   * List of standard ICD files and schemas
   */
  val stdNames = List(subsystemFileNames, componentFileNames, publishFileNames, subscribeFileNames, commandFileNames)

  /**
   * Set of standard ICD file names
   */
  val stdSet = stdNames.map(_.name).toSet

  /**
   * True if the argument is a directory containing icd files with the standard names
   */
  def isStdDir(d: File): Boolean = {
    d.isDirectory && d.listFiles().map(_.getName).toSet.intersect(StdName.stdSet).nonEmpty
  }
}

/**
 * Holds name of input file and matching schema
 */
case class StdName(name: String, schema: String) {
  /**
   * Base name: For example icd, component, publish, etc.
   */
  val modelBaseName = name.substring(0, name.length - 11)

  def isSubsystemModel = modelBaseName == "subsystem"
  def isComponentModel = modelBaseName == "component"
}

