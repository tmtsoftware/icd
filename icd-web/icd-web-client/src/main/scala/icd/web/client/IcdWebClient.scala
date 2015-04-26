package icd.web.client

import org.scalajs.dom.{FileList, Document}
import upickle._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{HTMLInputElement, HTMLSelectElement}

import scala.concurrent.Future
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global


object IcdWebClient extends js.JSApp {

  def $id(s: String) = dom.document.getElementById(s)

  // Displays the HTML for the given ICD name
  def displayIcd(name: String): Unit = {
    getIcdHtml(name).map { doc =>
      $id("content").innerHTML = doc
    }
  }

  // Gets the list of top level ICDs from the server
  def getIcdNames: Future[List[String]] = {
    Ajax.get(Routes.icdNames).map { r =>
      read[List[String]](r.responseText)
    }
  }

  // Gets the HTML for the named ICD
  def getIcdHtml(name: String): Future[String] = {
    Ajax.get(Routes.icdHtml(name)).map { r =>
      r.responseText
    }
  }


  // Makes the Subsystem combobox
  def makeSubsystemDropDown(items: List[String]) = {
    import scalatags.JsDom.all._

    val idStr = "subsystem"
    val titleStr = "Subsystem"
    val msg = "Select a subsystem"

    // called when an item is selected
    def subsystemSelected = (e: dom.Event) => {
      val sel = e.target.asInstanceOf[HTMLSelectElement]
      println(s"You selected ${sel.value}")
      // remove empty option
      if (sel.options.length > 1 && sel.options(0).value == msg)
        sel.remove(0)
      displayIcd(sel.value)
    }

    val list = msg :: items
    div(cls := "btn-group")(
      label(`for` := idStr)(titleStr),
      select(id := idStr, onchange := subsystemSelected)(
        list.map(s => option(value := s)(s)): _*
      )
    )
  }

  // Main entry point
  def main(): Unit = {
    getIcdNames.map(init)
    initUploadHandler()
  }

  // Hack to access unsupported method: File.webkitRelativePath
  // (This can't be defined inside the def below).
  // Note that this only works on webkit browsers: Safari, Chrome.
  trait WebkitFile extends org.scalajs.dom.File {
    def webkitRelativePath: js.Any = js.native
  }

  // Add some misisng fields to Event
  trait EventExt extends dom.Event {
    var dataTransfer: dom.DataTransfer = ???

    var loaded: Int = ???
    var total: Int = ???
  }

  // Setup upload handler
  def initUploadHandler(): Unit = {


//    def uploadFile(file: dom.File) = {
//      import org.scalajs.jquery.{jQuery=>$,_}
//      val xhr = new dom.XMLHttpRequest
//      if(xhr.upload != null && file.size <= maxFileSize){
//
//        xhr.upload.addEventListener("progress", (e: dom.Event) => {
//          val pc = (e.loaded / e.total * 100)
//          $("#progress").css("with", pc+"%").attr("aria-valuenow", pc.toString)
//            .html(s"${file.name} ($pc %)")
//        }, false)
//
//        xhr.onreadystatechange = (e: dom.Event) => {
//          if(xhr.readyState == dom.XMLHttpRequest.UNSENT){
//            $("#status").addClass("hide")
//          }else if(xhr.readyState == dom.XMLHttpRequest.DONE){
//            val statusClass = if(xhr.status == 200) "label-success" else "label-danger"
//            val statusMsg = if(xhr.status == 200) "Success" else "Error "+xhr.statusText
//            $("#status").removeClass("hide").addClass(statusClass).text(statusMsg)
//          }
//        }
//        //start upload
//        xhr.open("POST", $id("upload").asInstanceOf[dom.raw.HTMLFormElement].action, true)
//        xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest")
//        xhr.setRequestHeader("X-FILENAME", file.name)
//        xhr.send(file)
//      }
//    }
//
//    $("#fileDrag").on("dragenter dragstart dragend dragleave dragover drag drop", (e: dom.Event) => {
//      e.preventDefault()
//    });
//
//    $id("fileSelect").addEventListener("change", fileSelectHandler _, false)
//    //    $id("fileSelect").ondragend
//    val xhr = new dom.XMLHttpRequest
//    if(xhr.upload != null){
//      val fileDrag = $id("fileDrag")
//      fileDrag.addEventListener("dragover", fileDragHover _, false)
//      fileDrag.addEventListener("dragleave", fileDragHover _, false)
//      fileDrag.addEventListener("drop", fileSelectHandler _, false)
//    }
//  }




  // called when a file or directory has been selected
    def fileSelected(e: dom.Event) {
      val files: FileList = e.target.asInstanceOf[HTMLInputElement].files
      val count = files.length
      for(i <- 0 until count) {
        val file = files(i).asInstanceOf[WebkitFile]
        val path = file.webkitRelativePath
        println(s"You selected $path")
//        uploadFile(path)
      }
    }

    $id("uploadFiles").addEventListener("change", fileSelected _, false)

  }

  // Initialize the main layout
  def init(icdNames: List[String]): Unit = {
    val subsystemDropDown = makeSubsystemDropDown(icdNames)
    $id("navbarItem1").appendChild(subsystemDropDown.render)
  }
}
