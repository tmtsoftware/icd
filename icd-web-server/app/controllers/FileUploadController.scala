package controllers

import javax.inject.Inject
import com.mongodb.MongoTimeoutException
import com.typesafe.config.ConfigException
import csw.services.icd.db.StdConfig
import csw.services.icd.{IcdValidator, Problem, StdName}
import icd.web.shared.JsonSupport
import play.api.Environment
import play.api.mvc._
import play.api.libs.json._
import org.webjars.play._

class FileUploadController @Inject()(env: Environment,
                                     webJarAssets: WebJarAssets,
                                     components: ControllerComponents)
    extends AbstractController(components) {
  import JsonSupport._

  private val log = play.Logger.of("application")
  private lazy val db = Application.db

  // Server side of the upload ICD feature.
  // Supported file types: A directory containing icd config files (chrome)
  // or a .zip file containing directories with icd config files.
  def uploadFiles = Action(parse.multipartFormData) { implicit request =>
    val files = request.body.files.toList
    try {
      // XXX TODO: Return config parse errors in StdConfig.get with file names!
      val list = files.flatMap(filePart =>
        StdConfig.get(filePart.ref.path.toFile, filePart.filename))
      val comment =
        request.body.asFormUrlEncoded.getOrElse("comment", List("")).head
      ingestConfigs(list, comment)
    } catch {
      case e: MongoTimeoutException =>
        val msg = "Database seems to be down"
        log.error(msg, e)
        ServiceUnavailable(Json.toJson(List(Problem("error", msg))))
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
    * @param list         list of objects based on uploaded ICD files
    * @param comment      change comment from user
    * @return the HTTP result (OK, or NotAcceptable[list of Problems in JSON format])
    */
  private def ingestConfigs(list: List[StdConfig], comment: String): Result = {
    import net.ceedubs.ficus.Ficus._
    // Get the schema version
    val schemaVersion = list
      .find(f =>
        f.stdName == StdName.subsystemFileNames || f.stdName == StdName.componentFileNames)
      .map(_.config.as[String](IcdValidator.schemaVersionKey))
      .getOrElse(IcdValidator.currentSchemaVersion)

    // Validate everything first
    val validateProblems =
      list.flatMap(sc => IcdValidator.validate(sc.config, sc.stdName, schemaVersion))
    if (validateProblems.nonEmpty) {
      NotAcceptable(Json.toJson(validateProblems))
    } else {
      val problems = list.flatMap(db.ingestConfig)
      if (problems.nonEmpty) {
        NotAcceptable(Json.toJson(problems))
      } else {
        // XXX TODO: Check if all referenced component names are valid
        Ok.as(JSON)
      }
    }
  }
}
