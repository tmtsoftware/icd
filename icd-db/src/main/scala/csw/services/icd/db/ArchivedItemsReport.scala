package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.db.ArchivedItemsReport._
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels.{ComponentModel, EventModel}
import icd.web.shared.{PdfOptions, SubsystemWithVersion}
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

/**
 * Generates an "Archived Items" report for the given subsystem (or all subsystems)
 * @param db the icd database
 * @param maybeSv if defined, restrict report to this subsystem, version, component (otherwise: all current subsystems)
 */
case class ArchivedItemsReport(db: IcdDb, maybeSv: Option[SubsystemWithVersion], maybePdfOptions: Option[PdfOptions]) {
  private val query          = new CachedIcdDbQuery(db.db, db.admin, maybeSv.map(sv => List(sv.subsystem)), maybePdfOptions)
  private val versionManager = new CachedIcdVersionManager(query)

  // Returns true if the given subsystem should be included in the report
  private def subsystemFilter(subsystem: String): Boolean = {
    !subsystem.startsWith("TEST")
  }

  // Gets all the archived items
  private def getArchivedItems: List[ArchiveInfo] = {
    // Save horizontal space in the table by wrapping at certain chars
    def saveSpace(s: String): String = {
      s.replace("-", "-\n") // save horizontal space (old version)
        .replace("_", "_\n") // save horizontal space (new version, '-' not allowed)
        .replace(".", ".\n")
    }

    // Gets the archived items from the list
    def getItems(c: ComponentModel, eventType: String, list: List[EventModel]): List[ArchiveInfo] = {
      list
        .filter(_.archive)
        .map(e => ArchiveInfo(c.subsystem, saveSpace(c.component), saveSpace(c.prefix), eventType, e))
    }

    val result = if (maybeSv.isDefined) {
      // Use given subsystem version and component, if defined
      val sv = maybeSv.get
      for {
        models         <- versionManager.getModels(sv, subsystemOnly = false, maybePdfOptions)
        componentModel <- models.componentModel
        if sv.maybeComponent.isEmpty || sv.maybeComponent.get == componentModel.component
        publishModel <- models.publishModel
      } yield {
        getItems(componentModel, "Events", publishModel.eventList) ++
        getItems(componentModel, "ObserveEvents", publishModel.observeEventList)
      }
    } else {
      for {
        componentModel <- query.getComponents(maybePdfOptions)
        if subsystemFilter(componentModel.subsystem)
        publishModel <- query.getPublishModel(componentModel, maybePdfOptions)
      } yield {
        getItems(componentModel, "Events", publishModel.eventList) ++
        getItems(componentModel, "ObserveEvents", publishModel.observeEventList)
      }
    }
    result.flatten
  }

  private def totalsTable(archivedItems: List[ArchiveInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    val subsystems = archivedItems.map(_.subsystem).distinct
    table(
      style := "width:100%;",
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
  def makeReport(pdfOptions: PdfOptions): String = {
    import scalatags.Text.all._

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    val titleExt                         = maybeSv.map(sv => s" for $sv").getOrElse("")
    val title                            = s"Archived Items Report$titleExt"
    val archivedItems: List[ArchiveInfo] = getArchivedItems
    val markup = html(
      head(
        scalatags.Text.tags2.title(title),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
      ),
      body(
        h2(title),
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
          if (maybeSv.isEmpty)
            strong(
              p(s"Total archive space required for one year: ${EventModel.getTotalArchiveSpace(archivedItems.map(_.eventModel))}")
            )
          else span()
        )
      )
    )
    markup.render
  }

  /**
   * Saves the report in HTML or PDF, depending on the file suffix
   */
  def saveToFile(file: File, pdfOptions: PdfOptions): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit =
      IcdToPdf.saveAsPdf(file, html, showLogo = false, pdfOptions)

    val html = makeReport(pdfOptions)
    file.getName.split('.').drop(1).lastOption match {
      case Some("html") => saveAsHtml(html)
      case Some("pdf")  => saveAsPdf(html)
      case _            => println(s"Unsupported output format: Expected *.html or *.pdf")
    }
  }
}
