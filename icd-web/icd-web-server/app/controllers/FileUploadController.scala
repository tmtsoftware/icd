package controllers

import java.io.File
import java.util.UUID

import csw.services.icd.StdName
import play.api.Play
import play.api.Play.current
import play.api.mvc.{Action, Controller}
import play.filters.csrf.CSRF
import play.filters.csrf.CSRFAddToken
import play.filters.csrf.CSRFAddToken
import shared.Csrf

object FileUploadController extends Controller {

  val log = play.Logger.of("application") //same as play.Logger

//  val uploadDir = Play.getFile("upload")
//  if(!uploadDir.exists()) uploadDir.mkdir()

//  def index = CSRFAddToken { Action { implicit request =>
//    import play.filters.csrf.CSRF
//    val token = CSRF.getToken(request).map(t=>Csrf(t.value)).getOrElse(Csrf(""))
//    Ok(views.html.fileupload(token))
//  }}

//  def uploadFileWithFormData = Action(parse.multipartFormData) { request =>
//    request.body.files.zipWithIndex.foreach{ case(file, i) =>
//      log.info(s"file upload[$i]: ${file.filename}")
//      file.ref.moveTo(createFile(file.filename), false)
//    }
//    Ok("File uploaded")
//  }

//  def uploadFile = Action(parse.temporaryFile){ request =>
//    val file = request.body.file
//    val filename = request.headers.get("X-FILENAME").getOrElse(file.getName)
//    log.info(s"file upload: $filename")
//    request.body.moveTo(createFile(filename), replace = false)
//    Ok("File uploaded")
//  }


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
        println(s"XXX file content: ${request.body}")
        Ok("File uploaded")
      } else {
        val msg = s"${file.getName} is not a standard ICD file name"
        log.error(msg)
        NotAcceptable(msg)
      }
    } else {
      val msg = "Missing X-FILENAME header"
      println(s"XXX $msg")
      log.error(msg)
      BadRequest(msg)
    }
  }
//
//
//  private def createFile(name: String): File = {
//    val upload = uploadDir.getAbsolutePath + File.separatorChar
//    val extPos = name.lastIndexOf(".")
//    val ext = if(extPos != -1) name.substring(extPos) else ""
//    new File(upload+UUID.randomUUID.toString+ext)
//  }

}
