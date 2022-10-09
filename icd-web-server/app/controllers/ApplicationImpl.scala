package controllers

import java.io.{ByteArrayOutputStream, File}
import controllers.ApplicationData.maybeCache
import csw.services.icd.IcdToPdf
import csw.services.icd.codegen.{JavaCodeGenerator, PythonCodeGenerator, ScalaCodeGenerator, TypescriptCodeGenerator}
import csw.services.icd.db.IcdVersionManager.{SubsystemAndVersion, VersionDiff}
import csw.services.icd.db.{ArchivedItemsReport, CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdComponentInfo, IcdDb, IcdDbPrinter, IcdDbQuery, IcdVersionManager}
import csw.services.icd.fits.{IcdFits, IcdFitsPrinter}
import csw.services.icd.github.IcdGitManager
import csw.services.icd.html.OpenApiToHtml
import csw.services.icd.viz.IcdVizManager
import diffson.playJson.DiffsonProtocol
import icd.web.shared.AllEventList.EventsForSubsystem
import icd.web.shared.IcdModels.IcdModel
import icd.web.shared.{ApiVersionInfo, ComponentInfo, DiffInfo, FitsDictionary, FitsKeyInfo, FitsTags, IcdName, IcdVersion, IcdVersionInfo, IcdVizOptions, PdfOptions, PublishApiInfo, PublishIcdInfo, SubsystemInfo, SubsystemWithVersion, UnpublishApiInfo, UnpublishIcdInfo, VersionInfo}
import play.api.libs.json.Json

import scala.util.Try

class ApplicationImpl(db: IcdDb) {
  // Cache of API and ICD versions published on GitHub (cached for better performance)
  var (allApiVersions, allIcdVersions) = IcdGitManager.ingestMissing(db)

  // Update the database and cache after a new API or ICD was published (or in case one was published)
  private def updateAfterPublish(): Unit = {
    val pair = IcdGitManager.ingestMissing(db)
    allApiVersions = pair._1
    allIcdVersions = pair._2
  }

  def getSubsystemNames: List[String] = {
    val subsystemsInDb          = db.query.getSubsystemNames
    val publishedSubsystemNames = allApiVersions.map(_.subsystem)
    (publishedSubsystemNames ++ subsystemsInDb).distinct.sorted
  }

