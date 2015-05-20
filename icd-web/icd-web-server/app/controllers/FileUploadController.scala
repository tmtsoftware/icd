package controllers

import java.io.File
import java.util.zip.ZipFile

import com.mongodb.MongoTimeoutException
import com.typesafe.config.{ ConfigException, Config, ConfigFactory }
import csw.services.icd.db.StdConfig
import csw.services.icd.{ Problem, StdName }
import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.mvc.{ WebSocket, Result, Action, Controller }
import play.api.libs.json._

object FileUploadController extends Controller {

  private val log = play.Logger.of("application")
  private lazy val db = Application.db
  val (wsEnumerator, wsChannel) = Concurrent.broadcast[String]

  // Converts a Problem (returned from ICD validate method) to JSON
  implicit val problemWrites = new Writes[Problem] {
    def writes(problem: Problem) = Json.obj(
      "severity" -> problem.severity,
      "message" -> problem.message)
  }

  // Server side of the upload ICD feature.
  // The uploaded file should be a single .conf file with X-FILENAME giving the relative path
  // (which is needed to determine where in the ICD it belongs).
  def uploadFile = Action(parse.tolerantText) { request ⇒
    val fileNameOpt = request.headers.get("X-FILENAME")
    val lastFile = request.headers.get("X-Last-File").getOrElse("") == "true"
    val result = if (fileNameOpt.isDefined) {
      val file = new File(fileNameOpt.get)
      ingestFile(file, request.body, lastFile)
    } else {
      val msg = "Missing X-FILENAME header"
      log.error(msg)
      BadRequest(Json.toJson(List(Problem("error", msg))))
    }
    if (lastFile) {
      wsChannel.push("update")
    }
    result
  }

  // Ingest the given ICD file with the given contents into the database
  private def ingestFile(file: File, contents: String, lastFile: Boolean): Result = {
    // Check that the file name is one of the standard names
    if (StdName.stdSet.contains(file.getName)) {
      log.info(s"file upload: $file")
      try {
        val config = ConfigFactory.parseString(contents)
        val problems = ingestConfig(config, file.toString, lastFile)
        if (problems.isEmpty) Ok(Json.toJson(Nil)) else NotAcceptable(Json.toJson(problems))
      } catch handleExceptions(Some(file.toString))
    } else {
      val problem = Problem("error", s"${file.getName} is not a standard ICD file name")
      NotAcceptable(Json.toJson(List(problem)))
    }
  }

  // Ingest the given config (part of an ICD) into the database and return an error message,
  // if there is a problem
  private def ingestConfig(config: Config, path: String, lastFile: Boolean): List[Problem] = {
    val stdConfig = StdConfig.get(config, path).get
    val problems = db.ingestConfig(stdConfig)
    if (lastFile && problems.isEmpty) {
      // XXX TODO FIXME: add comment field, version field, check all subsystem values
      val comment: String = ""
      val majorVersion: Boolean = false
      // XXX TODO: maybe use flash or session scope to check with other subsystem values?
      val subsystem = db.getSubsystemName(stdConfig)
      db.manager.newVersion(subsystem, comment, majorVersion)
    }
    problems
  }

  private def handleExceptions(fileNameOpt: Option[String]): PartialFunction[Throwable, Result] = {
    case e: MongoTimeoutException ⇒
      val msg = "Database seems to be down"
      log.error(msg, e)
      ServiceUnavailable(Json.toJson(List(Problem("error", msg))))
    case e: ConfigException ⇒
      val file = fileNameOpt.getOrElse("")
      val msg = s"$file: ${e.getMessage}"
      log.error(msg, e)
      NotAcceptable(Json.toJson(List(Problem("error", msg))))
    case t: Throwable ⇒
      val msg = "Internal error"
      log.error(msg, t)
      InternalServerError(Json.toJson(List(Problem("error", msg))))
  }

  // Uploads/ingests the ICD files together in a zip file
  def uploadZipFile = Action(parse.temporaryFile) { request ⇒
    try {
      // XXX TODO: Return config parse errors in StdConfig.get with file names!
      val list = StdConfig.get(new ZipFile(request.body.file))
      val problems = list.flatMap(db.ingestConfig)
      wsChannel.push("update")

      // Check the subsystem names
      val subsystems = list.map(stdConfig ⇒ db.getSubsystemName(stdConfig)).distinct
      val errors = if (subsystems.length != 1)
        problems ::: db.multipleSubsystemsError(subsystems)
      else problems

      if (problems.isEmpty) {
        // XXX TODO FIXME
        val comment: String = ""
        val majorVersion: Boolean = false
        db.manager.newVersion(subsystems.head, comment, majorVersion)
      }

      // XXX TODO FIXME: Return a JSON object with list of error messages (Problem instances?)!
      if (errors.isEmpty)
        Ok(Json.toJson(Nil))
      else
        NotAcceptable(Json.toJson(errors))

    } catch handleExceptions(None)
  }

  // Websocket used to notify client when upload is complete
  def ws = WebSocket.using[String] { request ⇒
    (Iteratee.ignore, wsEnumerator)
  }
}
