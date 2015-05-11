package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLStyleElement

import scala.scalajs.js
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.TypedTag
import org.scalajs.dom.Element
import scalacss.Defaults._
import scalacss.ScalatagsCss._
import scalatags.Text._
import scalatags.Text.all._

object IcdWebClient extends JSApp {

  // Main entry point
  @JSExport
  def init(settings: js.Dynamic) = {
    val csrfToken = settings.csrfToken.toString
    val wsBaseUrl = settings.wsBaseUrl.toString
    val inputDirSupported = settings.inputDirSupported.toString == "true"

    dom.document.head.appendChild(Styles.render[TypedTag[HTMLStyleElement]].render)

    // Top navbar and items
    Navbar.init()
    Subsystem.init(wsBaseUrl)
    View.init()
    FileUpload.init(csrfToken, inputDirSupported)

    // Main layout
    Layout.init()
    LeftSidebar.init()
    Main.init()
    RightSidebar.init()

  }

  // Main entry point (not used, see init() above)
  @JSExport
  def main(): Unit = {
  }
}