  /**
   * Gets information about a named subsystem
   */
  def getSubsystemInfo(subsystem: String, maybeVersion: Option[String]): Option[SubsystemInfo] = {
    // Get the subsystem info from the database, or if not found, look in the published GitHub repo
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    db.versionManager.getSubsystemModel(sv, None).map(model => SubsystemInfo(sv, model.title, model.description))
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def getComponents(subsystem: String, maybeVersion: Option[String]): List[String] = {
    val sv = SubsystemWithVersion(subsystem, maybeVersion, None)
    db.versionManager.getComponentNames(sv)
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
    val fitsKeyMap          = IcdFits(db).getFitsKeyMap()
    val query               = new CachedIcdDbQuery(db.db, db.admin, subsystems, None, fitsKeyMap)
    val versionManager      = new CachedIcdVersionManager(query)
    new ComponentInfoHelper(displayWarnings = searchAllSubsystems, clientApi = clientApi, maybeStaticHtml = Some(false))
      .getComponentInfoList(versionManager, sv, None, fitsKeyMap)
  }

  /**
   * Gets a list of all published events by subsystem/component
   * (assumes latest versions of all subsystems).
   */
  def getEventList: List[EventsForSubsystem] = {
    val fitsKeyMap = IcdFits(db).getFitsKeyMap()
    val query      = new CachedIcdDbQuery(db.db, db.admin, None, None, fitsKeyMap)
    query.getEventList(fitsKeyMap)
  }

//  /**
//   * Gets information about the given event in the given subsystem/component
//   * (assumes latest versions of all subsystems).
//   */
//  def getEventInfo(subsystem: String, component: String, event: String, fitsKeyMap: FitsKeyMap): Option[EventModel] = {
//    val componentModel = db.query.getComponentModel(subsystem, component, None)
//    componentModel
//      .flatMap(db.query.getPublishModel(_, None, fitsKeyMap))
//      .flatMap(_.eventList.find(_.name == event))
//  }

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
    val sv             = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val targetSv       = SubsystemWithVersion(target, maybeTargetVersion, maybeTargetComponent)
    val fitsKeyMap     = IcdFits(db).getFitsKeyMap()
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem, targetSv.subsystem)), None, fitsKeyMap)
    val versionManager = new CachedIcdVersionManager(query)
    IcdComponentInfo.getComponentInfoList(versionManager, sv, targetSv, None, staticHtml = false, fitsKeyMap)
  }

  // Returns the selected subsystem, target subsystem and optional ICD version
  private def getSelectedSubsystems(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String]
  ): (SubsystemWithVersion, SubsystemWithVersion, Option[IcdVersion]) = {
    // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
    // if only the subsystem or target versions were given, use those (default to latest versions)
    val v = maybeIcdVersion.getOrElse("*")

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
    icdPrinter.saveIcdAsPdf(sv, targetSv, iv, pdfOptions)
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
    icdPrinter.saveApiAsPdf(sv, pdfOptions)
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
    val fitsDictionary = IcdFits(db).getFitsDictionary(maybeSubsystem = None, maybeTag = maybeTag, maybePdfOptions = Some(pdfOptions))
    IcdFitsPrinter(fitsDictionary).saveAsPdf(maybeTag, pdfOptions)
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
    val out  = new ByteArrayOutputStream()
    val sv   = SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
    val html = ArchivedItemsReport(db, Some(sv), Some(pdfOptions)).makeReport(pdfOptions)
    IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
    Some(out.toByteArray)
  }

  /**
   * Returns the archived items report (PDF) for all current subsystems
   * @param pdfOptions         options for PDF generation
   */
  def getArchivedItemsReportFull(
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    val out  = new ByteArrayOutputStream()
    val html = ArchivedItemsReport(db, None, Some(pdfOptions)).makeReport(pdfOptions)
    IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
    Some(out.toByteArray)
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
    IcdVizManager.showRelationships(db, newOptions, Some(out))
    Some(out.toByteArray)
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
    import DiffsonProtocol._
    val jsValue = Json.toJson(diff.patch)
    val s       = Json.prettyPrint(jsValue)
    DiffInfo(diff.path, s)
  }

  /**
   * Gets the difference between two subsystem versions
   */
  def getDiff(subsystem: String, versionsStr: String): List[DiffInfo] = {
    val versions = versionsStr.split(',')
    val v1       = versions.head
    val v2       = versions.tail.head
    val v1Opt    = if (v1.nonEmpty) Some(v1) else None
    val v2Opt    = if (v2.nonEmpty) Some(v2) else None
    // convert list to use shared IcdVersion class
    db.versionManager.diff(subsystem, v1Opt, v2Opt).map(getDiffInfo)
  }

  /**
   * Publish the selected API (add an entry for the current commit of the master branch on GitHub)
   */
  def publishApi(publishApiInfo: PublishApiInfo): Try[ApiVersionInfo] = {
    Try {
      val apiVersionInfo = IcdGitManager.publish(
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
      val icdVersionInfo = IcdGitManager.publish(
        subsystems,
        publishIcdInfo.majorVersion,
        publishIcdInfo.user,
        publishIcdInfo.password,
        publishIcdInfo.comment
      )
      updateAfterPublish()
      IcdGitManager.importIcdFiles(db, subsystems, (s: String) => println(s), allIcdVersions)
      icdVersionInfo
    }
  }

  /**
   * Unublish the selected API (removes an entry from the file in the master branch on GitHub)
   */
  def unpublishApi(unpublishApiInfo: UnpublishApiInfo): Try[Option[ApiVersionInfo]] = {
    Try {
      val result = IcdGitManager.unpublish(
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
      val maybeIcdEntry = IcdGitManager.unpublish(
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
    updateAfterPublish()
  }

  /**
   * Converts OpenApi JSON to HTML
   */
  def openApiToDynamicHtml(openApiJson: String): Try[String] = {
    Try(OpenApiToHtml.getHtml(openApiJson, staticHtml = false))
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

    val query          = new IcdDbQuery(db.db, db.admin, Some(List(sv.subsystem, targetSv.subsystem)))
    val versionManager = new IcdVersionManager(query)
    versionManager.getIcdModels(sv, targetSv, None)
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
        val src    = scala.io.Source.fromFile(tempFile.get)
        val result = src.mkString
        src.close()
        tempFile.get.delete()
        Some(result)
      }
      catch {
        case ex: Exception =>
          ex.printStackTrace()
          None
      }
    }
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
      case x =>
        println(s"Unsupported language fo code generation: $lang")
        None
    }
  }

  def getFitsDictionary(maybeSubsystem: Option[String], maybeComponent: Option[String]): FitsDictionary = {
    IcdFits(db).getFitsDictionary(maybeSubsystem, maybeComponent)
  }
}
