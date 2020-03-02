package controllers

import javax.inject.Inject
import com.typesafe.config.ConfigException
import csw.services.icd.db.StdConfig
import csw.services.icd.{IcdValidator, Problem, StdName}
import play.api.Environment
import play.api.mvc._
import play.api.libs.json._
import org.webjars.play._
import play.api.libs.Files

class FileUploadController @Inject()(env: Environment, webJarAssets: WebJarAssets, components: ControllerComponents)
    extends AbstractController(components) {

  private val log     = play.Logger.of("application")
  private lazy val db = ApplicationData.tryDb.get

  // Server side of the upload ICD feature.
  // Supported file types: A directory containing icd config files (chrome)
  // or a .zip file containing directories with icd config files.
  def uploadFiles(): Action[MultipartFormData[Files.TemporaryFile]] = Action(parse.multipartFormData) { implicit request =>
    val files = request.body.files.toList
    try {
      val list = files.flatMap { filePart =>
        StdConfig.get(filePart.ref.path.toFile, filePart.filename)
      }
      val comment =
        request.body.asFormUrlEncoded.getOrElse("comment", List("")).head
      ingestConfigs(list, comment)
    } catch {
      case e: ConfigException =>
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
   * @param comment change comment from user
   * @return the HTTP result (OK, or NotAcceptable[list of Problems in JSON format])
   */
  private def ingestConfigs(list: List[StdConfig], comment: String): Result = {
    import net.ceedubs.ficus.Ficus._

    // Note: The user may have selected a top level dir containing subdirs that use different schema versions,
    // so this needs to be updated for each subsystem and component in the list of model files received
    var schemaVersion = IcdValidator.currentSchemaVersion

    // Validate everything first
    val validateProblems =
      list.flatMap { sc =>
        // Update the schema version being used any time we come across a component or subsystem model
        if (sc.stdName == StdName.subsystemFileNames || sc.stdName == StdName.componentFileNames)
          schemaVersion = sc.config.as[Option[String]](IcdValidator.schemaVersionKey).getOrElse(IcdValidator.currentSchemaVersion)
        IcdValidator.validateStdName(sc.config, sc.stdName, schemaVersion, sc.fileName)
      }
    if (validateProblems.nonEmpty) {
      NotAcceptable(Json.toJson(validateProblems))
    } else {
      val problems = list.flatMap(db.ingestConfig)
      db.query.afterIngestFiles(problems, db.dbName)
      if (problems.nonEmpty) {
        NotAcceptable(Json.toJson(problems))
      } else {
        // XXX TODO: Check if all referenced component names are valid
        Ok.as(JSON)
      }
    }
  }
}
