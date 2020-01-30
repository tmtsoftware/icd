package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.db.ArchivedItemsReport._
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels.{ComponentModel, EventModel}
import scalatags.Text

object ArchivedItemsReport {

  case class ArchiveInfo(
      subsystem: String,
      component: String,
      prefix: String,
      eventType: String,
      eventModel: EventModel
  ) {
    val name: String                 = eventModel.name
    val maybeMaxRate: Option[Double] = eventModel.maybeMaxRate
    val sizeInBytes: Int             = eventModel.totalSizeInBytes
    val yearlyAccumulation: String   = eventModel.totalArchiveSpacePerYear
    val description: String          = eventModel.description
  }
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
        .map(e => ArchiveInfo(c.subsystem, comp, c.prefix, eventType, e))
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

  private def totalsTable(archivedItems: List[ArchiveInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    val subsystems = archivedItems.map(_.subsystem).distinct
    table(
      thead(
        tr(
          th("Subsystem"),
          th("Yearly", br, "Accum.")
        )
      ),
      tbody(
        for {
          subsystem <- subsystems
        } yield {
          tr(
            td(p(subsystem)),
            td(p(EventModel.getTotalArchiveSpace(archivedItems.filter(_.subsystem == subsystem).map(_.eventModel))))
          )
        }
      )
    )
  }

  // Generates the HTML for the report
  def makeReport(): String = {
    import scalatags.Text.all._

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    val archivedItems: List[ArchiveInfo] = getArchivedItems
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
                th("Max", br, "Rate Hz"),
                th("Size", br, "Bytes"),
                th("Yearly", br, "Accum."),
                th("Description")
              )
            ),
            tbody(
              for {
                item <- archivedItems
              } yield {
                val (maxRate, defaultMaxRateUsed) = EventModel.getMaxRate(item.maybeMaxRate)
                tr(
                  td(p(item.component)),
                  td(p(raw(item.prefix.replace(".", ".<br/>")))),
                  td(p(item.eventType)),
                  td(p(item.name)),
                  td(
                    p(if (defaultMaxRateUsed) em(maxRate.toString + "*") else span(maxRate.toString))
                  ),
                  td(p(item.sizeInBytes)),
                  td(p(if (defaultMaxRateUsed) em(item.yearlyAccumulation + "*") else span(item.yearlyAccumulation))),
                  td(raw(firstParagraph(item.description)))
                )
              }
            )
          ),
          span("* Assumes 1 Hz if maxRate is not specified or is 0."),
          h3("Totals for Subsystems"),
          totalsTable(archivedItems),
          if (maybeSubsystem.isEmpty)
            strong(p(s"Total archive space required for one year: ${EventModel.getTotalArchiveSpace(archivedItems.map(_.eventModel))}"))
          else span()
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
