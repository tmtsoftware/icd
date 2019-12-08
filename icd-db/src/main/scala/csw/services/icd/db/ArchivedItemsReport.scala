package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.db.ArchivedItemsReport._
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels.{ComponentModel, EventModel}

object ArchivedItemsReport {

  case class ArchiveInfo(
      component: String,
      prefix: String,
      eventType: String,
      name: String,
      maybeMaxRate: Option[Double],
      sizeInBytes: Int,
      yearlyAccumulation: String,
      description: String
  )
}

case class ArchivedItemsReport(db: IcdDb, maybeSubsystem: Option[String]) {
  val query = new CachedIcdDbQuery(db.db, db.admin, maybeSubsystem.map(List(_)))

  // Returns true if the given subsystem should be included in the report
  private def subsystemFilter(subsystem: String): Boolean = {
    if (maybeSubsystem.isDefined) maybeSubsystem.contains(subsystem)
    else !subsystem.startsWith("TEST")
  }

  // Gets all the archived items
  private def getArchivedItems: List[ArchiveInfo] = {
    // Gets the archived items from the list
    def getItems(c: ComponentModel, eventType: String, list: List[EventModel]): List[ArchiveInfo] = {
      val comp = c.component.replace("-", "-\n") // save horizontal space
      list
        .filter(_.archive)
        .map(
          e =>
            ArchiveInfo(
              comp,
              c.prefix,
              eventType,
              e.name,
              e.maybeMaxRate,
              e.totalSizeInBytes,
              e.totalArchiveSpacePerYear,
              e.description
            )
        )
    }

    val result = for {
      component <- query.getComponents
      if subsystemFilter(component.subsystem)
      publishModel <- query.getPublishModel(component)
    } yield {
      getItems(component, "Events", publishModel.eventList) ++
      getItems(component, "ObserveEvents", publishModel.observeEventList)
    }
    result.flatten
  }

  // Generates the HTML for the report
  private def makeReport(): String = {
    import scalatags.Text.all._

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    val markup = html(
      head(
        scalatags.Text.tags2.title("Archived Items"),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
      ),
      body(
        h2("Archived Items"),
        div(
          table(
            thead(
              tr(
                th("Component"),
                th("Prefix"),
                th("Type"),
                th("Name"),
                th("Max", br, "Rate"),
                th("Size", br, "Bytes"),
                th("Yearly", br, "Accum."),
                th("Description")
              )
            ),
            tbody(
              for {
                item <- getArchivedItems
              } yield {
                val maxRate = item.maybeMaxRate.getOrElse(EventModel.defaultMaxRate)
                tr(
                  td(p(item.component)),
                  td(p(raw(item.prefix.replace(".", ".<br/>")))),
                  td(p(item.eventType)),
                  td(p(item.name)),
                  td(
                    p(if (item.maybeMaxRate.isEmpty) em(maxRate.toString) else span(maxRate.toString))
                  ),
                  td(p(item.sizeInBytes)),
                  td(p(if (item.maybeMaxRate.isEmpty) em(item.yearlyAccumulation) else span(item.yearlyAccumulation))),
                  td(raw(firstParagraph(item.description)))
                )
              }
            )
          )
        )
      )
    )
    markup.render
  }

  /**
   * Saves the report in HTML or PDF, depending on the file suffix
   */
  def saveToFile(file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit =
      IcdToPdf.saveAsPdf(file, html, showLogo = false)

    val html = makeReport()
    file.getName.split('.').drop(1).lastOption match {
      case Some("html") => saveAsHtml(html)
      case Some("pdf")  => saveAsPdf(html)
      case _            => println(s"Unsupported output format: Expected *.html or *.pdf")
    }
  }
}
