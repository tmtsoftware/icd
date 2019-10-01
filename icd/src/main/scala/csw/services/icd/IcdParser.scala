package csw.services.icd

import java.io.File

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory, Config}
import csw.services.icd.parser._
import icd.web.shared.IcdModels

/**
 * Parses the standard ICD files in the given directory
 */
case class IcdParser(dir: File) extends IcdModels {

  // XXX TODO FIXME: Remove Config based parsers and icd command
  import StdName._

  val subsystemModel: Option[IcdModels.SubsystemModel] = getConfig(subsystemFileNames.name).map(SubsystemModelParser(_))
  val componentModel: Option[IcdModels.ComponentModel] = getConfig(componentFileNames.name).map(ComponentModelParser(_))
  val publishModel: Option[IcdModels.PublishModel]     = getConfig(publishFileNames.name).map(PublishModelParser(_))
  val subscribeModel: Option[IcdModels.SubscribeModel] = getConfig(subscribeFileNames.name).map(SubscribeModelParser(_))
  val commandModel: Option[IcdModels.CommandModel]     = getConfig(commandFileNames.name).map(CommandModelParser(_))

  // Gets the Config object corresponding to the given standard ICD file in the given dir
  private def getConfig(name: String): Option[Config] = {
    val inputFile = new File(dir, name)
    if (inputFile.exists()) {
      Some(ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem()))
    } else None
  }
}
