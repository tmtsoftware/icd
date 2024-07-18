package csw.services.icd.db

import java.io.{File, FileNotFoundException}
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions, ConfigValueFactory}
import csw.services.icd.{Problem, StdName}
import csw.services.icd.StdName.*
import StdConfig.Resources

object StdConfig {

  /**
   * Used to manage OpenApi files referenced in model files.
   */
  trait Resources {
    def getResource(name: String): Option[String]
  }

  /**
   * Used to read referenced resource files in model files (service-model.conf OpenApi files)
   */
  class FileResources(dirName: String) extends Resources {
    override def getResource(name: String): Option[String] = {
      try {
        Some(getFileContents(new File(dirName, new File(name).getName)))
      }
      catch {
        case _: FileNotFoundException => None
        case e: Exception =>
          e.printStackTrace()
          None
      }
    }
  }

  /**
   * Returns a pair of lists of StdConfig and Problem objects, for each icd model file in the given directory
   */
  def get(dir: File): (List[StdConfig], List[Problem]) = {
    val eList = stdNames.flatMap { stdName =>
      val inputFile        = new File(dir, stdName.name)
      val jsonnetInputFile = new File(dir, stdName.name.replace(".conf", ".jsonnet"))
      val resources        = new FileResources(dir.getPath)
      if (inputFile.exists()) {
        try {
          val config    = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
          val stdConfig = StdConfig(stdName, addTargetSubsystem(config, stdName), inputFile.getPath, resources)
          Some(Right(stdConfig))
        }
        catch {
          case ex: Exception => Some(Left(Problem("error", s"$inputFile: ${ex.getMessage}")))
        }
      }
      else if (jsonnetInputFile.exists()) {
        try {
          val config = Jsonnet.preprocess(jsonnetInputFile)
          val stdConfig = StdConfig(stdName, addTargetSubsystem(config, stdName), inputFile.getPath, resources)
          Some(Right(stdConfig))
        }
        catch {
          case ex: Exception => Some(Left(Problem("error", s"$jsonnetInputFile: ${ex.getMessage}")))
        }
      }
      else None
    }
    val x = eList.partitionMap(identity)
    (x._2, x._1)
  }

  // Automatically add the target subsystem to $subsystem-icd-model.conf file configs.
  def addTargetSubsystem(config: Config, stdName: StdName): Config = {
    if (stdName.isIcdModel && !config.hasPath(stdName.icdTargetSubsystem.get)) {
      config.withValue("targetSubsystem", ConfigValueFactory.fromAnyRef(stdName.icdTargetSubsystem.get))
    }
    else config
  }
}

/**
 * Used to determine the subsystem and component name, given a set of model files.
 * The DB collection name is subsystem.name or subsystem.component.name,
 * where name is the value returned by StdName.modelBaseName.
 *
 * @param stdName indicates which of the ICD model files the config represents
 * @param config  the model file parsed into a Config
 * @param fileName  the (relative) path name of the source file (for error reporting)
 * @param resources  used to read OpenApi files referenced in service-model.conf, if uploaded or from a zip file
 */
case class StdConfig(stdName: StdName, config: Config, fileName: String, resources: Resources)
