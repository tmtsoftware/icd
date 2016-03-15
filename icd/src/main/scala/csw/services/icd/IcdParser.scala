package csw.services.icd

import java.io.File

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory, Config}
import csw.services.icd.model._
import icd.web.shared.IcdModels

/**
 * Parses the standard ICD files in the given directory
 */
case class IcdParser(dir: File) extends IcdModels {

  import StdName._

  val subsystemModel = getConfig(subsystemFileNames.name).map(SubsystemModelParser(_))
  val componentModel = getConfig(componentFileNames.name).map(ComponentModelParser(_))
  val publishModel = getConfig(publishFileNames.name).map(PublishModelParser(_))
  val subscribeModel = getConfig(subscribeFileNames.name).map(SubscribeModelParser(_))
  val commandModel = getConfig(commandFileNames.name).map(CommandModelParser(_))

  // Gets the Config object corresponding to the given standard ICD file in the given dir
  private def getConfig(name: String): Option[Config] = {
    val inputFile = new File(dir, name)
    if (inputFile.exists()) {
      Some(ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem()))
    } else None
  }
}
