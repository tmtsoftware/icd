package icd.web.client

import org.scalajs.dom.Element

import scalatags.JsDom.all._

/**
 * Manages the main content section, which displays information on the
 * selected subsystem and components
 */
case class MainContent() extends Displayable {
  private val contentTitle = h3(cls := "page-header").render

  private val contentDiv = {
    import scalacss.ScalatagsCss._
    div(Styles.contentDiv, id := "content").render
  }

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, content: String): Unit = {
    contentTitle.textContent = title
    this.contentDiv.innerHTML = content
  }

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, displayable: Displayable): Unit = {
    contentTitle.textContent = title
    contentDiv.innerHTML = ""
    contentDiv.appendChild(displayable.markup())
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

  // Replace the old element with the new one
  def replaceElement(oldElement: Element, newElement: Element) = contentDiv.replaceChild(newElement, oldElement)

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
