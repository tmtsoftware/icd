package controllers

import java.io.{InputStreamReader, File}
import java.util.zip.{ZipEntry, ZipFile}

import com.mongodb.MongoTimeoutException
import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd.{Problem, IcdValidator, StdName}
import csw.services.icd.db.IcdDb
import play.Play
import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.mvc.{WebSocket, Result, Action, Controller}
import play.api.libs.json._

object FileUploadController extends Controller {

  val log = play.Logger.of("application")
  //same as play.Logger
  val stdSet = StdName.stdNames.map(_.name).toSet
  val (wsEnumerator, wsChannel) = Concurrent.broadcast[String]
  lazy val db = Application.db

  // Converts a Problem (returned from ICD validate method) to JSON
  implicit val problemWrites = new Writes[Problem] {
    def writes(problem: Problem) = Json.obj(
      "severity" -> problem.severity,
      "message" -> problem.message
    )
  }

  // Gets the database collection name for the given relative file path
  private def getCollectionName(path: String): String = {
    val s = path.replace('/', '.')
    s.substring(0, s.length - "-model.conf".length)
  }

  // Server side of the upload ICD feature.
  // The uploaded file may be a single .conf file with X-FILENAME giving the relative path
  // (which is needed to determine where in the ICD it belongs).
  // Alternatively, the file can be a zip file containing all the ICD files.
  // XXX check content type on client and use text?
  def uploadFile = Action(parse.tolerantText) { request =>
    val fileNameOpt = request.headers.get("X-FILENAME")
    val result = if (fileNameOpt.isDefined) {
      val file = new File(fileNameOpt.get)
      ingestFile(file, request.body)
    } else {
      val msg = "Missing X-FILENAME header"
      log.error(msg)
      BadRequest(Json.toJson(List(Problem("error", msg))))
    }
    log.info(s"XXX last = ${request.headers.get("X-Last-File")}")
    if (request.headers.get("X-Last-File").getOrElse("") == "true")
      wsChannel.push("update")
    result
  }

  // Ingest the given ICD file with the given contents into the database
  private def ingestFile(file: File, contents: String): Result = {
    log.info(s"XXX ingestFile $file")
    // Check that the file name is one of the standard names
    if (stdSet.contains(file.getName)) {
      log.info(s"file upload: $file")
      try {
        val problems = ingestConfig(ConfigFactory.parseString(contents), file.toString)
        if (problems.isEmpty) Ok(Json.toJson(Nil)) else NotAcceptable(Json.toJson(problems))
      } catch handleExceptions
    } else {
      val problem = Problem("error", s"${file.getName} is not a standard ICD file name")
      NotAcceptable(Json.toJson(List(problem)))
    }
  }

  val handleExceptions: PartialFunction[Throwable, Result] = {
    case e: MongoTimeoutException =>
      val msg = "Database seems to be down"
      log.error(msg, e)
      ServiceUnavailable(Json.toJson(List(Problem("error", msg))))
    case t: Throwable =>
      val msg = "Internal error"
      log.error(msg)
      InternalServerError(Json.toJson(List(Problem("error", msg))))
  }

  // Ingest the given config (part of an ICD) into the database and return an error message,
  // if there is a problem
  private def ingestConfig(config: Config, path: String): List[Problem] = {
    val problems = IcdValidator.validate(config, path)
    if (problems.isEmpty) {
      db.ingestConfig(getCollectionName(path), config)
      Nil
    } else {
      problems
    }
  }

  // Uploads/ingests the ICD files together in a zip file
  def uploadZipFile = Action(parse.temporaryFile) { request =>
    import scala.collection.JavaConversions._
    try {
      val zipFile = new ZipFile(request.body.file)
      def isValid(f: ZipEntry) = stdSet.contains(new File(f.getName).getName)
      val entries = zipFile.entries().filter(isValid)
      val results = for (e <- entries) yield {
        val reader = new InputStreamReader(zipFile.getInputStream(e))
        val config = ConfigFactory.parseReader(reader)
        ingestConfig(config, e.getName)
      }
      wsChannel.push("update")
      val errors = results.flatten.toList
      // XXX TODO FIXME: Return a JSON object with list of error messages (Problem instances?)!
      if (errors.isEmpty)
        Ok(Json.toJson(Nil))
      else
        NotAcceptable(Json.toJson(errors))
    } catch handleExceptions
  }

  // Websocket used to notify client when upload is complete
  def ws = WebSocket.using[String] { request =>
    (Iteratee.ignore, wsEnumerator)
  }
}
