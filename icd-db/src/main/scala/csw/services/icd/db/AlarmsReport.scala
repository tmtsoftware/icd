package csw.services.icd.db

import java.io.{File, FileOutputStream}
import csw.services.icd.IcdToPdf
import csw.services.icd.db.AlarmsReport.*
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels.{ComponentModel, AlarmModel}
import icd.web.shared.{Headings, PdfOptions, SubsystemWithVersion}
import scalatags.Text

object AlarmsReport {

  private case class AlarmInfo(
      subsystem: String,
      component: String,
      prefix: String,
      alarmModel: AlarmModel
  )
}

/**
 * Generates an "Alarms" report for the given subsystem (or all subsystems)
 *
   * @param db              the icd database
 * @param maybePdfOptions used in creating PDF
 * @param maybeSv         if defined, restrict report to this subsystem, version, component (otherwise: all current subsystems)
 * @param headings        used for HTML headings (use for TOC)
 */
case class AlarmsReport(
    db: IcdDb,
    maybeSv: Option[SubsystemWithVersion],
    maybePdfOptions: Option[PdfOptions],
    headings: Headings
) {
  private val query          = new CachedIcdDbQuery(db, maybeSv.map(sv => List(sv.subsystem)), maybePdfOptions, Map.empty)
  private val versionManager = new CachedIcdVersionManager(query)

  // Returns true if the given subsystem should be included in the report
  private def subsystemFilter(subsystem: String): Boolean = {
    !subsystem.startsWith("TEST")
  }

  // Gets all the Alarms
  private def getAlarms: List[AlarmInfo] = {
    // Save horizontal space in the table by wrapping at certain chars
    def saveSpace(s: String): String = {
      s.replace("-", "-\n") // save horizontal space (old version)
        .replace("_", "_\n") // save horizontal space (new version, '-' not allowed)
        .replace(".", ".\n")
    }

    // Gets the Alarms from the list
    def getItems(c: ComponentModel, list: List[AlarmModel]): List[AlarmInfo] = {
      list.map(a => AlarmInfo(c.subsystem, saveSpace(c.component), saveSpace(c.prefix), a))
    }

    val result = if (maybeSv.isDefined) {
      // Use given subsystem version and component, if defined.
      // Note: For backward compatibility, look for alarms in publish-model as well as alarm-model files
      val sv = maybeSv.get
      for {
        models         <- versionManager.getResolvedModels(sv, maybePdfOptions, Map.empty)
        componentModel <- models.componentModel
        if sv.maybeComponent.isEmpty || sv.maybeComponent.get == componentModel.component
        publishModel <- models.publishModel
      } yield {
        val alarmsModel = models.alarmsModel.toList.flatMap(_.alarmList) ++ publishModel.alarmList
        getItems(componentModel, alarmsModel)
      }
    }
    else {
      for {
        componentModel <- query.getComponents(maybePdfOptions)
        if subsystemFilter(componentModel.subsystem)
        publishModel <- query.getPublishModel(componentModel, maybePdfOptions, Map.empty)
      } yield {
        val maybeAlarmsModel = query.getAlarmsModel(componentModel, maybePdfOptions)
        val alarmsModel      = maybeAlarmsModel.toList.flatMap(_.alarmList) ++ publishModel.alarmList
        getItems(componentModel, alarmsModel)
      }
    }
    result.flatten
  }

  // Generates the HTML for the report
  def makeReport(pdfOptions: PdfOptions): String = {
    import scalatags.Text.all.*

    val titleExt = maybeSv.map(sv => s" for $sv").getOrElse("")
    val titleStr = s"Alarms$titleExt"
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

    val alarms: List[AlarmInfo] = getAlarms
    div(
      headings.H2(titleStr),
      table(
        thead(
          tr(
            th("Component"),
            th("Prefix"),
            th("Name"),
            th("Severity Levels"),
            th("Auto Ack"),
            th("Latched"),
            th("Description")
          )
        ),
        tbody(
          for {
            item <- alarms
          } yield {
            tr(
              td(p(item.component)),
              td(p(raw(item.prefix.replace(".", ".<br/>")))),
              td(p(item.alarmModel.name)),
              td(p(raw(item.alarmModel.severityLevels.mkString(",<br/>")))),
              td(p(if (item.alarmModel.autoAck) "yes" else "no")),
              td(p(if (item.alarmModel.latched) "yes" else "no")),
              td(raw(firstParagraph(item.alarmModel.description)))
            )
          }
        )
      )
    )
  }

  // Generates the text/CSV formatted report
  private def makeCsvReport(file: File): Unit = {
    import com.github.tototoshi.csv.*

    implicit object MyFormat extends DefaultCSVFormat {
      override val lineTerminator = "\n"
    }

    val alarms: List[AlarmInfo] = getAlarms
    val writer                  = CSVWriter.open(file)
    writer.writeRow(List("Component", "Prefix", "Name", "Severity Levels", "Auto Ack", "Latched", "Description"))
    alarms.foreach { i =>
      writer.writeRow(
        List(
          i.component.filter(_ != '\n'),
          i.prefix.filter(_ != '\n'),
          i.alarmModel.name,
          i.alarmModel.severityLevels.mkString(":"),
          i.alarmModel.autoAck,
          i.alarmModel.latched,
          firstParagraphPlainText(i.alarmModel.description)
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
