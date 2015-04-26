package icd.web.client

import org.scalajs.dom
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.jquery.{jQuery => $, _}

@JSExport
object FileUpload {

  //  // Hack to access unsupported method: File.webkitRelativePath
  //  // (This can't be defined inside the def below).
  //  // Note that this only works on webkit browsers: Safari, Chrome.
  //  trait WebkitFile extends org.scalajs.dom.File {
  //    def webkitRelativePath: js.Any = js.native
  //  }
  //
  //  // called when a file or directory has been selected
  //    def fileSelected(e: dom.Event) {
  //      val files: FileList = e.target.asInstanceOf[HTMLInputElement].files
  //      val count = files.length
  //      for(i <- 0 until count) {
  //        val file = files(i).asInstanceOf[WebkitFile]
  //        val path = file.webkitRelativePath
  //        println(s"You selected $path")
  ////        uploadFile(path)
  //      }
  //    }
  //
  //    $id("uploadFiles").addEventListener("change", fileSelected _, false)
  //
  //  }

  val stdList = List("icd-model.conf", "component-model.conf", "publish-model.conf",
    "subscribe-model.conf", "command-model.conf")
  // Check if file is one of the standard ICD files
  def isStdFile(file: dom.File): Boolean = {
    stdList.exists(file.name.endsWith)
  }

  @JSExport
  // Initialize the upload callback
  def init(csrfToken: String): Unit = {

    // Called when the Upload item is selected
    def uploadSelected(e: dom.Event) = {
      val content = $id("content")
      val children = content.childNodes
      for (i <- 0 until children.length) {
        content.removeChild(children(i))
      }
      $id("contentTitle").textContent = "Upload ICD Directory"
      $id("content").appendChild(markup(csrfToken).render)
      ready()
    }

    $id("uploadButton").addEventListener("click", uploadSelected _, useCapture = false)
  }


  def markup(csrfToken: String) = {
    import scalatags.JsDom.all._

    div(
      p("Modified from ",
        a(href := "http://www.sitepoint.com/html5-file-drag-and-drop/", "How to Use HTML5 File Drag and Drop"),
        " by ",
        a(href := "http://twitter.com/craigbuckler", "Craig Buckler")
      ),
      p( """This is a demonstration of the HTML5 file drag & drop API with asynchronous Ajax file uploads,
      graphical progress bars and progressive enhancement."""),
      form(id := "upload", action := s"/upload", "role".attr := "form",
        "method".attr := "POST", "enctype".attr := "multipart/form-data")(
          input(`type` := "hidden", name := "csrfToken", value := csrfToken),
          div(`class` := "panel panel-info")(
            div(`class` := "panel-heading")(h3(`class` := "panel-title", "HTML File Upload")),
            div(`class` := "panel-body")(
              div(
                label(`for` := "", "Files to upload:"),
                input(`type` := "file", id := "fileSelect", name := "fileSelect", "multiple".attr := "multiple")
              ),
              div(id := "submitButton", `class` := "hide")(
                button(`type` := "submit")("Upload Files")
              ), br,
              div(id := "fileDrag", `class` := "panel panel-info",
                style := "height: 70px; border-style: dashed; border-width: 2px;text-align: center;")(
                  p(style := "margin-top:20px;")("Or drop file here...")
                )
            )
          )
        ),
      div(`class` := "progress")(
        div(id := "progress", `class` := "progress-bar progress-bar-success progress-bar-striped", "role".attr := "progressbar", "aria-valuenow".attr := "0",
          "aria-valuemin".attr := "0", "aria-valuemax".attr := "100", style := "width: 100%", "0%")
      ),
      h4("Status Messages")(span(style := "margin-left:15px;"), span(id := "status", `class` := "label hide", "Done")),
      div(id := "messages", `class` := "alert alert-info")
    )
  }

  trait EventTargetExt extends dom.EventTarget {
    var files: dom.FileList = js.native

  }

  trait EventExt extends dom.Event {
    var dataTransfer: dom.DataTransfer = js.native

    var loaded: Int = js.native
    var total: Int = js.native
  }

  def ready(): Unit = {

    implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]
    implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

    val maxFileSize = 3000000l

    def output(msg: String) = {
      val m = $id("messages")
      m.innerHTML = msg + m.innerHTML
    }

    def fileDragHover(e: dom.Event) = {
      e.stopPropagation()
      e.preventDefault()
      if (e.`type` == "dragover") {
        $("#fileDrag").removeClass("panel-info").addClass("panel-primary")
      } else {
        $("#fileDrag").removeClass("panel-primary").addClass("panel-info")
      }
    }

    def fileSelectHandler(e: dom.Event) = {
      fileDragHover(e)
      val files = if (e.target.files.toString != "undefined") {
        e.target.files
      } else {
        e.asInstanceOf[dom.DragEvent].dataTransfer.files
      }
      (0 until files.length).foreach { i =>
        try {
          val file = files(i)
          if (isStdFile(file)) {
            parseFile(file)
            uploadFile(file)
          }
        } catch {
          case e: Throwable => println(e)
        }
      }
    }

