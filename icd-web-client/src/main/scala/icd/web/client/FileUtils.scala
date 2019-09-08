package icd.web.client

import org.scalajs.dom

import scala.scalajs.js

/**
 * Defines some methods and javascript APIS used by FileUpload
 */
object FileUtils {

  import scala.language.implicitConversions

  // Check if file is one of the standard ICD files
  def basename(file: dom.File): String =
    if (file.name.contains('/'))
      file.name.substring(file.name.lastIndexOf('/') + 1)
    else file.name

  // Extend js objects to add missing fields
  // (These can't be defined inside a def).
  @js.native
  trait EventTargetExt extends dom.EventTarget {
    var files: dom.FileList = js.native
  }

  implicit def monkeyizeEventTarget(e: dom.EventTarget): EventTargetExt = e.asInstanceOf[EventTargetExt]

  @js.native
  trait EventExt extends dom.Event {
    var dataTransfer: dom.DataTransfer = js.native
    var loaded: Int                    = js.native
    var total: Int                     = js.native
  }

  implicit def monkeyizeEvent(e: dom.Event): EventExt = e.asInstanceOf[EventExt]

  // Add unsupported method: File.webkitRelativePath
  // Note that this only works on webkit browsers: Safari, Chrome.
  @js.native
  trait WebkitFile extends dom.File {
    def webkitRelativePath: String = js.native
  }

}
