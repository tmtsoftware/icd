package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.jquery.{jQuery => $, _}

import scalatags.JsDom.TypedTag

/**
 * Handles uploading an ICD directory or zip file to the play server.
 */
object FileUpload {
  import FileUtils._

  // Id of file select input item
  val fileSelect= "fileSelect"

  // id of messages item
  val messages = "messages"

  val errorSet = Set("error", "fatal")

  // standard ICD file names (See StdName class in icd-db. Reuse here?)
  val stdList = List("icd-model.conf", "component-model.conf", "publish-model.conf",
    "subscribe-model.conf", "command-model.conf")

  def isStdFile(file: dom.File): Boolean = stdList.contains(basename(file))

  /**
   * Describes any validation problems found
   * @param severity a string describing the error severity: fatal, error, warning, etc.
   * @param message describes the problem
   */
  case class Problem(severity: String, message: String) {
    def errorMessage(): String = s"$severity: $message"
  }

  // Initialize the upload page and callback
  def init(csrfToken: String, inputDirSupported: Boolean): Unit = {

    // Produce the HTML to display for the upload screen
    def uploadDialogMarkup(csrfToken: String, inputDirSupported: Boolean) = {
      import scalatags.JsDom.all._

      // Only Chrome supports uploading8 directories. For other browsers, use zip file upload
      val dirMsg = if (inputDirSupported)
        "Here you can select the top level directory containing the ICD to upload."
      else
        "Here you can select a zip file of the top level directory containing the ICD to upload."
      val dirLabel = if (inputDirSupported) "ICD Directory" else "Zip file containing ICD Directory"

      val acceptSuffix = if (inputDirSupported) "" else ".zip,application/zip"

      div(cls := "container",
        p(dirMsg),
        form(id := "upload", action := "/upload", "role".attr := "form",
          "method".attr := "POST", "enctype".attr := "multipart/form-data")(
            input(`type` := "hidden", name := "csrfToken", value := csrfToken, accept := acceptSuffix),
            div(`class` := "panel panel-info")(
              div(`class` := "panel-body")(
                div(
                  label(`for` := fileSelect, s"$dirLabel to upload:"),
                  input(`type` := "file", id := fileSelect, name := "files[]", multiple := "multiple",
                    "webkitdirectory".attr:="webkitdirectory")
                ),
                div(id := "submitButton", `class` := "hide")(
                  button(`type` := "submit")("Upload Files")
                )
              )
            )
          ),
        div(`class` := "progress")(
          div(id := "progress", `class` := "progress-bar progress-bar-info progress-bar-striped",
            "role".attr := "progressbar", "aria-valuenow".attr := "0", "aria-valuemin".attr := "0",
            "aria-valuemax".attr := "100", style := "width: 100%", "0%")
        ),
        h4("Status")(
          span(style := "margin-left:15px;"),
          span(id := "busyStatus", cls := "glyphicon glyphicon-refresh glyphicon-refresh-animate hide"),
          span(style := "margin-left:15px;"),
          span(id := "status", `class` := "label", "Working...")
        ),
        div(id := messages, `class` := "alert alert-info")
      )
    }

    // Called when the Upload item is selected
    def uploadSelected(e: dom.Event) = {
      LeftSidebar.uncheckAll()
      RightSidebar.uncheckAll()
      Main.setContent("Upload ICD", uploadDialogMarkup(csrfToken, inputDirSupported).toString())
      ready(inputDirSupported)
    }

    // Returns the HTML markup for the navbar item
    def markup(): TypedTag[Element] = {
      import scalatags.JsDom.all._
      li(a(onclick := uploadSelected _)("Upload"))
    }

    // Add the Upload item to the navbar
    Navbar.addItem(markup().render)
  }


  // Called once the file upload screen has been displayed
  def ready(inputDirSupported: Boolean): Unit = {
    implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]
    implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

    val maxFileSize = 3000000l

    var problemSet = Set[Problem]()

    // Adds an error (or warning) message to the upload messages
    def displayProblem(problem: Problem): Unit = {
      val m = $id(messages)
      if (m != null && problem.message != null && !problemSet.contains(problem)) {
        val msg = if (problem.message.trim.startsWith("<!DOCTYPE html>")) {
          problem.message
        } else {
          if (errorSet.contains(problem.severity))
            errorDiv(problem.message)
          else
            warningDiv(problem.message)
        }
        problemSet += problem
        m.innerHTML = msg + m.innerHTML
      }
    }

    // Clears the problem messages display
    def clearProblems(): Unit = {
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
    val busyStatusItem = $("#busyStatus")

    // Called when a file selection has been made
    def fileSelectHandler(e: dom.Event): Unit = {
      clearProblems()
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
        displayProblem(Problem("warning", s"${getFilePath(file)}: Ignored"))
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
          $("#progress").css("width", pc + "%").attr("aria-valuenow", pc.toString).html(s"${getFilePath(file)} ($pc %)")
        }

        // Displays status after upload complete
        def onloadListener(e: dom.Event) = {
          busyStatusItem.addClass("hide")
          val statusClass = if (xhr.status == 200) "label-success" else "label-danger"
          if (!statusItem.hasClass("label-danger")) {
            val statusMsg = if (xhr.status == 200) "Success" else xhr.statusText
            statusItem.removeClass("label-default").addClass(statusClass).text(statusMsg)
          }
          if (xhr.status != 200) {
            val problems = upickle.read[List[Problem]](xhr.responseText)
            for(problem <- problems)
              displayProblem(problem)
          }
        }

        xhr.upload.addEventListener("progress", progressListener _, useCapture = false)
        xhr.onload = onloadListener _

        //start upload
        statusItem.addClass("label-default").text("Working...")
        busyStatusItem.removeClass("hide")
        xhr.send(file)
      } else if (file.size > maxFileSize) {
        dom.alert(s"${file.name} is too large")
      }
    }

    $id(fileSelect).addEventListener("change", fileSelectHandler _, useCapture = false)
  }
}


