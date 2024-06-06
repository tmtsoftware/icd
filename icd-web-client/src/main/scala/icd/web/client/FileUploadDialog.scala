package icd.web.client

import icd.web.client.FileUtils.*
import org.scalajs.dom
import org.scalajs.dom.*
import org.scalajs.dom.HTMLDivElement
import play.api.libs.json.*

import scala.language.implicitConversions

/**
 * Displays the page for uploading ICD files and directories
 */
case class FileUploadDialog(subsystemNames: SubsystemNames, csrfToken: String, inputDirSupported: Boolean) extends Displayable {

  implicit val problemFormat: OFormat[Problem] = Json.format[Problem]

  implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]

  implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

  private val errorSet   = Set("error", "fatal")
  private var problemSet = Set[Problem]()

  // standard ICD file names (XXX TODO FIXME: See StdName class in icd-db. Reuse here?)
  private val stdList =
    List(
      "subsystem-model.conf",
      "component-model.conf",
      "publish-model.conf",
      "subscribe-model.conf",
      "command-model.conf",
      "alarm-model.conf",
      "service-model.conf"
    )

  // Displays upload button
  private val inputItem = {
    import scalatags.JsDom.all.*
    input(
      `type` := "file",
      name := "files[]",
      multiple := "multiple",
      attr("webkitdirectory") := "webkitdirectory",
      onclick := fileSelectReset _,
      onchange := fileSelectHandler _
    ).render
  }

  // True if the file is one of the standard ICD files (or supported resources)
  private def isStdFile(file: WebkitFile): Boolean = {
    stdList.contains(basename(file)) || file.name.endsWith("-icd-model.conf") || file.name.endsWith(".json") || file.name
      .endsWith(".yml") || file.name.endsWith(".yaml") || file.name.endsWith(".jsonnet") || file.name.endsWith(
      ".libsonnet"
    ) || file.name.endsWith(".txt") || file.name.endsWith("FITS-Tags.conf") || file.name.endsWith("FITS-Channels.conf")
  }

  // HTML item displaying error messages
  private val messagesItem = {
    import scalatags.JsDom.all.*

    div(id := "fileUploadMessages", cls := "alert alert-info").render
  }

  // Adds an error (or warning) message to the upload messages
  private def displayProblem(problem: Problem): Unit = {
    if (problem.message != null && !problemSet.contains(problem)) {
      val msg = if (problem.message.trim.startsWith("<!DOCTYPE html>")) {
        problem.message
      }
      else {
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
  private def clearProblems(): Unit = {
    messagesItem.innerHTML = ""
    problemSet = Set[Problem]()
  }

  // Gets the full path, if supported (webkit/chrome), otherwise the simple file name
  private def getFilePath(file: WebkitFile): String = {
    if (inputDirSupported) file.webkitRelativePath else file.name
  }

  // Returns true if the file is a valid ICD file name
  private def isValidFile(file: WebkitFile): Boolean = isStdFile(file)

  // Returns a pair of lists containing the valid and invalid ICD files
  private def getIcdFiles(e: dom.Event): (Seq[WebkitFile], Seq[WebkitFile]) = {
    val files = e.target.files
    if (inputDirSupported) {
      val fileList = for (i <- 0 until files.length) yield files(i).asInstanceOf[WebkitFile]
      fileList
        .filterNot { f =>
          f.webkitRelativePath.contains(".git") ||
          f.webkitRelativePath.contains(".idea") ||
          f.webkitRelativePath.contains("/apis/") ||
          f.webkitRelativePath.contains("/icds/") ||
          f.name.endsWith(".md")
        }
        .partition(isValidFile)
    }
    else {
      val fileList = for (i <- 0 until files.length) yield files(i).asInstanceOf[WebkitFile]
      fileList.filterNot(_.name.endsWith(".md")).partition(isValidFile)
    }
  }

  private def statusItem = document.querySelector("#status")

  private def busyStatusItem = document.querySelector("#busyStatus")

  //noinspection ScalaUnusedSymbol
  // Called when user clicks on input item.
  // Reset the value (Otherwise you can't upload the same file twice,
  // since it won't fire the change event)
  private def fileSelectReset(e: dom.Event): Unit = {
    clearProblems()
    inputItem.value = ""
    setProgressBar(0)
    statusItem.classList.remove("text-bg-danger")
    statusItem.classList.remove("text-bg-success")
  }

  // Called when a file selection has been made
  private def fileSelectHandler(e: dom.Event): Unit = {
    clearProblems()
    statusItem.classList.remove("text-bg-danger")
    val (validFiles, invalidFiles) = getIcdFiles(e)
    if (validFiles.isEmpty) {
      val fileType = "directory of .conf files for the ICD"
      displayProblem(Problem("error", s"Expected a $fileType"))
    }
    else {
      uploadFiles(validFiles.toList)
    }

    // list ignored files:
    for (file <- invalidFiles) {
      val path = getFilePath(file)
      if (!path.endsWith(".iml") && !path.endsWith(".project"))
        displayProblem(Problem("warning", s"$path: Ignored"))
    }
  }

  private def setProgressBar(percent: Int): Unit = {
    val progressDiv = document.querySelector("#progress").asInstanceOf[HTMLDivElement]
    progressDiv.setAttribute("aria-valuenow", percent.toString)
    progressDiv.setAttribute("style", s"width:$percent%")
    progressDiv.innerHTML = s"$percent %"
  }

  // Starts uploading the selected files (or files in selected directory) to the server
  private def uploadFiles(files: List[WebkitFile]): Unit = {
    val formData = new FormData()
    for (file <- files if isValidFile(file)) {
      formData.append(getFilePath(file), file)
    }

    val xhr = new dom.XMLHttpRequest
    xhr.open("POST", ClientRoutes.uploadFiles, async = true)

    // Updates progress bar during upload
    def progressListener(e: dom.Event): Unit = {
      val pc = e.loaded / e.total * 100
      setProgressBar(pc)
    }

    //noinspection ScalaUnusedSymbol
    // Displays status after upload complete
    def onloadListener(e: dom.Event) = {
      busyStatusItem.classList.add("d-none")
      val statusClass = if (xhr.status == 200) "text-bg-success" else "text-bg-danger"
      if (!statusItem.classList.contains("text-bg-danger")) {
        val statusMsg = if (xhr.status == 200) "Success" else xhr.statusText
        statusItem.classList.remove("text-bg-primary")
        statusItem.classList.add(statusClass)
        statusItem.textContent = statusMsg
      }
      val problems = Json.fromJson[List[Problem]](Json.parse(xhr.responseText)).getOrElse(Nil)
      for (problem <- problems.reverse)
        displayProblem(problem)

      // Update the menus of subsystem names,in case anything changed
      subsystemNames.update()
    }

    xhr.upload.addEventListener("progress", progressListener _, useCapture = false)
    xhr.onload = onloadListener _

    //start upload
    statusItem.classList.add("text-bg-primary")
    statusItem.textContent = "Working..."
    busyStatusItem.classList.remove("d-none")
    xhr.send(formData)
  }

  // Produce the HTML to display for the upload screen
  override def markup(): Element = {
    import scalatags.JsDom.all.*

    val dirMsg = "Here you can select the top level directory containing the subsystem or component files to upload."
    val dirLabel =
      if (inputDirSupported) "Model File Directory to upload:" else "Error: Your browser does not support uploading a directory!"
    val acceptSuffix = ""

    div(
      cls := "container-fluid",
      p(dirMsg),
      form(
        id := "upload",
        action := "/upload",
        attr("role") := "form",
        attr("method") := "POST",
        attr("enctype") := "multipart/form-data"
      )(
        input(`type` := "hidden", name := "csrfToken", value := csrfToken, accept := acceptSuffix),
        div(cls := "card")(
          div(cls := "card-body")(
            label(strong(dirLabel)),
            div(inputItem),
            div(cls := "d-none")(
              button(`type` := "submit")("Upload Files")
            )
          )
        )
      ),
      p(),
      div(cls := "progress")(
        div(
          id := "progress",
          cls := "progress-bar progress-bar-striped",
          role := "progressbar",
          attr("aria-valuenow") := "0",
          attr("aria-valuemin") := "0",
          attr("aria-valuemax") := "100",
          style := "width: 0%",
          "0%"
        )
      ),
      p(),
      h4("Status")(
        span(style := "margin-left:15px;"),
        div(id := "busyStatus", cls := "spinner-border d-none", role := "status")(
          span(cls := "visually-hidden")
        ),
        span(style := "margin-left:15px;"),
        span(id := "status", cls := "badge", "Working...")
      ),
      messagesItem
    ).render
  }
}
