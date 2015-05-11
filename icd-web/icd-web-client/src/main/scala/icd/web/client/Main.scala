package icd.web.client

import scalatags.JsDom.all._

/**
 * Manages the main content section
 */
object Main {
  val contentTitle = h3(cls := "page-header").render
  val content = div().render

  // Sets the title and HTML content of the main section of the page
  def setContent(title: String, content: String): Unit = {
    contentTitle.textContent = title
    this.content.innerHTML = content
  }

  // Sets the title and HTML content of the main section of the page
  def setContentTitle(title: String): Unit = {
    contentTitle.textContent = title
  }

  def clearContent(): Unit = setContent("", "")

  def displayInternalError(ex: Throwable): Unit = {
    // Display an error message
    println(s"Internal error: $ex")
    setContent("Internal Error", errorDiv("Internal error. The database may be down."))
  }

  private def markup() = {
    import scalacss.ScalatagsCss._
    //Styles.render[TypedTag[HTMLStyleElement]], Styles.mainWrapper,

    div(Styles.mainWrapper)(
      div(Styles.main)(contentTitle, content)
    )
  }

  def init(): Unit = {
    Layout.addItem(markup().render)
  }
}
