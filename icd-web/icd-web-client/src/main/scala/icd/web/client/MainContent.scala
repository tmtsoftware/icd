package icd.web.client

import org.scalajs.dom.{ Element, Node }

import scalatags.JsDom.all._

/**
 * Manages the main content section
 */
case class MainContent() extends Displayable {
  private val contentTitle = h3(cls := "page-header").render
  private val contentDiv = div(id := "content").render

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, content: String): Unit = {
    contentTitle.textContent = title
    this.contentDiv.innerHTML = content
  }

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, node: Node): Unit = {
    contentTitle.textContent = title
    contentDiv.innerHTML = ""
    contentDiv.appendChild(node)
  }

  // Sets the title of the main section of the page
  def setTitle(title: String): Unit = {
    contentTitle.textContent = title
  }

  // Gets the title of the page
  def getTitle: String = contentTitle.textContent

  // Clear out the title and content
  def clearContent(): Unit = setContent("", "")

  // Appends the element to the content
  def appendElement(element: Element): Unit = contentDiv.appendChild(element)

  // Removes the element from the content
  def removeElement(element: Element): Unit = contentDiv.removeChild(element)

  // Displays an error message for the exception
  def displayInternalError(ex: Throwable): Unit = {
    // Display an error message
    println(s"Internal error: $ex")
    setContent("Internal Error", errorDiv("Internal error. The database may be down."))
  }

  // Scroll the title to the top
  def scrollToTop(): Unit = {
    contentTitle.scrollTop = 0
  }

  def markup() = {
    import scalacss.ScalatagsCss._
    div(Styles.mainContent)(
      div(Styles.main)(contentTitle, contentDiv),
      div(p(" ")) // XXX FIXME: space at bottom, use css
      ).render
  }
}
