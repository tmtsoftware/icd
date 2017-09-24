package csw.services.icd.db

import java.io.{InputStreamReader, File}
import java.util.zip.{ZipEntry, ZipFile}

import com.typesafe.config.{ConfigResolveOptions, ConfigFactory, Config}
import csw.services.icd.StdName
import csw.services.icd.StdName._

/**
 * Used to determine the subsystem and component name, given a set of model files.
 * The MongoDB collection name is subsystem.name or subsystem.component.name,
 * where name is the value returned by StdName.modelBaseName.
 *
 * @param stdName indicates which of the ICD model files the config represents
 * @param config the model file parsed into a Config
 */
case class StdConfig(stdName: StdName, config: Config)

object StdConfig {

  /**
   * Returns a list for StdConfig objects, one for each ICD file in the given directory
   * XXX TODO: Return config parse errors in StdConfig.get with file names
   */
  def get(dir: File): List[StdConfig] = {
    stdNames.flatMap { stdName =>
      val inputFile = new File(dir, stdName.name)
      if (inputFile.exists())
        Some(StdConfig(stdName, ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())))
      else None
    }
  }

  /**
   * Returns a list of StdConfig objects using the content of the given inputFile and the given fileName,
   * if fileName is one of the standard ICD file names (or a zip file containing standard files).
   */
  def get(inputFile: File, fileName: String): List[StdConfig] = {
    val name = new File(fileName).getName
    if (name.endsWith(".zip"))
      get(new ZipFile(inputFile))
    else {
      stdNames.flatMap { stdName =>
        if (name == stdName.name)
          Some(StdConfig(stdName, ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())))
        else None
      }
    }
  }

  /**
   * Returns a list for StdConfig objects, one for each ICD file in the given zip file
   */
  def get(zipFile: ZipFile): List[StdConfig] = {
    import scala.collection.JavaConverters._
    def isValid(f: ZipEntry) = stdSet.contains(new File(f.getName).getName)
    val list = for (e <- zipFile.entries().asScala.filter(isValid)) yield {
      val reader = new InputStreamReader(zipFile.getInputStream(e))
      val config = ConfigFactory.parseReader(reader)
      StdConfig.get(config, e.getName).get
    }
    list.toList
  }

  /**
   * Returns a StdConfig object for the given config and file name,
   * if the fileName is one of the standard ICD file names.
   */
  def get(config: Config, fileName: String): Option[StdConfig] = {
    val name = new File(fileName).getName
    stdNames.flatMap {
      stdName => if (name == stdName.name) Some(StdConfig(stdName, config)) else None
    }.headOption
  }
}