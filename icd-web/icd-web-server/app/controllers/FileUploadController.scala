package controllers

import com.mongodb.MongoTimeoutException
import com.typesafe.config.ConfigException
import csw.services.icd.db.StdConfig
import csw.services.icd.Problem
import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.mvc.{ WebSocket, Result, Action, Controller }

object FileUploadController extends Controller {

  private val log = play.Logger.of("application")
  private lazy val db = Application.db
  val (wsEnumerator, wsChannel) = Concurrent.broadcast[String]

  // Server side of the upload ICD feature.
  // Supported files: ICD files and .zip files of ICD files.
  def uploadFiles = Action(parse.multipartFormData) { request ⇒
    import upickle.default._
    val files = request.body.files.toList
    try {
      // XXX TODO: Return config parse errors in StdConfig.get with file names!
      val list = files.flatMap(filePart ⇒ StdConfig.get(filePart.ref.file, filePart.filename))
      val comment = request.body.asFormUrlEncoded.getOrElse("comment", List("")).head
      val majorVersion = false // XXX TODO
      ingestConfigs(list, comment, majorVersion)
    } catch {
      case e: MongoTimeoutException ⇒
        val msg = "Database seems to be down"
        log.error(msg, e)
        ServiceUnavailable(write(List(Problem("error", msg)))).as(JSON)
      case e: ConfigException ⇒
        val msg = e.getMessage
        log.error(msg, e)
        NotAcceptable(write(List(Problem("error", msg)))).as(JSON)
      case t: Throwable ⇒
        val msg = "Internal error"
        log.error(msg, t)
        InternalServerError(write(List(Problem("error", msg)))).as(JSON)
    }
  }

  /**
   * Uploads/ingests the given configs
   * @param list list of objects based on uploaded ICD files
   * @param comment change comment from user
   * @param majorVersion if true, increment the ICD's major version
   * @return the HTTP result (OK, or NotAcceptable[list of Problems in JSON format])
   */
  private def ingestConfigs(list: List[StdConfig], comment: String, majorVersion: Boolean = false): Result = {
    import upickle.default._
    val problems = list.flatMap(db.ingestConfig)

    wsChannel.push("update")

    if (problems.isEmpty) {
      Ok.as(JSON)
    } else {
      NotAcceptable(write(problems)).as(JSON)
    }
  }

  // Websocket used to notify client when upload is complete
  def ws = WebSocket.using[String] { request ⇒
    (Iteratee.ignore, wsEnumerator)
  }
}
