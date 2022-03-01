package csw.services.icd.db

import java.io.{File, FileInputStream, FileNotFoundException, InputStreamReader}
import java.util.zip.{ZipEntry, ZipFile}
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions, ConfigValueFactory}
import csw.services.icd.{Problem, StdName}
import csw.services.icd.StdName._
import StdConfig.Resources

import java.nio.charset.StandardCharsets
import scala.io.Source

object StdConfig {

  /**
   * Used to manage OpenApi files referenced in model files.
   * If the model files are uploaded from a browser or in a zip file, they need to be handled differently.
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
        val source   = Source.fromFile(new File(dirName, new File(name).getName))
        val contents = source.mkString
        source.close()
        Some(contents)
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
   * Reads resource files referenced in a model files in a zip file (service-model.conf OpenApi files)
   */
  class ZipResources(zipFile: ZipFile) extends Resources {
    override def getResource(name: String): Option[String] = {
      import scala.jdk.CollectionConverters._
      zipFile.entries().asScala.find(_.getName == name).map { e =>
        val inputStream = zipFile.getInputStream(e)
        val s           = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
        inputStream.close()
        s
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
        val inputStream = new FileInputStream(jsonnetInputFile)
        try {
          val config      = Jsonnet.preprocess(inputStream, jsonnetInputFile.getPath)
          inputStream.close()
          val stdConfig   = StdConfig(stdName, addTargetSubsystem(config, stdName), inputFile.getPath, resources)
          Some(Right(stdConfig))
        } catch {
          case ex: Exception => Some(Left(Problem("error", s"$jsonnetInputFile: ${ex.getMessage}")))
        }
      }
      else None
    }
    val x = eList.partitionMap(identity)
    (x._2, x._1)
  }

  /**
   * Returns a list of StdConfig objects using the content of the given inputFile and the given fileName,
   * if fileName is one of the standard ICD file names (or a zip file containing standard files).
   */
  def get(inputFile: File, fileName: String, resources: Resources): List[StdConfig] = {
    val name = new File(fileName).getName
    if (name.endsWith(".zip"))
      get(new ZipFile(inputFile))
    else {
      val config = {
        if (fileName.endsWith(".jsonnet")) {
          val inputStream = new FileInputStream(inputFile)
          val result      = Jsonnet.preprocess(inputStream, fileName)
          inputStream.close()
          result
        }
        else {
          ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
        }
      }
      val newFileName = fileName.replace(".jsonnet", ".conf")
      StdConfig.get(config, newFileName, resources).toList
    }
  }

  /**
   * Returns a list for StdConfig objects, one for each ICD file in the given zip file
   */
  def get(zipFile: ZipFile): List[StdConfig] = {
    import scala.jdk.CollectionConverters._
    def isValid(f: ZipEntry) = stdSet.contains(new File(f.getName).getName)

    val list = for (e <- zipFile.entries().asScala.filter(isValid)) yield {
      val inputStream = zipFile.getInputStream(e)
      val config = if (e.getName.endsWith(".jsonnet")) {
        Jsonnet.preprocess(inputStream, e.getName)
      }
      else {
        val reader = new InputStreamReader(inputStream)
        val result = ConfigFactory.parseReader(reader).resolve(ConfigResolveOptions.noSystem())
        reader.close()
        result
      }
      val fileName = e.getName.replace(".jsonnet", ".conf")
      StdConfig.get(config, fileName, new ZipResources(zipFile)).get
    }
    list.toList
  }

  /**
   * Returns a StdConfig object for the given config and file name,
   * if the fileName is one of the standard ICD file names.
   */
  def get(config: Config, fileName: String, resources: Resources): Option[StdConfig] = {
    val name = new File(fileName).getName
    stdNames.flatMap { stdName =>
      if (name == stdName.name)
        Some(StdConfig(stdName, addTargetSubsystem(config, stdName), fileName, resources))
      else None
    }.headOption
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
