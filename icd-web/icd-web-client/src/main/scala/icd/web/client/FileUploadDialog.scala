package icd.web.client

import icd.web.client.FileUtils._
import org.scalajs.dom
import org.scalajs.dom._
import scala.language.implicitConversions
import org.scalajs.jquery.{ jQuery ⇒ $, _ }

/**
 * Displays the page for uploading ICD files and directories
 */
case class FileUploadDialog(csrfToken: String, inputDirSupported: Boolean) extends Displayable {

  implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]

  implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

  private val maxFileSize = 3000000l
  private val errorSet = Set("error", "fatal")
  private var problemSet = Set[Problem]()

  // standard ICD file names (See StdName class in icd-db. Reuse here?)
  private val stdList = List("subsystem-model.conf", "component-model.conf", "publish-model.conf",
    "subscribe-model.conf", "command-model.conf")

  // Displays upload button
  private val inputItem = {
    import scalatags.JsDom.all._
    input(`type` := "file", name := "files[]", multiple := "multiple",
      "webkitdirectory".attr := "webkitdirectory",
      onclick := fileSelectReset _,
      onchange := fileSelectHandler _).render
  }

  // True if the file is one of the standard ICD files
  private def isStdFile(file: dom.File): Boolean = stdList.contains(basename(file))

  // HTML item displaying error messages
  val messagesItem = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(Styles.fileUploadMessages).render
  }

  // Adds an error (or warning) message to the upload messages
  def displayProblem(problem: Problem): Unit = {
    if (problem.message != null && !problemSet.contains(problem)) {
      val msg = if (problem.message.trim.startsWith("<!DOCTYPE html>")) {
        problem.message
      } else {
        if (errorSet.contains(problem.severity))
          errorDiv(problem.message)
        else
          warningDiv(problem.message)
      }
      problemSet += problem
      messagesItem.innerHTML = msg + messagesItem.innerHTML
    }
  }

  // Clears the problem messages display
  def clearProblems(): Unit = {
    messagesItem.innerHTML = ""
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
    val fileList = for (i ← 0 until files.length) yield files(i).asInstanceOf[WebkitFile]
    fileList.partition(isValidFile)
  }

  def statusItem = $("#status")

  def busyStatusItem = $("#busyStatus")

  // Called when user clicks on input item.
  // Reset the value (Otherwise you can't upload the same file twice,
  // since it won't fire the change event)
  def fileSelectReset(e: dom.Event): Unit = {
    clearProblems()
    inputItem.value = ""
  }

  // Called when a file selection has been made
  def fileSelectHandler(e: dom.Event): Unit = {
    clearProblems()
    statusItem.removeClass("label-danger")
    val (validFiles, invalidFiles) = getIcdFiles(e)
    uploadFiles(validFiles.toList)

    // list ignored files:
    for (file ← invalidFiles)
      displayProblem(Problem("warning", s"${getFilePath(file)}: Ignored"))
  }

  // Starts uploading the selected files (or files in selected directory) to the server
  def uploadFiles(files: List[WebkitFile]) = {
    val formData = new FormData()
    for (file ← files if isValidFile(file)) {
      formData.append(getFilePath(file), file)
    }
    //    formData.append("comment", commentBox.value)

    val xhr = new dom.XMLHttpRequest
    xhr.open("POST", Routes.uploadFiles, async = true)

    // Updates progress bar during upload
    def progressListener(e: dom.Event): Unit = {
      val pc = e.loaded / e.total * 100
      $("#progress").css("width", pc + "%").attr("aria-valuenow", pc.toString).html(s"$pc %")
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
        for (problem ← problems)
          displayProblem(problem)
      }
    }

    xhr.upload.addEventListener("progress", progressListener _, useCapture = false)
    xhr.onload = onloadListener _

    //start upload
    statusItem.addClass("label-default").text("Working...")
    busyStatusItem.removeClass("hide")
    xhr.send(formData)
  }

  // Produce the HTML to display for the upload screen
  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    import scalatags.JsDom.all._

    // Only Chrome supports uploading8 directories. For other browsers, use zip file upload
    val dirMsg = if (inputDirSupported)
      "Here you can select the top level directory containing the subsystem or component files to upload."
    else
      "Here you can select a zip file of the top level directory containing the subsystem or component files to upload."
    val dirLabel = if (inputDirSupported) "ICD Directory" else "Zip file containing ICD Directory"

    val acceptSuffix = if (inputDirSupported) "" else ".zip,application/zip"

    div(cls := "container",
      p(dirMsg),
      form(id := "upload", action := "/upload", "role".attr := "form",
        "method".attr := "POST", "enctype".attr := "multipart/form-data")(
          input(`type` := "hidden", name := "csrfToken", value := csrfToken, accept := acceptSuffix),
          div(cls := "panel panel-info")(
            div(cls := "panel-body")(
              div(label(s"$dirLabel to upload:")(inputItem)),
              //              div(Styles.commentBox, label("Comments")(commentBox)),
              div(cls := "hide")(
                button(`type` := "submit")("Upload Files"))))),
      div(cls := "progress")(
        div(id := "progress", cls := "progress-bar progress-bar-info progress-bar-striped",
          "role".attr := "progressbar", "aria-valuenow".attr := "0", "aria-valuemin".attr := "0",
          "aria-valuemax".attr := "100", style := "width: 100%", "0%")),
      h4("Status")(
        span(style := "margin-left:15px;"),
        span(id := "busyStatus", cls := "glyphicon glyphicon-refresh glyphicon-refresh-animate hide"),
        span(style := "margin-left:15px;"),
        span(id := "status", cls := "label", "Working...")),
      messagesItem).render
  }
}
