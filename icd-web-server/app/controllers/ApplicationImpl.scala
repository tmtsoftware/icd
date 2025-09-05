package controllers

import java.io.{ByteArrayOutputStream, File}
import controllers.ApplicationData.maybeCache
import csw.services.icd.IcdToPdf
import csw.services.icd.codegen.{JavaCodeGenerator, PythonCodeGenerator, ScalaCodeGenerator, TypescriptCodeGenerator}
import csw.services.icd.db.IcdVersionManager.{SubsystemAndVersion, VersionDiff}
import csw.services.icd.db.{
  AlarmsReport,
  ArchivedItemsReport,
  CachedIcdDbQuery,
  CachedIcdVersionManager,
  ComponentInfoHelper,
  IcdComponentInfo,
  IcdDb,
  IcdDbPrinter,
  IcdDbQuery,
  IcdVersionManager,
  MissingItemsReport,
  getFileContents
}
import csw.services.icd.fits.{IcdFits, IcdFitsPrinter}
import csw.services.icd.github.IcdGitManager
import csw.services.icd.html.NumberedHeadings
import csw.services.icd.viz.IcdVizManager
import diffson.playJson.DiffsonProtocol
import icd.web.shared.IcdModels.IcdModel
import icd.web.shared.{
  ApiVersionInfo,
  ComponentInfo,
  DiffInfo,
  FitsDictionary,
  FitsTags,
  HtmlHeadings,
  IcdName,
  IcdVersion,
  IcdVersionInfo,
  IcdVizOptions,
  PdfOptions,
  PublishApiInfo,
  PublishIcdInfo,
  SubsystemInfo,
  SubsystemWithVersion,
  UnpublishApiInfo,
  UnpublishIcdInfo,
  VersionInfo
}
import play.api.libs.json.Json

import scala.util.Try

