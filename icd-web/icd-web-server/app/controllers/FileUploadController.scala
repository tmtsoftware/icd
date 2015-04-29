package controllers

import java.io.{InputStreamReader, File}
import java.util.zip.{ZipEntry, ZipFile}

import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd.StdName
import csw.services.icd.db.IcdDb
import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}
import play.api.mvc.{WebSocket, Result, Action, Controller}

object FileUploadController extends Controller {

  val log = play.Logger.of("application") //same as play.Logger
  val stdSet = StdName.stdNames.map(_.name).toSet
  val (wsEnumerator, wsChannel) = Concurrent.broadcast[String]


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
      BadRequest(msg)
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
      ingestConfig(ConfigFactory.parseString(contents), file.toString)
    } else {
      val msg = s"${file.getName} is not a standard ICD file name"
      log.error(msg)
      NotAcceptable(msg)
    }
  }

  // Ingest the given config (part of an ICD) into the database
  private def ingestConfig(config: Config, path: String): Result = {
    log.info(s"XXX ingestConfig $path")
    val db = IcdDb("test") // XXX reuse or pass as param
    db.ingestConfig(getCollectionName(path), config)
    db.close()
    Ok("File uploaded")
  }

  // Uploads/ingests the ICD files together in a zip file
  def uploadZipFile = Action(parse.temporaryFile) { request =>
    import scala.collection.JavaConversions._
    log.info(s"XXX uploadZipFile")
    val zipFile = new ZipFile(request.body.file)
    def isValid(f: ZipEntry) = stdSet.contains(new File(f.getName).getName)
    zipFile.entries().filter(isValid).foreach { e =>
      ingestConfig(
        ConfigFactory.parseReader(new InputStreamReader(zipFile.getInputStream(e))),
        e.getName)
    }
    wsChannel.push("update")
    Ok(s"Files uploaded")
  }

  // Websocket used to notify client when upload is complete
  def ws = WebSocket.using[String] { request =>
    (Iteratee.ignore, wsEnumerator)
  }
}
