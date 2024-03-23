package csw.services.icd.db

import java.io.{ByteArrayOutputStream, File, FileOutputStream}
import csw.services.icd.{IcdToPdf, PdfCache}
import csw.services.icd.html.{IcdToHtml, NumberedHeadings}
import icd.web.shared.TitleInfo.unpublished
import icd.web.shared.*
import IcdToHtml.*
import csw.services.icd.fits.{IcdFits, IcdFitsDefs}
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import icd.web.shared.IcdModels.IcdModel

/**
 * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
 *
 * @param db                  used to query the database
 * @param searchAllSubsystems Include all subsystems in searches for publishers, subscribers, etc.
 *                            while generating API or ICD doc
 *                            (Default: Search only one subsystem for API, two for ICD)
 * @param clientApi           Include subscribed events and sent commands in the API dic
 *                            (Default: only include published events and received commands)
 */
//noinspection DuplicatedCode
case class IcdDbPrinter(
    db: IcdDb,
    searchAllSubsystems: Boolean,
    clientApi: Boolean,
    maybeCache: Option[PdfCache],
    maybePdfOptions: Option[PdfOptions]
) {

  /**
   * Gets information about a named subsystem (or component, if sv.maybeComponent is defined)
   */
  private def getSubsystemInfo(sv: SubsystemWithVersion): Option[SubsystemInfo] = {
    if (sv.maybeComponent.isDefined) {
      db.versionManager
        .getComponentModel(sv, maybePdfOptions)
        .map(m => SubsystemInfo(sv, m.title, m.description))

    }
    else {
      db.versionManager
        .getSubsystemModel(sv, maybePdfOptions)
        .map(m => SubsystemInfo(sv, m.title, m.description))
    }
  }

  /**
   * Gets information about the ICD between the two subsystems
   */
  private def getIcdModelList(sv: SubsystemWithVersion, tv: SubsystemWithVersion): List[IcdModel] = {
    db.versionManager.getIcdModels(sv, tv, maybePdfOptions)
  }

  /**
   * Gets information about the given components
   *
   * @param versionManager used to access the db
   * @param sv            the subsystem
   * @param maybeTargetSv optional target subsystem
   * @return future list of objects describing the components
   */
  private def getComponentInfo(
      versionManager: IcdVersionManager,
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      fitsKeyMap: FitsKeyMap
  ): List[ComponentInfo] = {
    maybeTargetSv match {
      case Some(targetSv) =>
        IcdComponentInfo.getComponentInfoList(versionManager, sv, targetSv, maybePdfOptions, fitsKeyMap)
      case None =>
        new ComponentInfoHelper(versionManager, searchAllSubsystems, clientApi)
          .getComponentInfoList(sv, maybePdfOptions, fitsKeyMap)
    }
  }

  /**
   * Returns an HTML document describing the given components in the given subsystem.
   *
   * @param sv the selected subsystem and version
   * @param pdfOptions options for PDF generation
   */
  private def getApiAsHtml(sv: SubsystemWithVersion, pdfOptions: PdfOptions): Option[String] = {
    val fitsDictionary  = IcdFits(db).getFitsDictionary(Some(sv.subsystem), sv.maybeComponent, None, Some(pdfOptions))
    val fitsKeyMap      = IcdFitsDefs.getFitsKeyMap(fitsDictionary.fitsKeys)
    val maybeSubsystems = if (searchAllSubsystems) None else Some(List(sv.subsystem))
    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val query          = new CachedIcdDbQuery(db.db, db.admin, maybeSubsystems, Some(pdfOptions), fitsKeyMap)
    val versionManager = new CachedIcdVersionManager(query)

    val markup = for {
      subsystemInfo <- getSubsystemInfo(sv)
    } yield {
      val infoList = getComponentInfo(versionManager, sv, None, fitsKeyMap)
      IcdToHtml.getApiAsHtml(subsystemInfo, infoList, pdfOptions, clientApi, fitsDictionary)
    }
    markup.map(_.render)
  }

  /**
   * Returns an HTML document describing the the given subsystem.
   * If a target subsystem is given, the information is restricted to the ICD from
   * the subsystem to the target. If components are given, the output is restricted to
   * parts that related to the components.
   *
   * For ICDs, the complete document consists of two parts: subsystem to target and target to subsystem.
   *
   * @param sv              the selected subsystem and version
   * @param targetSv        the target subsystem and version
   * @param maybeIcdVersion optional ICD version, to be displayed in the title
   * @param pdfOptions      options for generating HTML/PDF
   *
   */
  private def getIcdAsHtml(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybeIcdVersion: Option[IcdVersion],
      pdfOptions: PdfOptions
  ): Option[String] = {

    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val fitsKeyMap = IcdFits(db).getFitsKeyMap(maybePdfOptions)
    val query =
      new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem, targetSv.subsystem)), Some(pdfOptions), fitsKeyMap)
    val versionManager = new CachedIcdVersionManager(query)
    val icdInfoList    = getIcdModelList(sv, targetSv)
    val markup = for {
      subsystemInfo       <- getSubsystemInfo(sv)
      targetSubsystemInfo <- getSubsystemInfo(targetSv)
    } yield {
      import scalatags.Text.all.*
      val infoList               = getComponentInfo(versionManager, sv, Some(targetSv), fitsKeyMap)
      val titleInfo              = TitleInfo(subsystemInfo, Some(targetSv), maybeIcdVersion, documentNumber = pdfOptions.documentNumber)
      val titleInfo1             = TitleInfo(subsystemInfo, Some(targetSv), maybeIcdVersion, "(Part 1)")
      val infoList2              = getComponentInfo(versionManager, targetSv, Some(sv), fitsKeyMap)
      val titleInfo2             = TitleInfo(targetSubsystemInfo, Some(sv), maybeIcdVersion, "(Part 2)")
      val nh                     = new NumberedHeadings
      val subsystemVersion       = subsystemInfo.sv.maybeVersion.getOrElse(unpublished)
      val targetSubsystemVersion = targetSubsystemInfo.sv.maybeVersion.getOrElse(unpublished)
      val mainContent = div(
        style := "width: 100%;",
        p(strong(s"${subsystemInfo.sv.subsystem}: ${subsystemInfo.title} $subsystemVersion")),
        raw(subsystemInfo.description),
        if (subsystemInfo.sv == targetSubsystemInfo.sv) {
          // Two components in same subsystem
          div(
            icdInfoList.map(i => div(p(strong(i.titleStr)), raw(i.description))),
            SummaryTable.displaySummary(subsystemInfo, Some(targetSv), infoList, nh, clientApi = false, displayTitle = true),
            makeIntro(titleInfo1),
            displayDetails(infoList, nh, forApi = false, pdfOptions, clientApi = clientApi)
          )
        }
        else {
          // Two subsystems
          div(
            p(strong(s"${targetSubsystemInfo.sv.subsystem}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
            raw(targetSubsystemInfo.description),
            icdInfoList.map(i => div(p(strong(i.titleStr)), raw(i.description))),
            SummaryTable.displaySummary(subsystemInfo, Some(targetSv), infoList, nh, clientApi = false, displayTitle = true),
            SummaryTable.displaySummary(targetSubsystemInfo, Some(sv), infoList2, nh, clientApi = false, displayTitle = false),
            makeIntro(titleInfo1),
            displayDetails(infoList, nh, forApi = false, pdfOptions, clientApi = clientApi),
            makeIntro(titleInfo2),
            displayDetails(infoList2, nh, forApi = false, pdfOptions, clientApi = clientApi),
            if (sv.subsystem == "DMS" && targetSv.subsystem != "DMS" || targetSv.subsystem == "DMS" && sv.subsystem != "DMS") {
              // Special case: When DMS is involved, ICD consists of "Archived Items Report" with an ICD header
              // page (DEOPSICDDB-138)
              val sv2 = if (sv.subsystem == "DMS") targetSv else sv
              div (
                ArchivedItemsReport(db, Some(sv2), maybePdfOptions, nh)
                  .makeReportMarkup(s"Archived Items for ${sv2.subsystem}")
              )
            } else div()
          )
        }
      )
      val toc = nh.mkToc()

      html(
        head(
          scalatags.Text.tags2.title(titleInfo.title),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
        ),
        body(
          getTitleMarkup(titleInfo),
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          toc,
          div(cls := "pagebreakBefore"),
          getTitleMarkup(titleInfo, titleId = "title2"),
          mainContent
        )
      )
    }
    markup.map(_.render)
  }

  /**
   * Saves a document describing the API for the given subsystem/component or ICD between two subsystems/components
   * to the given file, in a format determined by the file's suffix, which should be one of (html, pdf).
   *
   * @param subsystemStr         the name of the subsystem (or component's subsystem) to print, followed by optional :version
   * @param maybeComponent       optional names of the component to print (separated by ",")
   * @param maybeTarget          optional target subsystem, followed by optional :version
   * @param maybeTargetComponent optional name of target component (default is to use all target components)
   * @param maybeIcdVersion      optional icd version (overrides source and target subsystem versions)
   * @param pdfOptions           options for PDF/HTML generation
   * @param file                 the file in which to save the document (should end with .html or .pdf)
   */
  def saveToFile(
      subsystemStr: String,
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      pdfOptions: PdfOptions,
      file: File
  ): Unit = {

    val isPdf = file.getName.endsWith(".pdf")

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html, showLogo = true, pdfOptions)

    val s1 = IcdVersionManager.SubsystemAndVersion(subsystemStr)

    val (subsys, maybeTarg, maybeIcdV) = maybeTarget match {
      case Some(t) => // ICD
        val s2 = IcdVersionManager.SubsystemAndVersion(t)
        // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
        // if only the subsystem or target versions were given, use those (default to latest versions)
        val v = maybeIcdVersion.getOrElse("*")
        val maybeIv =
          db.versionManager.getIcdVersions(s1.subsystem, s2.subsystem).find(_.icdVersion.icdVersion == v).map(_.icdVersion)
        val (sv, maybeTargetSv) = if (maybeIv.isDefined) {
          val i = maybeIv.get
          (
            SubsystemWithVersion(i.subsystem, Some(i.subsystemVersion), maybeComponent),
            Some(SubsystemWithVersion(i.target, Some(i.targetVersion), maybeTargetComponent))
          )
        }
        else {
          (
            SubsystemWithVersion(s1.subsystem, s1.maybeVersion, maybeComponent),
            Some(SubsystemWithVersion(s2.subsystem, s2.maybeVersion, maybeTargetComponent))
          )
        }
        (sv, maybeTargetSv, maybeIv)

      case None => // API
        val sv = SubsystemWithVersion(s1.subsystem, s1.maybeVersion, maybeComponent)
        (sv, None, None)
    }

    val maybeCachedBytes = if (isPdf) {
      if (maybeTarg.isDefined)
        maybeCache.flatMap(_.getIcd(subsys, maybeTarg.get, pdfOptions))
      else
        maybeCache.flatMap(_.getApi(subsys, pdfOptions, searchAllSubsystems, clientApi))
    }
    else None

    if (maybeCachedBytes.isDefined) {
      val out = new FileOutputStream(file)
      out.write(maybeCachedBytes.get)
      out.close()
    }
    else {
      val maybeHtml = if (maybeTarg.isDefined) {
        getIcdAsHtml(subsys, maybeTarg.get, maybeIcdV, pdfOptions)
      }
      else {
        getApiAsHtml(subsys, pdfOptions)
      }

      maybeHtml match {
        case Some(html) =>
          file.getName.split('.').drop(1).lastOption match {
            case Some("html") =>
              saveAsHtml(html)
            case Some("pdf") =>
              saveAsPdf(html)
              maybeCache.foreach {
                _.save(subsys, maybeTarg, pdfOptions, searchAllSubsystems, clientApi, file)
              }

            case _ => println(s"Unsupported output format: Expected *.html or *.pdf")
          }
        case None =>
          println(s"Failed to generate $file. You might need to run: 'icd-git --ingest' first to update the database.")
      }
    }
  }

  /**
   * Saves the API for the given subsystem version in PDF format and returns a byte array containing the PDF
   * data, if successful.
   * @param sv the subsystem with version
   * @param pdfOptions PDF generation options
   * @return byte array with the PDF data
   */
  def saveApiAsPdf(sv: SubsystemWithVersion, pdfOptions: PdfOptions): Option[Array[Byte]] = {
    val maybeCachedBytes = maybeCache.flatMap(_.getApi(sv, pdfOptions, searchAllSubsystems, clientApi))
    if (maybeCachedBytes.isDefined)
      maybeCachedBytes
    else
      getApiAsHtml(sv, pdfOptions).map { html =>
        val out = new ByteArrayOutputStream()
        IcdToPdf.saveAsPdf(out, html, showLogo = true, pdfOptions)
        val bytes = out.toByteArray
        maybeCache.foreach(_.saveApi(sv, pdfOptions, searchAllSubsystems, clientApi, bytes))
        bytes
      }
  }

  def saveIcdAsPdf(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      iv: Option[IcdVersion],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val maybeCachedBytes = maybeCache.flatMap(_.getIcd(sv, targetSv, pdfOptions))
    if (maybeCachedBytes.isDefined)
      maybeCachedBytes
    else
      getIcdAsHtml(sv, targetSv, iv, pdfOptions).map { html =>
        val out = new ByteArrayOutputStream()
        IcdToPdf.saveAsPdf(out, html, showLogo = true, pdfOptions)
        val bytes = out.toByteArray
        maybeCache.foreach(_.saveIcd(sv, targetSv, pdfOptions, bytes))
        bytes
      }
  }
}
