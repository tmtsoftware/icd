package controllers

import java.io.File
import java.util.UUID

import com.typesafe.config.ConfigFactory
import csw.services.icd.StdName
import csw.services.icd.db.IcdDb
import play.api.Play
import play.api.Play.current
import play.api.mvc.{Action, Controller}
import play.filters.csrf.CSRF
import play.filters.csrf.CSRFAddToken
import play.filters.csrf.CSRFAddToken
import shared.Csrf

object FileUploadController extends Controller {

  val log = play.Logger.of("application") //same as play.Logger

  private def getCollectionName(file: File): String = {
    val s = file.toString.replace('/', '.')
    s.substring(0, s.length-"-model.conf".length)
  }

  // XXX check content type on client and use text?
  def uploadFile = Action(parse.tolerantText) { request =>
    println(s"XXX file upload")
    val fileNameOpt = request.headers.get("X-FILENAME")
    if (fileNameOpt.isDefined) {
      val file = new File(fileNameOpt.get)
      // Check that the file name is one of the standard names
      val stdSet = StdName.stdNames.map(_.name).toSet
      if (stdSet.contains(file.getName)) {
        log.info(s"file upload: $file")
//        println(s"XXX coll name = ${getCollectionName(file)}")
        val config = ConfigFactory.parseString(request.body)
        // XXX TODO: configure db name
        IcdDb("test").ingestConfig(getCollectionName(file), config)
        Ok("File uploaded")
      } else {
        val msg = s"${file.getName} is not a standard ICD file name"
        log.error(msg)
        NotAcceptable(msg)
      }
    } else {
      val msg = "Missing X-FILENAME header"
      log.error(msg)
      BadRequest(msg)
    }
  }
}
