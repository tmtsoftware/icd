package csw.services.icd.html

import icd.web.shared.IcdModels

/**
 * Handles converting ICD API from GFM to HTML
 */
object IcdToHtml {

  def getCss: String = {
    val stream = getClass.getResourceAsStream("/icd.css")
    val lines = scala.io.Source.fromInputStream(stream).getLines()
    lines.mkString("\n")
  }

  private def getTitle(models: IcdModels): String =
    models.subsystemModel.map(_.title).getOrElse(models.componentModel.map(_.title).getOrElse(""))

  /**
   * Gets the HTML for the document
   *
   * @param models list of ICD models for the different parts of the ICD
   * @return a string in HTML format
   */
  def getAsHtml(models: List[IcdModels]): String = {
    import scalatags.Text.all._
    val title = getTitle(models.head)
    val markupInfo = models.map(IcdToHtml(_))
    html(
      head(
        scalatags.Text.tags2.title(title),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
      ),
      body(
        h2(a(name := "title"), cls := "page-header")("Interface Control Document", br, title),
        div(cls := "pagebreak"),
        h2("Table of Contents"),
        markupInfo.map(_.tocEntry),
        markupInfo.map(_.tags)
      )
    ).render
  }
}

/**
 * Converts an ICD model to HTML.
 */
case class IcdToHtml(models: IcdModels) extends HtmlMarkup {

  private val markupList: List[HtmlMarkup] = List(
    models.subsystemModel.map(SubsystemModelToHtml),
    models.componentModel.map(ComponentModelToHtml),
    models.publishModel.map(PublishModelToHtml),
    models.subscribeModel.map(SubscribeModelToHtml),
    models.commandModel.map(CommandModelToHtml)
  ).flatten

  override val tags = {
    import scalatags.Text.all._
    div(cls := "pagebreak") :: markupList.map(_.markup)
  }

  override val tocEntry = {
    import scalatags.Text.all._
    Some(ul(markupList.flatMap(_.tocEntry)))
  }
}