case class ApplicationImpl(db: IcdDb) {
  private val log = play.Logger.of("ApplicationImpl")

  val icdGitManager = IcdGitManager(db.versionManager)

  // Cache of API and ICD versions published on GitHub (cached for better performance)
  var (allApiVersions, allIcdVersions) =
    try {
      icdGitManager.ingestLatest(db)
    }
    catch {
      case ex: Exception =>
        // XXX TODO FIXME: How to handle this unlikely error at startup? Exit? Retry?
        log.error("Failed to fetch API and ICD versions from tmt-icd repo on GitHub", ex)
        (Nil, Nil)
    }

  // Update the database and cache after a new API or ICD was published (or in case one was published)
  def updateAfterPublish(): Unit = {
    val pair = icdGitManager.ingestLatest(db)
    allApiVersions = pair._1
    allIcdVersions = pair._2
  }

  // Note: Using try/catch below to prevent an exception from killing the application actor,
  // which is not automatically restarted

  def getSubsystemNames: List[String] = {
    try {
      val subsystemsInDb          = db.query.getSubsystemNames
      val publishedSubsystemNames = allApiVersions.map(_.subsystem)
      (publishedSubsystemNames ++ subsystemsInDb).distinct.sorted
    }
    catch {
      case ex: Exception =>
        log.error("Failed to get subsystem names", ex)
        Nil
    }
  }

  /**
   * Gets information about a named subsystem
   */
  def getSubsystemInfo(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]): Option[SubsystemInfo] = {
    val sv = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    try {
      if (sv.maybeComponent.isDefined) {
        db.versionManager
          .getComponentModel(sv, None)
          .map(m => SubsystemInfo(sv, m.title, m.description))

      }
      else {
        db.versionManager
          .getSubsystemModel(sv, None)
          .map(m => SubsystemInfo(sv, m.title, m.description))
      }
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get subsystem info for $sv", ex)
        None
    }
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def getComponents(subsystem: String, maybeVersion: Option[String]): List[String] = {
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    try {
      db.versionManager.getComponentNames(sv)
    }
    catch {
      case ex: Exception =>
        log.error(s"Could not get list of components for $sv", ex)
        Nil
    }
  }

  /**
   * Query the database for information about the subsystem's components
   *
   * @param subsystem      the subsystem
   * @param maybeVersion   the subsystem's version (default: current)
   * @param maybeComponent component name (default all in subsystem)
   * @param searchAll      if true, search all components for API dependencies
   * @param clientApiOpt   if true, include subscribed events, sent commands
   */
  def getComponentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApiOpt: Option[Boolean]
  ): List[ComponentInfo] = {
    val sv                  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val clientApi           = clientApiOpt.getOrElse(false)
    val searchAllSubsystems = clientApi && searchAll.getOrElse(false)
    val subsystems          = if (searchAllSubsystems) None else Some(List(sv.subsystem))
    try {
      val fitsKeyMap     = IcdFits(db).getFitsKeyMap()
      val query          = new CachedIcdDbQuery(db, subsystems, None, fitsKeyMap)
      val versionManager = new CachedIcdVersionManager(query)
      new ComponentInfoHelper(
        versionManager,
        displayWarnings = searchAllSubsystems,
        clientApi = clientApi
      ).getComponentInfoList(sv, None, fitsKeyMap)
    }
    catch {
      case ex: Exception =>
        log.error(s"Could not get component info for $sv", ex)
        Nil
    }
  }

  /**
   * Query the database for information about the given components in an ICD
   *
   * @param subsystem           the source subsystem
   * @param maybeVersion        the source subsystem's version (default: current)
   * @param maybeComponent      optional component name (default: all in subsystem)
   * @param target              the target subsystem
   * @param maybeTargetVersion  the target subsystem's version
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   */
  def getIcdComponentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String]
  ): List[ComponentInfo] = {
    val sv       = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val targetSv = SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
    try {
      val fitsKeyMap     = IcdFits(db).getFitsKeyMap()
      val query          = new CachedIcdDbQuery(db, Some(List(sv.subsystem, targetSv.subsystem)), None, fitsKeyMap)
      val versionManager = new CachedIcdVersionManager(query)
      IcdComponentInfo.getComponentInfoList(versionManager, sv, targetSv, None, fitsKeyMap)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get ICD component info for ICD between $sv and $targetSv", ex)
        Nil
    }
  }

  // Returns the selected subsystem, target subsystem and optional ICD version
  // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
  // if only the subsystem or target versions were given, use those (default to latest versions)
  private def getSelectedSubsystems(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String]
  ): (SubsystemWithVersion, SubsystemWithVersion, Option[IcdVersion]) = {
    val v        = maybeIcdVersion.getOrElse("*")
    val versions = db.versionManager.getIcdVersions(subsystem, target)
    val iv       = versions.find(_.icdVersion.icdVersion == v).map(_.icdVersion)
    if (iv.isDefined) {
      val i = iv.get
      (
        SubsystemWithVersion(i.subsystem, Some(i.subsystemVersion), maybeComponent),
        SubsystemWithVersion(i.target, Some(i.targetVersion), maybeTargetComponent),
        iv
      )
    }
    else {
      (
        SubsystemWithVersion(subsystem, maybeVersion, maybeComponent),
        SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent),
        iv
      )
    }
  }

  /**
   * Returns the PDF for the given ICD
   *
   * @param subsystem          the source subsystem
   * @param maybeVersion       the source subsystem's version (default: current)
   * @param maybeComponent      optional component name (default: all in subsystem)
   * @param target             the target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param maybeIcdVersion    optional ICD version (default: current)
   * @param pdfOptions         options for PDF generation
   */
  def getIcdAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    try {
      val (sv, targetSv, iv) = getSelectedSubsystems(
        subsystem,
        maybeVersion,
        maybeComponent,
        target,
        maybeTargetVersion,
        maybeTargetComponent,
        maybeIcdVersion
      )
      val icdPrinter = IcdDbPrinter(db, searchAllSubsystems = false, clientApi = true, maybeCache, Some(pdfOptions))
      val result     = icdPrinter.saveIcdAsPdf(sv, targetSv, iv, pdfOptions)
      result
    }
    catch {
      case ex: Exception =>
        val sv       = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
        val targetSv = SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
        val v        = maybeIcdVersion.getOrElse("*")
        log.error(s"Failed to generate PDF for ICD (version: $v) between $sv and $targetSv", ex)
        None
    }
  }

  /**
   * Returns the PDF for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param searchAll      if true, search all components for API dependencies
   * @param clientApiOpt      if true, include subscribed events and sent commands in the API
   * @param pdfOptions     options for PDF generation
   */
  def getApiAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApiOpt: Option[Boolean],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val sv                  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val clientApi           = clientApiOpt.getOrElse(false)
    val searchAllSubsystems = clientApi && searchAll.getOrElse(false)
    val icdPrinter          = IcdDbPrinter(db, searchAllSubsystems, clientApi, maybeCache, Some(pdfOptions))
    try {
      icdPrinter.saveApiAsPdf(sv, pdfOptions)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to generate PDF for API for $sv", ex)
        None
    }
  }

  /**
   * Returns the PDF for the FITS keywords
   *
   * @param tag "All" for all keywords, otherwise restrict output to given tag, as defined in DMS-Model-Files/FITS-Dictionary
   * @param pdfOptions options for PDF generation
   */
  def getFitsDictionaryAsPdf(
      tag: String,
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val maybeTag = if (tag == "All") None else Some(tag)
    try {
      val fitsDictionary =
        IcdFits(db).getFitsDictionary(maybeSubsystem = None, maybeTag = maybeTag, maybePdfOptions = Some(pdfOptions))
      IcdFitsPrinter(fitsDictionary).saveAsPdf(maybeTag, pdfOptions)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get FITS dictionary for tag $tag", ex)
        None
    }
  }

  /**
   * Returns the archived items report (PDF) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param pdfOptions         options for PDF generation
   */
  def getArchivedItemsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream()
    val sv  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    try {
      val html = ArchivedItemsReport(db, Some(sv), Some(pdfOptions), new HtmlHeadings).makeReport(pdfOptions)
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get archived items report for $sv", ex)
        None
    }
  }

  /**
   * Returns the archived items report (HTML) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   */
  def getArchivedItemsReportHtml(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String]
  ): Option[String] = {
    val sv = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    try {
      Some(
        ArchivedItemsReport(db, Some(sv), None, new HtmlHeadings)
          .makeReportMarkup(s"Archived Items for ${sv.subsystem}")
          .render
      )
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get archived items report for $sv", ex)
        None
    }
  }

  /**
   * Returns the archived items report (PDF) for all current subsystems
   * @param pdfOptions         options for PDF generation
   */
  def getArchivedItemsReportFull(
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream()
    try {
      val html = ArchivedItemsReport(db, None, Some(pdfOptions), new HtmlHeadings).makeReport(pdfOptions)
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error("Failed to get full archived items report", ex)
        None
    }
  }

  /**
   * Returns the alarms report (PDF) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param pdfOptions         options for PDF generation
   */
  def getAlarmsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream()
    val sv  = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    try {
      val html = AlarmsReport(db, Some(sv), Some(pdfOptions), new HtmlHeadings).makeReport(pdfOptions)
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get alarms report for $sv", ex)
        None
    }
  }

  /**
   * Returns the alarms report (PDF) for all current subsystems
   * @param pdfOptions         options for PDF generation
   */
  def getAlarmsReportFull(
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream()
    try {
      val html = AlarmsReport(db, None, Some(pdfOptions), new HtmlHeadings).makeReport(pdfOptions)
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error("Failed to get full alarms report", ex)
        None
    }
  }

  /**
   * Returns a missing items report (PDF) for the given subsystem API
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param maybeTarget    optional target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param pdfOptions         options for PDF generation
   */
  def getMissingItemsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out           = new ByteArrayOutputStream()
    val sv            = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val maybeTargetSv = maybeTarget.map(target => SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent))
    try {
      val html = MissingItemsReport(db, List(Some(sv), maybeTargetSv).flatten, pdfOptions).makeReport()
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get missing items report for $sv", ex)
        None
    }
  }

  /**
   * Returns a missing items list (HTML) for the given subsystem APIs
   *
   * @param subsystem      the source subsystem
   * @param maybeVersion   the source subsystem's version (default: current)
   * @param maybeComponent optional component (default: all in subsystem)
   * @param maybeTarget    optional target subsystem
   * @param maybeTargetVersion optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param pdfOptions         options for PDF generation
   */
  def getMissingItemsReportHtml(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      pdfOptions: PdfOptions
  ): Option[String] = {
    val sv            = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val maybeTargetSv = maybeTarget.map(target => SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent))
    try {
      Some(
        MissingItemsReport(db, List(Some(sv), maybeTargetSv).flatten, pdfOptions)
          .makeReportMarkup(new HtmlHeadings)
          .render
      )
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get missing items report for $sv", ex)
        None
    }
  }

  /**
   * Returns the missing items report (PDF) for all current subsystems
   * @param pdfOptions         options for PDF generation
   */
  def getMissingItemsReportFull(
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out = new ByteArrayOutputStream()
    try {
      val html = MissingItemsReport(db, List(), pdfOptions).makeReport()
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      Some(out.toByteArray)
    }
    catch {
      case ex: Exception =>
        log.error("Failed to get full missing items report", ex)
        None
    }
  }

  /**
   * Returns a generated graph of component relationships for the selected components
   *
   * @param subsystem            the source subsystem
   * @param maybeVersion         the source subsystem's version (default: current)
   * @param maybeComponent       optional component name (default: all in subsystem)
   * @param maybeTarget          optional target subsystem
   * @param maybeTargetVersion   optional target subsystem's version (default: current)
   * @param maybeTargetComponent optional target component name (default: all in target subsystem)
   * @param maybeIcdVersion      optional ICD version (default: current)
   * @param options              options for graph generation
   */
  def makeGraph(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      options: IcdVizOptions
  ): Option[Array[Byte]] = {
    try {
      val selectedSv = maybeTarget match {
        case Some(target) =>
          val (sv, targetSv, _) = getSelectedSubsystems(
            subsystem,
            maybeVersion,
            maybeComponent,
            target,
            maybeTargetVersion,
            maybeTargetComponent,
            maybeIcdVersion
          )
          List(sv, targetSv)
        case None =>
          List(SubsystemWithVersion(subsystem, maybeVersion, maybeComponent))
      }
      val subsystems = selectedSv.filter(_.maybeComponent.isEmpty)
      val components = selectedSv.filter(_.maybeComponent.nonEmpty)
      val newOptions =
        options.copy(subsystems = subsystems, components = components, showPlot = false, imageFile = None, dotFile = None)
      val out = new ByteArrayOutputStream()
      if (IcdVizManager.showRelationships(db, newOptions, Some(out))) Some(out.toByteArray) else None
    }
    catch {
      case ex: Exception =>
        val sv       = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
        val targetSv = maybeTarget.map(target => SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent))
        val v        = maybeIcdVersion.getOrElse("*")
        log.error(s"Failed to make graph for subsystem1 = $sv, subsystem2 = $targetSv, icd version - $v", ex)
        None
    }
  }

  /**
   * Returns a detailed list of the versions of the given subsystem
   */
  def getVersions(subsystem: String): List[VersionInfo] = {
    allApiVersions
      .filter(_.subsystem == subsystem)
      .flatMap(_.apis)
      .map(a => VersionInfo(Some(a.version), a.user, a.comment, a.date))
  }

  /**
   * Returns a list of version names for the given subsystem
   */
  def getVersionNames(subsystem: String): List[String] = {
    allApiVersions
      .filter(_.subsystem == subsystem)
      .flatMap(_.apis)
      .map(_.version)
  }

  /**
   * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
   */
  def getIcdNames: List[IcdName] = {
    val list = allIcdVersions.map(i => IcdName(i.subsystems.head, i.subsystems.tail.head))
    list.sortWith((a, b) => a.subsystem.compareTo(b.subsystem) < 0)
  }

  /**
   * Gets a list of versions for the ICD from subsystem to target subsystem
   */
  def getIcdVersions(subsystem: String, target: String): List[IcdVersionInfo] = {
    // convert list to use shared IcdVersion class
    allIcdVersions.find(i => i.subsystems.head == subsystem && i.subsystems.tail.head == target).toList.flatMap(_.icds).map {
      icd =>
        val icdVersion = IcdVersion(icd.icdVersion, subsystem, icd.versions.head, target, icd.versions.tail.head)
        IcdVersionInfo(icdVersion, icd.user, icd.comment, icd.date)
    }
  }

  // Packages the diff information for return to browser
  private def getDiffInfo(diff: VersionDiff): DiffInfo = {
    import DiffsonProtocol.*
    val jsValue = Json.toJson(diff.patch)
    val s       = Json.prettyPrint(jsValue)
    DiffInfo(diff.path, s)
  }

  /**
   * Gets the difference between two subsystem versions
   */
  def getDiff(subsystem: String, versionsStr: String): List[DiffInfo] = {
    try {
      val versions = versionsStr.split(',')
      val v1       = versions.head
      val v2       = versions.tail.head
      val v1Opt    = if (v1.nonEmpty) Some(v1) else None
      val v2Opt    = if (v2.nonEmpty) Some(v2) else None
      // convert list to use shared IcdVersion class
      db.versionManager.diff(subsystem, v1Opt, v2Opt).map(getDiffInfo)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get diff for $subsystem versions $versionsStr", ex)
        Nil
    }
  }

  /**
   * Publish the selected API (add an entry for the current commit of the master branch on GitHub)
   */
  def publishApi(publishApiInfo: PublishApiInfo): Try[ApiVersionInfo] = {
    Try {
      val apiVersionInfo = icdGitManager.publish(
        publishApiInfo.subsystem,
        publishApiInfo.majorVersion,
        publishApiInfo.user,
        publishApiInfo.password,
        publishApiInfo.comment
      )
      updateAfterPublish()
      apiVersionInfo
    }
  }

  /**
   * Publish an ICD (add an entry to the icds file on the master branch of https://github.com/tmt-icd/ICD-Model-Files)
   */
  def publishIcd(publishIcdInfo: PublishIcdInfo): Try[IcdVersionInfo] = {
    Try {
      val sv         = SubsystemAndVersion(publishIcdInfo.subsystem, Some(publishIcdInfo.subsystemVersion))
      val tv         = SubsystemAndVersion(publishIcdInfo.target, Some(publishIcdInfo.targetVersion))
      val subsystems = List(sv, tv)
      val icdVersionInfo = icdGitManager.publish(
        subsystems,
        publishIcdInfo.majorVersion,
        publishIcdInfo.user,
        publishIcdInfo.password,
        publishIcdInfo.comment
      )
      updateAfterPublish()
      icdGitManager.importIcdFiles(db, subsystems, (s: String) => println(s), allIcdVersions)
      icdVersionInfo
    }
  }

  /**
   * Unublish the selected API (removes an entry from the file in the master branch on GitHub)
   */
  def unpublishApi(unpublishApiInfo: UnpublishApiInfo): Try[Option[ApiVersionInfo]] = {
    Try {
      val result = icdGitManager.unpublish(
        SubsystemAndVersion(unpublishApiInfo.subsystem, Some(unpublishApiInfo.subsystemVersion)),
        unpublishApiInfo.user,
        unpublishApiInfo.password,
        unpublishApiInfo.comment
      )
      result.foreach(_ => updateAfterPublish())
      result
    }
  }

  /**
   * Unpublish an ICD (remove an entry in the icds file on the master branch of https://github.com/tmt-icd/ICD-Model-Files)
   */
  def unpublishIcd(unpublishIcdInfo: UnpublishIcdInfo): Try[Option[IcdVersionInfo]] = {
    Try {
      val maybeIcdEntry = icdGitManager.unpublish(
        unpublishIcdInfo.icdVersion,
        unpublishIcdInfo.subsystem,
        unpublishIcdInfo.target,
        unpublishIcdInfo.user,
        unpublishIcdInfo.password,
        unpublishIcdInfo.comment
      )
      maybeIcdEntry.map { e =>
        updateAfterPublish()
        val icdVersion =
          IcdVersion(e.icdVersion, unpublishIcdInfo.subsystem, e.versions.head, unpublishIcdInfo.target, e.versions.tail.head)
        IcdVersionInfo(icdVersion, e.user, e.comment, e.date)
      }
    }
  }

  /**
   * Updates the cache of published APIs and ICDs (in case new ones were published)
   */
  def updatePublished(): Unit = {
    try {
      updateAfterPublish()
    }
    catch {
      case ex: Exception =>
        log.error("Failed to update cache of published APIs and ICDs", ex)
    }
  }

  /**
   * Gets optional information about the ICD between two subsystems
   * (from the <subsystem>-icd-model.conf files)
   *
   * @param subsystem           the source subsystem
   * @param maybeVersion        the source subsystem's version
   * @param target              the target subsystem
   * @param maybeTargetVersion  the target subsystem's version
   */
  def getIcdModels(
      subsystem: String,
      maybeVersion: Option[String],
      target: String,
      maybeTargetVersion: Option[String]
  ): List[IcdModel] = {
    val sv       = SubsystemWithVersion(subsystem, maybeVersion, None)
    val targetSv = SubsystemWithVersion(target, maybeTargetVersion, None)

    val query          = new IcdDbQuery(db, Some(List(sv.subsystem, targetSv.subsystem)))
    val versionManager = new IcdVersionManager(query)
    try {
      versionManager.getIcdModels(sv, targetSv, None)
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to get ICD models for ICD between $sv and $targetSv", ex)
        Nil
    }
  }

  /**
   * Returns the generated source code for the given subsystem/component API in the given language
   *
   * @param subsystem        the source subsystem
   * @param lang             the language to generate (scala, java, typescript, python)
   * @param className        the top level class name to generate
   * @param maybeVersion     the source subsystem's version (default: current)
   * @param maybeComponent   optional component (default: all in subsystem)
   * @param maybePackageName optional package name for generated scala/java code
   */
  def generate(
      subsystem: String,
      lang: String,
      className: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybePackageName: Option[String]
  ): Option[String] = {
    val suffix = lang
      .toLowerCase()
      .replace("typescript", "ts")
      .replace("python", "py")
    val sourceFile = new File(s"$className.$suffix")
    val tempFile   = Some(File.createTempFile(className, s".$suffix"))
    val versionStr = maybeVersion.map(v => s":$v").getOrElse("")
    val subsysVers = s"$subsystem$versionStr"

    def readAndDeleteFile(): Option[String] = {
      try {
        val result = getFileContents(tempFile.get)
        tempFile.get.delete()
        Some(result)
      }
      catch {
        case ex: Exception =>
          ex.printStackTrace()
          None
      }
    }
    try {
      suffix match {
        case "scala" =>
          new ScalaCodeGenerator(db).generate(
            subsysVers,
            maybeComponent,
            sourceFile,
            tempFile,
            maybePackageName
          )
          readAndDeleteFile()
        case "java" =>
          new JavaCodeGenerator(db).generate(
            subsysVers,
            maybeComponent,
            sourceFile,
            tempFile,
            maybePackageName
          )
          readAndDeleteFile()
        case "ts" =>
          new TypescriptCodeGenerator(db).generate(
            subsysVers,
            maybeComponent,
            sourceFile,
            tempFile,
            maybePackageName
          )
          readAndDeleteFile()
        case "py" =>
          new PythonCodeGenerator(db).generate(
            subsysVers,
            maybeComponent,
            sourceFile,
            tempFile,
            maybePackageName
          )
          readAndDeleteFile()
        case _ =>
          println(s"Unsupported language fo code generation: $lang")
          None
      }
    }
    catch {
      case ex: Exception =>
        log.error(s"Failed to generate $sourceFile for $subsysVers", ex)
        None
    }
  }

  def getFitsDictionary(maybeSubsystem: Option[String], maybeComponent: Option[String]): FitsDictionary = {
    try {
      IcdFits(db).getFitsDictionary(maybeSubsystem, maybeComponent)
    }
    catch {
      case ex: Exception =>
        val maybeSv = maybeSubsystem.map(subsystem => SubsystemWithVersion(subsystem, None, maybeComponent))
        log.error(s"Failed to get FITS dictionary for $maybeSv", ex)
        FitsDictionary(Nil, FitsTags(Map.empty))
    }
  }

}
