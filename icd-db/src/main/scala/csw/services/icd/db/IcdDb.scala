package csw.services.icd.db

import java.io.File

import com.mongodb.casbah.Imports._
import com.typesafe.config.{ Config, ConfigResolveOptions, ConfigFactory }
import csw.services.icd._

object IcdDb {
  /**
   * Command line options: [--injest <dir> --out <outputFile>]
   * (Some options may be abbreviated to a single letter: -i, -o)
   */
  case class Options(injestDir: Option[File] = None, outputFile: Option[File] = None)

  private val parser = new scopt.OptionParser[Options]("icd-db") {
    head("icd-db", System.getProperty("CSW_VERSION"))

    opt[File]('i', "injest") valueName "<dir>" action { (x, c) ⇒
      c.copy(injestDir = Some(x))
    } text "Directory containing ICD files to injest into the database"

    opt[File]('o', "out") valueName "<outputFile>" action { (x, c) ⇒
      c.copy(outputFile = Some(x))
    } text "Saves the ICD to the given file in a format based on the file's suffix (md, html, pdf)"

  }

  // XXX TODO

}

/**
 * ICD Database (Mongodb) support
 */
case class IcdDb(dbName: String = "icds", host: String = "localhost", port: Int = 27017) {

  val mongoClient = MongoClient(host, port)
  val db = mongoClient(dbName)
  val query = IcdDbQuery(db)

  /**
   * Ingests all the files with the standard names (stdNames) in the given directory and recursively
   * in its subdirectories into the database.
   * @param name the name to store the ICD under (the collection name, usually the last component of the directory name)
   * @param dir the top level directory containing one or more of the the standard set of ICD files
   *            and any number of subdirectories containing ICD files
   */
  def ingest(name: String, dir: File = new File(".")): List[Problem] = {
    val problems = IcdValidator.validateRecursive(dir) // XXX TODO: enforce that dir name == component name?
    if (problems.isEmpty) {
      ingestDir(name, dir)
      for (subdir ← subDirs(dir)) {
        // build names for collections from name and subdir names, separated by "."
        val path = dir.toPath.relativize(subdir.toPath).toString.replaceAll("/", ".")
        ingestDir(s"$name.$path", subdir)
      }
    }
    problems
  }

  /**
   * Ingests all files with the standard names (stdNames) in the given directory into the database.
   * @param name the name of the collection in which to store this part of the ICD
   * @param dir the directory containing the standard set of ICD files
   */
  private def ingestDir(name: String, dir: File): Unit = {
    import csw.services.icd.StdName._
    for (stdName ← stdNames) yield {
      val inputFile = new File(dir, stdName.name)
      if (inputFile.exists()) {
        val inputConfig = ConfigFactory.parseFile(inputFile).resolve(ConfigResolveOptions.noSystem())
        ingestConfig(s"$name.${stdName.modelBaseName}", inputConfig)
      }
    }
  }

  /**
   * Ingests the given input config into the database.
   * @param name the name of the collection in which to store this part of the ICD
   * @param config the config to be ingested into the datasbase
   * @return a list of problems, if any were found
   */
  private def ingestConfig(name: String, config: Config): Unit = {
    import collection.JavaConversions._
    val coll = db(name)
    val dbObj = config.root().unwrapped().toMap.asDBObject
    coll.insert(dbObj)
  }

  /**
   * Drops this database.  Removes all data on disk.  Use with caution.
   */
  def dropDatabase(): Unit = {
    db.dropDatabase()
  }

}
