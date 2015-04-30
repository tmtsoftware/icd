package icd.web.client

import org.scalajs.dom
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.jquery.{jQuery => $, _}

@JSExport
object FileUpload {
  import FileUtils._

  // Id of file select input item
  val fileSelect= "fileSelect"

  // id of messages item
  val messages = "messages"

  // standard ICD file names (See StdName class in icd-db. Reuse here?)
  val stdList = List("icd-model.conf", "component-model.conf", "publish-model.conf",
    "subscribe-model.conf", "command-model.conf")

  def isStdFile(file: dom.File): Boolean = stdList.contains(basename(file))

  @JSExport
  // Initialize the upload page and callback
  def init(csrfToken: String, inputDirSupported: Boolean): Unit = {

    // Produce the HTML to display for the upload screen
    def markup(csrfToken: String, inputDirSupported: Boolean) = {
      import scalatags.JsDom.all._

      // Only Chrome supports uploading8 directories. For other browsers, use zip file upload
      val dirMsg = if (inputDirSupported)
        "Here you can select the top level directory containing the ICD to upload."
      else
        "Here you can select a zip file of the top level directory containing the ICD to upload."
      val dirLabel = if (inputDirSupported) "ICD Directory" else "Zip file containing ICD Directory"

      val acceptSuffix = if (inputDirSupported) "" else ".zip,application/zip"
      div(
        p(dirMsg),
        form(id := "upload", action := "/upload", "role".attr := "form",
          "method".attr := "POST", "enctype".attr := "multipart/form-data")(
            input(`type` := "hidden", name := "csrfToken", value := csrfToken, accept := acceptSuffix),
            div(`class` := "panel panel-info")(
              div(`class` := "panel-body")(
                div(
                  label(`for` := fileSelect, s"$dirLabel to upload:"),
                  input(`type` := "file", id := fileSelect, name := "files[]", "webkitdirectory".attr:="webkitdirectory")
                ),
                div(id := "submitButton", `class` := "hide")(
                  button(`type` := "submit")("Upload Files")
                )
              )
            )
          ),
        div(`class` := "progress")(
          div(id := "progress", `class` := "progress-bar progress-bar-success progress-bar-striped", "role".attr := "progressbar", "aria-valuenow".attr := "0",
            "aria-valuemin".attr := "0", "aria-valuemax".attr := "100", style := "width: 100%", "0%")
        ),
        h4("Status")(span(style := "margin-left:15px;"), span(id := "status", `class` := "label hide", "Done")),
        div(id := messages, `class` := "alert alert-info")
      )
    }

    // Called when the Upload item is selected
    def uploadSelected(e: dom.Event) = {
      val content = $id("content")
      val children = content.childNodes
      for (i <- (0 until children.length).reverse) {
        content.removeChild(children(i))
      }
      $id("contentTitle").textContent = "Upload ICD"
      $id("content").appendChild(markup(csrfToken, inputDirSupported).render)
      ready(inputDirSupported)
    }

    $id("uploadButton").addEventListener("click", uploadSelected _, useCapture = false)
  }

  // Called once the file upload screen has been displayed
  def ready(inputDirSupported: Boolean): Unit = {
    implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]
    implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

    val maxFileSize = 3000000l

    // Adds an error message to the upload messages
    def uploadMessage(file: WebkitFile, status: String, isError: Boolean): Unit = {
      import scalatags.JsDom.all._
      val m = $id(messages)
      // XXX Probably should not display stack trace to use here...
      val msg = if (status.trim.startsWith("<!DOCTYPE html>")) {
        status
      } else {
        val msg = s"${getFilePath(file)}: $status"
        if (isError) errorDiv(msg) else warningDiv(msg)
      }
      m.innerHTML = msg + m.innerHTML
    }

    // Clears the upload messages
    def clearUploadMessages(): Unit = {
      val m = $id(messages)
      m.innerHTML = ""
    }

    // Gets the full path, if supported (webkit/chrome), otherwise the simple file name
    def getFilePath(file: WebkitFile): String = {
      if (inputDirSupported) file.webkitRelativePath else file.name
    }

    // Returns true if the file is a valid ICD file name
    def isValidFile(file: dom.File): Boolean =
      if (inputDirSupported) isStdFile(file) else file.name.endsWith(".zip")

    // Returns a pair of lists containing the valid and invalid ICD files
    def getIcdFiles(e: dom.Event): (Seq[WebkitFile], Seq[WebkitFile]) = {
      val files = e.target.files
      val fileList = for(i <- 0 until files.length) yield files(i).asInstanceOf[WebkitFile]
      fileList.partition(isValidFile)
    }

    val statusItem = $("#status")

    // Called when a file selection has been made
    def fileSelectHandler(e: dom.Event): Unit = {
      clearUploadMessages()
      statusItem.removeClass("label-danger")
      val (validFiles, invalidFiles) = getIcdFiles(e)
      for((file, i) <- validFiles.zipWithIndex) {
        try {
          parseFile(file)
          uploadFile(file, i == validFiles.size - 1)
        } catch {
          case e: Throwable => println(e)
        }
      }

      // list ignored files:
      for(file <- invalidFiles)
        uploadMessage(file, "Ignored", isError = false)
    }

    // Starts the file read
    def parseFile(file: WebkitFile) = {
      val reader = new FileReader()
      if (file.name.endsWith(".zip"))
        reader.readAsDataURL(file)
      else
        reader.readAsText(file)
    }

    // Returns the server action to use to upload the given file
    def actionFor(file: WebkitFile): String =
      if (file.name.endsWith("zip")) "/uploadZip" else "/upload"

    // Starts uploading the file to the server
    // (lastFile indicates if it is the last file in the list to be uploaded.)
    def uploadFile(file: WebkitFile, lastFile: Boolean) = {
      val xhr = new dom.XMLHttpRequest
      if (xhr.upload != null && file.size <= maxFileSize) {
        xhr.open("POST", actionFor(file), async = true)
        xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest")
        xhr.setRequestHeader("X-Last-File", lastFile.toString)
        xhr.setRequestHeader("X-FILENAME", getFilePath(file))

        // Updates progress bar during upload
        def progressListener(e: dom.Event): Unit = {
          val pc = e.loaded / e.total * 100
          $("#progress").css("with", pc + "%").attr("aria-valuenow", pc.toString).html(s"${getFilePath(file)} ($pc %)")
        }

        // Displays status after upload complete
        def onloadListener(e: dom.Event) = {
          val statusClass = if (xhr.status == 200) "label-success" else "label-danger"
          if (!statusItem.hasClass("label-danger")) {
            val statusMsg = if (xhr.status == 200) "Success" else xhr.statusText
            statusItem.removeClass("hide").addClass(statusClass).text(statusMsg)
          }
          if (xhr.status != 200)
            uploadMessage(file, xhr.responseText, isError = true)
        }

        xhr.upload.addEventListener("progress", progressListener _, useCapture = false)
        xhr.onload = onloadListener _


        //start upload
        statusItem.addClass("hide")
        xhr.send(file)
      } else if (file.size > maxFileSize) {
        dom.alert(s"${file.name} is too large")
      }
    }

    $id(fileSelect).addEventListener("change", fileSelectHandler _, useCapture = false)
  }
}


