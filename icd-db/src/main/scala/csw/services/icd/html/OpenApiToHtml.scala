package csw.services.icd.html

import java.io.File
import java.nio.file.Files
import scala.io.Source
import scala.reflect.io.Directory
import scala.util.{Failure, Success, Try}
import sys.process._

object OpenApiToHtml {
  // Return this HTML if there is an error
  private def errorDiv(msg: String) = {
    s"<div><p>Warning: Error generating HTML from OpenApi description: $msg.</p></div>"
  }

  // Gets the directory containing the swagger-codegen templates
  // (Need to pass this directory as an option to the swagger-codegen command to customize the HTML)
  // The layout is different for static and dynamic HTML (static is for PDF, dynamic for web).
  private def getTemplateDir(staticHtml: Boolean): Option[File] = {
    val name        = if (staticHtml) "htmlDocs" else "htmlDocs2"
    val classDir    = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath).getParentFile.getParentFile
    val templateDir = new File(classDir, s"conf/handlebars/$name")
    val devDir      = new File(classDir.getParentFile, s"src/universal/conf/handlebars/$name")
    if (templateDir.exists()) Some(templateDir) else if (devDir.exists()) Some(devDir) else None
  }

  // Saves the openApi JSON to a file so it can be passed to the swagger-codegen command
  private def saveOpenApiJson(openApi: String): File = {
    val tmpFile = Files.createTempFile("openApi", ".json")
    Files.write(tmpFile, openApi.getBytes)
    tmpFile.toFile
  }

  def getHtml(openApi: String, staticHtml: Boolean): String = {
    val maybeDir = getTemplateDir(staticHtml)
    val format   = if (staticHtml) "html" else "html2"
    val maybeHtml = maybeDir.map { dir =>
      val openApiFile = saveOpenApiJson(openApi)
      val tempDir     = Files.createTempDirectory("openApi").toFile
      val cmd         = s"sh -c 'swagger-codegen generate -i $openApiFile -l $format -o $tempDir'"
      val x           = Try(cmd.!)
      openApiFile.delete()
      x match {
        case Failure(ex) =>
          errorDiv(ex.getMessage)
        case Success(_) =>
          val indexFile = new File(s"$tempDir/index.html")
          val html = if (indexFile.exists()) {
            val source = Source.fromFile(indexFile)
            val s      = source.mkString
            source.close()
            s
          }
          else {
            errorDiv(s"$indexFile was not generated")
          }
          new Directory(tempDir).deleteRecursively()
          html
      }
    }
    maybeHtml.getOrElse(errorDiv("Could not find swagger-codegen template dir"))
  }
}
