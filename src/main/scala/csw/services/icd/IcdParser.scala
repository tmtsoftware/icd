package csw.services.icd

import java.io.File

import com.typesafe.config.{ ConfigResolveOptions, ConfigFactory, Config }
import csw.services.icd.model._

/**
 * Parses the standard ICD files in the given directory
 */
case class IcdParser(dir: File) {

  import StdName._

  val icdModel = getConfig(icdFileNames.name).map(IcdModel(_))
  val componentModel = getConfig(componentFileNames.name).map(ComponentModel(_))
  val publishModel = getConfig(publishFileNames.name).map(PublishModel(_))
  val subscribeModel = getConfig(subscribeFileNames.name).map(SubscribeModel(_))
  val commandModel = getConfig(commandFileNames.name).map(CommandModel(_))

  // List of models for the files that were found
  val models = List(icdModel, componentModel, publishModel, subscribeModel, commandModel).flatten

  // Gets the Config object corresponding to the given standard ICD file in the given dir
  private def getConfig(name: String): Option[Config] = {
    val inputFile = new File(dir, name)
    if (inputFile.exists()) {
      Some(ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem()))
    } else None
  }

  /**
   * Saves the ICD as a document to the given file in the format given by the file's suffix,
   * which should be one of (md, html, pdf).
   * @param file the file to save the ICD document in
   */
  def saveToFile(file: File): Unit = {
    for (model ← models) {
      model.save() // XXX TODO
    }
  }
}
