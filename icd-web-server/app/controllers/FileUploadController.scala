package controllers

import javax.inject.Inject
import play.api.mvc._
import play.api.libs.json._

import java.io.File
import java.nio.file.Files

class FileUploadController @Inject() (components: ControllerComponents) extends AbstractController(components) {

  private val log     = play.Logger.of("application")
  private lazy val db = ApplicationData.tryDb.get

  /**
   * Deletes the contents of the given temporary directory (recursively).
   */
  private def deleteDirectoryRecursively(dir: File): Unit = {
    val tmpDir = System.getProperty("java.io.tmpdir")
    // just to be safe, don't delete anything that is not in /tmp/
    val p = dir.getPath
    if (!p.startsWith("/tmp/") && !p.startsWith(tmpDir))
      throw new RuntimeException(s"Refusing to delete $dir since not in /tmp/ or $tmpDir")

    if (dir.isDirectory) {
      dir.list.foreach { filePath =>
        val file = new File(dir, filePath)
        if (file.isDirectory) {
          deleteDirectoryRecursively(file)
        }
        else {
          file.delete()
        }
      }
      dir.delete()
    }
  }

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
