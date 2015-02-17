package csw.services.icd

import java.io.File

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory, Config}

/**
 * Parses the standard ICD files in the given directory
 */
case class IcdParser(dir: File) {

  import StdName._

//  case class IcdModel(config: Option[Config]) extends IcdModelBase {
//
//  }
//
//  case class ComponentModel(config: Option[Config]) extends IcdModelBase
//  case class PublishModel(config: Option[Config]) extends IcdModelBase
//  case class SubscribeModel(config: Option[Config]) extends IcdModelBase
//  case class CommandModel(config: Option[Config]) extends IcdModelBase
//
//  val models = List(
//    IcdModel(getConfig(dir, icdFileNames.name)),
//    ComponentModel(getConfig(dir, componentFileNames.name)),
//    PublishModel(getConfig(dir, publishFileNames.name)),
//    SubscribeModel(getConfig(dir, subscribeFileNames.name)),
//    CommandModel(getConfig(dir, commandFileNames.name))
//  )

  // List of models for the configs that were found (ignoring any missing ones)
  val models = List(
    getConfig(dir, icdFileNames.name).map(IcdModel(_)),
    getConfig(dir, componentFileNames.name).map(ComponentModel(_))
  ).flatten


  // Gets the Config object corresponding to the given standard ICD file in the given dir
  private def getConfig(dir: File, name: String): Option[Config] = {
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
    for(model <- models) {
      model.save() // XXX TODO
    }
  }
}
