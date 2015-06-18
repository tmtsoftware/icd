package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._

/**
 * A navbar item to handle uploading an ICD directory or zip file to the play server.
 * @param csrfToken passed from server, used for security
 * @param inputDirSupported true if browser can upload directories (chrome)
 */
case class FileUploadItem(csrfToken: String, inputDirSupported: Boolean, listener: () ⇒ Unit) extends Displayable {

  // Returns the HTML markup for the navbar item
  def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(onclick := listener)("Upload")).render
  }
}

