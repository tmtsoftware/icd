package controllers

import csw.services.icd.deleteDirectoryRecursively

import javax.inject.Inject
import play.api.mvc.*
import play.api.libs.json.*

import java.io.File
import java.nio.file.Files

class FileUploadController @Inject() (components: ControllerComponents) extends AbstractController(components) {

  private lazy val db = ApplicationData.tryDb.get

  // Server side of the upload ICD feature.
  // Supported file types: A directory containing icd model files (and supported resources)
  def uploadFiles(): Action[MultipartFormData[play.api.libs.Files.TemporaryFile]] =
    Action(parse.multipartFormData) { implicit request =>
      val files = request.body.files.toList

      // Save uploaded files to a temp dir
      val tempDir = Files.createTempDirectory("icd").toFile
      files.foreach { filePart =>
        val newFile = new File(tempDir, filePart.filename)
        if (!newFile.getParentFile.exists()) newFile.getParentFile.mkdirs()
        filePart.ref.copyTo(newFile)
      }
      val problems = db.ingestAndCleanup(tempDir)
      deleteDirectoryRecursively(tempDir)

      if (problems.nonEmpty)
        NotAcceptable(Json.toJson(problems))
      else
        Ok.as(JSON)
    }
}