    def parseFile(file: dom.File) = {
      import scalatags.JsDom.all._
      val reader = new FileReader()
      reader.onload = (e: dom.UIEvent) => {
        output(div(p(strong(file.name + ":")), pre(reader.result.toString)).toString())
      }
      reader.readAsText(file)
    }

    def uploadFile(file: dom.File) = {
      val xhr = new dom.XMLHttpRequest
      if (xhr.upload != null && file.size <= maxFileSize) {

        xhr.upload.addEventListener("progress", (e: dom.Event) => {
          val pc = e.loaded / e.total * 100
          $("#progress").css("with", pc + "%").attr("aria-valuenow", pc.toString)
            .html(s"${file.name} ($pc %)")
        }, useCapture = false)

        xhr.onreadystatechange = (e: dom.Event) => {
          if (xhr.readyState == dom.XMLHttpRequest.UNSENT) {
            $("#status").addClass("hide")
          } else if (xhr.readyState == dom.XMLHttpRequest.DONE) {
            val statusClass = if (xhr.status == 200) "label-success" else "label-danger"
            val statusMsg = if (xhr.status == 200) "Success" else "Error " + xhr.statusText
            $("#status").removeClass("hide").addClass(statusClass).text(statusMsg)
          }
        }
        //start upload
        xhr.open("POST", $id("upload").asInstanceOf[dom.raw.HTMLFormElement].action, async = true)
        xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest")
        xhr.setRequestHeader("X-FILENAME", file.name)
        xhr.send(file)
      }
    }

    $("#fileDrag").on("dragenter dragstart dragend dragleave dragover drag drop", (e: dom.Event) => {
      e.preventDefault()
    })

    $id("fileSelect").addEventListener("change", fileSelectHandler _, useCapture = false)
    val xhr = new dom.XMLHttpRequest
    if (xhr.upload != null) {
      val fileDrag = $id("fileDrag")
      fileDrag.addEventListener("dragover", fileDragHover _, useCapture = false)
      fileDrag.addEventListener("dragleave", fileDragHover _, useCapture = false)
      fileDrag.addEventListener("drop", fileSelectHandler _, useCapture = false)
    }
  }
}

class FileReader() extends dom.EventTarget {

  import dom._

  /**
   * A DOMError representing the error that occurred while reading the file.
   *
   * MDN
   */
  def error: DOMError = js.native

  /**
   * A number indicating the state of the FileReader. This will be one of the State constants.
   * EMPTY   : 0 : No data has been loaded yet.
   * LOADING : 1 : Data is currently being loaded.
   * DONE    : 2 : The entire read request has been completed.
   *
   * MDN
   */
  def readyState: Short = js.native

  /**
   * The file's contents. This property is only valid after the read operation is
   * complete, and the format of the data depends on which of the methods was used to
   * initiate the read operation.
   *
   * MDN
   */
  def result: js.Any = js.native

  /**
   * A handler for the abort event. This event is triggered each time the reading
   * operation is aborted.
   *
   * MDN
   */
  var onabort: js.Function1[Event, _] = js.native

  /**
   * A handler for the error event. This event is triggered each time the reading
   * operation encounter an error.
   *
   * MDN
   */
  var onerror: js.Function1[Event, _] = js.native

  /**
   * A handler for the load event. This event is triggered each time the reading
   * operation is successfully completed.
   *
   * MDN
   */
  var onload: js.Function1[UIEvent, _] = js.native

  /**
   * A handler for the loadstart event. This event is triggered each time the reading
   * is starting.
   *
   * MDN
   */
  var onloadstart: js.Function1[ProgressEvent, _] = js.native

  /**
   * A handler for the loadend event. This event is triggered each time the reading
   * operation is completed (either in success or failure).
   *
   * MDN
   */
  var onloadend: js.Function1[ProgressEvent, _] = js.native

  /**
   * A handler for the progress event. This event is triggered while reading
   * a Blob content.
   *
   * MDN
   */
  var onprogress: js.Function1[ProgressEvent, _] = js.native

  /**
   * Aborts the read operation. Upon return, the readyState will be DONE.
   *
   * MDN
   */
  def abort(): Unit = js.native

  /**
   * The readAsArrayBuffer method is used to starts reading the contents of the
   * specified Blob or File. When the read operation is finished, the readyState
   * becomes DONE, and the loadend is triggered. At that time, the result attribute
   * contains an ArrayBuffer representing the file's data.
   *
   * MDN
   */
  def readAsArrayBuffer(blob: Blob): Unit = js.native

  /**
   * The readAsDataURL method is used to starts reading the contents of the specified
   * Blob or File. When the read operation is finished, the readyState becomes DONE, and
   * the loadend is triggered. At that time, the result attribute contains a data: URL
   * representing the file's data as base64 encoded string.
   *
   * MDN
   */
  def readAsDataURL(blob: Blob): Unit = js.native

  /**
   * The readAsText method is used to read the contents of the specified Blob or File.
   * When the read operation is complete, the readyState is changed to DONE, the loadend
   * is triggered, and the result attribute contains the contents of the file as a text string.
   *
   * MDN
   */
  def readAsText(blob: Blob, encoding: String = "UTF-8"): Unit = js.native

}

