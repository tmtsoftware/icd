package controllers

import javax.inject.Inject
import com.typesafe.config.ConfigException
import controllers.FileUploadController.UploadResources
import csw.services.icd.db.StdConfig
import csw.services.icd.db.StdConfig.Resources
import csw.services.icd.{IcdValidator, Problem, StdName}
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.Files

import java.io.File
import scala.io.Source

object FileUploadController {
  /**
   * Reads resource files referenced in uploaded model files (service-model.conf OpenApi files)
   * @param map map of file name to actual file
   */
  class UploadResources(map: Map[String, File]) extends Resources {
    override def getResource(name: String): Option[String] = {
      map
        .get(name)
        .map { file =>
          val source   = Source.fromFile(file)
          val contents = source.mkString
          source.close()
          contents
        }
    }
  }
}

class FileUploadController @Inject() (components: ControllerComponents) extends AbstractController(components) {

  private val log     = play.Logger.of("application")
  private lazy val db = ApplicationData.tryDb.get

  // Server side of the upload ICD feature.
  // Supported file types: A directory containing icd config files (chrome)
  // or a .zip file containing directories with icd config files.
  def uploadFiles(): Action[MultipartFormData[Files.TemporaryFile]] =
    Action(parse.multipartFormData) { implicit request =>
      val files = request.body.files.toList
      val resourceMap = files.flatMap { filePart =>
        if (filePart.filename.endsWith(".json") || filePart.filename.endsWith(".yaml"))
          Some(filePart.filename -> filePart.ref.path.toFile)
        else None
      }
      val resources = new UploadResources(resourceMap.toMap)
      try {
        val list = files.flatMap { filePart =>
          StdConfig.get(filePart.ref.path.toFile, filePart.filename, resources)
        }
        ingestConfigs(list)
      }
      catch {
        case e: ConfigException =>
          e.printStackTrace()
          val msg = e.getMessage
          log.error(msg, e)
          NotAcceptable(Json.toJson(List(Problem("error", msg))))
        case e: RuntimeException =>
          e.printStackTrace()
          val msg = e.getMessage
          log.error(msg, e)
          NotAcceptable(Json.toJson(List(Problem("error", msg))))
        case t: Throwable =>
          val msg = "Internal error"
          log.error(msg, t)
          InternalServerError(Json.toJson(List(Problem("error", msg))))
      }
    }

  /**
   * Uploads/ingests the given API config files
   *
   * @param list    list of objects based on uploaded ICD files
   * @return the HTTP result (OK, or NotAcceptable[list of Problems in JSON format])
   */
  private def ingestConfigs(list: List[StdConfig]): Result = {
    import net.ceedubs.ficus.Ficus._

    def getSchemaVersion(sc: StdConfig): String = {
      sc.config.as[Option[String]](IcdValidator.schemaVersionKey).getOrElse(IcdValidator.currentSchemaVersion)
    }

    def getComponentName(sc: StdConfig): Option[String] = {
      sc.config.as[Option[String]]("component")
    }

    // The files might not be in any particular order, so make sure we use the correct schema version for each component
    def makeSchemaVersionMap(): Map[String, String] = {
      list.flatMap { sc =>
        if (sc.stdName.isComponentModel) {
          getComponentName(sc).map(_ -> getSchemaVersion(sc))
        }
        else None
      }.toMap
    }

    def validateAll(): List[Problem] = {
      val schemaVersionMap = makeSchemaVersionMap()
      list.flatMap { sc =>
        val schemaVersion =
          if (sc.stdName.isSubsystemModel)
            getSchemaVersion(sc)
          else {
            getComponentName(sc).flatMap(schemaVersionMap.get).getOrElse(IcdValidator.currentSchemaVersion)
          }
        IcdValidator.validateStdName(sc, schemaVersion)
      }
    }

    // Validate everything first
    val validateProblems = validateAll()
    if (validateProblems.nonEmpty) {
      NotAcceptable(Json.toJson(validateProblems))
    }
    else {
      val problems = list.flatMap(db.ingestConfig)
      // Determine the subsystems being ingested, in order to remove previous subsystem if a new one is being ingested.
      // This helps to avoid problems when a component was renamed, so that the old component's mongodb collection is removed.
      val subsystemList = list.filter(_.stdName == StdName.subsystemFileNames).map(_.config.getString("subsystem"))

      // Check for duplicate subsystem-model.conf file (found one in TCS)
      val duplicateSubsystems = subsystemList.diff(subsystemList.toSet.toList)
      val duplicateComponents = db.checkForDuplicateComponentNames(list)
      if (duplicateSubsystems.nonEmpty) {
        val dupFileList = list.filter(_.stdName == StdName.subsystemFileNames).map(_.fileName).mkString(", ")
        val p           = Problem("error", s"Duplicate subsystem-model.conf found: $dupFileList") :: problems
        NotAcceptable(Json.toJson(p))
      }
      else if (duplicateComponents.nonEmpty) {
        val p = Problem("error", s"Duplicate component names found: ${duplicateComponents.mkString(", ")}") :: problems
        NotAcceptable(Json.toJson(p))
      }
      else {
        if (subsystemList.isEmpty)
          db.query.afterIngestFiles(problems, db.dbName)
        else {
          subsystemList.foreach(db.query.afterIngestSubsystem(_, problems, db.dbName))
        }
        if (problems.nonEmpty) {
          NotAcceptable(Json.toJson(problems))
        }
        else {
          // XXX TODO: Check if all referenced component names are valid and no duplicate componen-model.conf files were found!
          // TODO: Check if event and param names are valid
          Ok.as(JSON)
        }
      }
    }
  }
}
