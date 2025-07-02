package csw.services.icd.db

import java.io.{File, FileOutputStream}
import csw.services.icd.IcdToPdf
import csw.services.icd.db.ArchivedItemsReport.*
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels.{ComponentModel, EventModel}
import icd.web.shared.{Headings, PdfOptions, SubsystemWithVersion}
import scalatags.Text

object ArchivedItemsReport {

  private case class ArchiveInfo(
      subsystem: String,
      component: String,
      prefix: String,
      eventType: String,
      eventModel: EventModel
  ) {
    val name: String                 = eventModel.name
    val category: String             = eventModel.getCategory
    val maybeMaxRate: Option[Double] = eventModel.maybeMaxRate
    val sizeInBytes: Int             = eventModel.totalSizeInBytes
    val hourlyAccumulation: String   = eventModel.totalArchiveSpacePerHour
    val yearlyAccumulation: String   = eventModel.totalArchiveSpacePerYear
    val description: String          = eventModel.description
  }
}

/**
 * Generates an "Archived Items" report for the given subsystem (or all subsystems)
 * @param db the icd database
 * @param maybePdfOptions used in creating PDF
 * @param maybeSv if defined, restrict report to this subsystem, version, component (otherwise: all current subsystems)
 * @param headings used for HTML headings (use for TOC)
 */
case class ArchivedItemsReport(db: IcdDb, maybeSv: Option[SubsystemWithVersion],
                               maybePdfOptions: Option[PdfOptions], headings: Headings) {
  private val query          = new CachedIcdDbQuery(db, maybeSv.map(sv => List(sv.subsystem)), maybePdfOptions, Map.empty)
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
        models         <- versionManager.getResolvedModels(sv, maybePdfOptions, Map.empty)
        componentModel <- models.componentModel
        if sv.maybeComponent.isEmpty || sv.maybeComponent.get == componentModel.component
        publishModel <- models.publishModel
      } yield {
        getItems(componentModel, "Events", publishModel.eventList) ++
        getItems(componentModel, "ObserveEvents", publishModel.observeEventList)
      }
    }
    else {
      for {
        componentModel <- query.getComponents(maybePdfOptions)
        if subsystemFilter(componentModel.subsystem)
        publishModel <- query.getPublishModel(componentModel, maybePdfOptions, Map.empty)
      } yield {
        getItems(componentModel, "Events", publishModel.eventList) ++
        getItems(componentModel, "ObserveEvents", publishModel.observeEventList)
      }
    }
    result.flatten
  }

  private def totalsTable(archivedItems: List[ArchiveInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    val subsystems = archivedItems.map(_.subsystem).distinct
    table(
      style := "width:100%;",
      thead(
        tr(
          th("Subsystem"),
          th("Hourly", br, "Accum."),
          th("Yearly", br, "Accum.")
        )
      ),
      tbody(
        for {
          subsystem <- subsystems
        } yield {
          tr(
            td(p(subsystem)),
            td(p(EventModel.getTotalArchiveSpaceHourly(archivedItems.filter(_.subsystem == subsystem).map(_.eventModel)))),
            td(p(EventModel.getTotalArchiveSpace(archivedItems.filter(_.subsystem == subsystem).map(_.eventModel))))
          )
        }
      )
    )
  }

  // Generates the HTML for the report
  def makeReport(pdfOptions: PdfOptions): String = {
    import scalatags.Text.all.*

    val titleExt = maybeSv.map(sv => s" for $sv").getOrElse("")
    val titleStr    = s"Archived Items Report$titleExt"
    val markup =
      html(
        head(
          scalatags.Text.tags2.title(titleStr),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
        ),
        body(
          makeReportMarkup(titleStr)
        )
      )
    markup.render
  }

  // Generates the HTML markup for the report
  def makeReportMarkup(titleStr: String): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    val archivedItems: List[ArchiveInfo] = getArchivedItems
    val averageEventSize                 = if (archivedItems.isEmpty) 0 else archivedItems.map(_.sizeInBytes).sum / archivedItems.size
    div(
      headings.H2(titleStr),
      table(
        thead(
          tr(
            th("Component"),
            th("Prefix"),
            th("Type"),
            th("Name"),
            th("Category"),
            th("Max", br, "Rate Hz"),
            th("Size", br, "Bytes"),
            th("Hourly", br, "Accum."),
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
              td(p(item.category)),
              td(
                p(if (defaultMaxRateUsed) em(maxRate.toString + "*") else span(maxRate.toString))
              ),
              td(p(item.sizeInBytes)),
              td(p(if (defaultMaxRateUsed) em(item.hourlyAccumulation + "*") else span(item.hourlyAccumulation))),
              td(p(if (defaultMaxRateUsed) em(item.yearlyAccumulation + "*") else span(item.yearlyAccumulation))),
              td(raw(firstParagraph(item.description)))
            )
          }
        )
      ),
      span("* Assumes 1 Hz if maxRate is not specified or is 0."),
      headings.H3(s"Totals for Subsystem${if (maybeSv.isEmpty) "s" else ""}"),
      totalsTable(archivedItems),
      if (maybeSv.isEmpty)
        strong(
          p(
            "Total archive space required for one year: "
              + EventModel.getTotalArchiveSpace(archivedItems.map(_.eventModel))
              + s". Average event size: $averageEventSize bytes"
          )
        )
      else span()
    )
  }

  // Generates the text/CSV formatted report
  private def makeCsvReport(file: File): Unit = {
    import com.github.tototoshi.csv.*

    implicit object MyFormat extends DefaultCSVFormat {
      override val lineTerminator = "\n"
    }

    val archivedItems: List[ArchiveInfo] = getArchivedItems
    val writer                           = CSVWriter.open(file)
    writer.writeRow(List("Component", "Prefix", "Type", "Name", "Category", "Max Rate Hz", "Size Bytes", "Yearly Accum.", "Description"))
    archivedItems.foreach { i =>
      val (maxRate, _) = EventModel.getMaxRate(i.maybeMaxRate)
      writer.writeRow(
        List(
          i.component.filter(_ != '\n'),
          i.prefix.filter(_ != '\n'),
          i.eventType,
          i.name,
          i.category,
          maxRate,
          i.sizeInBytes,
          i.yearlyAccumulation,
          firstParagraphPlainText(i.description)
        )
      )
    }
    writer.close()
    println(s"Wrote $file")
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

    file.getName.split('.').drop(1).lastOption match {
      case Some("html") => saveAsHtml(makeReport(pdfOptions))
      case Some("pdf")  => saveAsPdf(makeReport(pdfOptions))
      case _            => makeCsvReport(file)
    }
  }
}
