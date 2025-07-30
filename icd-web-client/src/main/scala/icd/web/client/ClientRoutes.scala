package icd.web.client

import icd.web.shared.IcdModels.ServicePath
import icd.web.shared.{IcdName, IcdVersion, IcdVizOptions, PdfOptions, SubsystemWithVersion}

import java.net.URLEncoder

/**
 * Defines URI routes to access the server API
 * (See icd-web-server/conf/routes file for server side)
 */
object ClientRoutes {

  /**
   * Gets a list of top level subsystem names
   */
  val subsystems = "/subsystems"

  // Return the attributes string based on the options
  private def getAttrs(
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeSearchAllSubsystems: Option[Boolean] = None,
      maybeClientApi: Option[Boolean] = None,
      maybeTargetVersion: Option[String] = None,
      maybeTargetCompName: Option[String] = None,
      maybeIcdVersion: Option[String] = None,
      maybePdfOptions: Option[PdfOptions] = None,
      maybeGraphOptions: Option[IcdVizOptions] = None,
      maybeTarget: Option[String] = None,
      maybePackageName: Option[String] = None
  ): String = {
    val versionAttr         = maybeVersion.map(v => s"version=$v")
    val componentAttr       = maybeComponent.map(c => s"component=$c")
    val searchAllAttr       = maybeSearchAllSubsystems.map(b => s"searchAll=$b")
    val clientApiAttr       = maybeClientApi.map(b => s"clientApi=$b")
    val targetVersionAttr   = maybeTargetVersion.map(v => s"targetVersion=$v")
    val targetComponentAttr = maybeTargetCompName.map(c => s"targetComponent=$c")
    val targetAttr          = maybeTarget.map(c => s"target=$c")
    val icdVersionAttr      = maybeIcdVersion.map(v => s"icdVersion=$v")
    val packageAttr         = maybePackageName.map(v => s"packageName=$v")
    val pdfAttrs = maybePdfOptions.map(o =>
      List(
        s"orientation=${o.orientation}",
        s"fontSize=${o.fontSize}",
        s"lineHeight=${o.lineHeight}",
        s"paperSize=${o.paperSize}",
        s"details=${o.details}",
        s"documentNumber=${o.documentNumber}"
      ).mkString("&")
    )
    val graphAttrs = maybeGraphOptions.map(o =>
      List(
        s"ratio=${o.ratio}",
        s"missingEvents=${o.missingEvents}",
        s"missingCommands=${o.missingCommands}",
        s"commandLabels=${o.commandLabels}",
        s"eventLabels=${o.eventLabels}",
        s"groupSubsystems=${o.groupSubsystems}",
        s"onlySubsystems=${o.onlySubsystems}",
        s"layout=${o.layout}",
        s"overlap=${o.overlap}",
        s"splines=${o.splines}",
        s"omitTypes=${o.omitTypes.mkString(",")}",
        s"imageFormat=${o.imageFormat}"
      ).mkString("&")
    )
    val attrs =
      (versionAttr ++ componentAttr ++ packageAttr ++ searchAllAttr ++ clientApiAttr ++
        targetAttr ++ targetVersionAttr ++ targetComponentAttr ++ icdVersionAttr ++
        pdfAttrs ++ graphAttrs ++ packageAttr)
        .mkString("&")
    if (attrs.isEmpty) "" else s"?$attrs"
  }

  /**
   * Gets top level information about a given version of the given subsystem
   */
  def icdModelList(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      None,
      maybeTargetVersion = targetSv.maybeVersion
    )
    s"/icdModelList/${sv.subsystem}/${targetSv.subsystem}$attrs"
  }

  /**
   * Gets top level information about a given version of the given subsystem
   */
  def subsystemInfo(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]): String = {
    val baseUri  = s"/subsystemInfo/$subsystem"
    val versAttr = maybeVersion.filter(_ != "*").map(s => s"version=$s")
    val compAttr = maybeComponent.map(s => s"component=$s")
    val attrs    = List(versAttr, compAttr).flatten
    attrs match {
      case Nil => baseUri
      case _   => s"$baseUri?${attrs.mkString("&")}"
    }
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, maybeVersion: Option[String]): String =
    maybeVersion match {
      case Some("*") | None => s"/components/$subsystem"
      case Some(version)    => s"/components/$subsystem?version=$version"
    }

  /**
   * Returns the route to use to get the information for a component
   *
   * @param sv      the subsystem
   * @return the URL path to use
   */
  def componentInfo(sv: SubsystemWithVersion, searchAllSubsystems: Boolean, clientApi: Boolean): String = {
    val attrs = getAttrs(sv.maybeVersion, sv.maybeComponent, Some(searchAllSubsystems), Some(clientApi))
    s"/componentInfo/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get the information for a component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param sv       the subsystem
   * @param maybeTargetSv defines the optional target subsystem and version
   * @param searchAllSubsystems if true, search all subsystems in the database for references, subscribers, etc.
   * @param clientApi if true, include subscribed events, sent commands in API
   * @return the URL path to use
   */
  def icdComponentInfo(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): String = {
    maybeTargetSv match {
      case None => componentInfo(sv, searchAllSubsystems, clientApi)
      case Some(targetSv) =>
        val attrs = getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent
        )
        s"/icdComponentInfo/${sv.subsystem}/${targetSv.subsystem}$attrs"
    }
  }

  /**
   * Returns the route to use to get a PDF for the given ICD or Subsystem with selected components.
   * If the target subsystem is defined, the document is restricted to the ICD
   * from subsystem to target, otherwise the API for the given subsystem and components is returned.
   *
   * @param sv       the subsystem
   * @param maybeTargetSv defines the optional target subsystem and version
   * @param icdVersion optional ICD version
   * @param searchAllSubsystems if true, search all subsystems in the database for references, subscribers, etc.
   * @param clientApi if true, include subscribed events and sent commands in the API
   * @param pdfOptions options for PDF generation
   * @return the URL path to use
   */
  def icdAsPdf(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      icdVersion: Option[String],
      searchAllSubsystems: Boolean,
      clientApi: Boolean,
      pdfOptions: PdfOptions
  ): String = {
    maybeTargetSv match {
      case None => apiAsPdf(sv, searchAllSubsystems, clientApi, pdfOptions)
      case Some(targetSv) =>
        val attrs = getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent,
          maybeIcdVersion = icdVersion,
          maybePdfOptions = Some(pdfOptions)
        )
        s"/icdAsPdf/${sv.subsystem}/${targetSv.subsystem}$attrs"
    }
  }

  /**
   * Returns the route to use to get a PDF for the FITS keyword info
   *
   * @param tag "All" for all keywords, otherwise restrict output to given tag, as defined in DMS-Model-Files/FITS-Dictionary
   * @param pdfOptions options for PDF generation
   * @return the URL path to use
   */
  def fitsDictionaryAsPdf(
      tag: String,
      pdfOptions: PdfOptions
  ): String = {
    val attrs = getAttrs(
      maybeVersion = None,
      maybeComponent = None,
      maybePdfOptions = Some(pdfOptions)
    )
    s"/fitsDictionaryAsPdf/$tag$attrs"
  }

  /**
   * Returns the route to use to "Generate" code in the given language.
   * @param sv       the subsystem
   * @param lang     Scala, Java, TypeScript or Python
   * @param className  top level class name (file basename)
   * @param packageName  package name
   * @return the URL path to use
   */
  def generate(
      sv: SubsystemWithVersion,
      lang: String,
      className: String,
      packageName: String
  ): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent,
      maybePackageName = Some(packageName)
    )
    s"/generate/${sv.subsystem}/$lang/$className$attrs"
  }

  /**
   * Returns a FitsDictionary object
   */
  def fitsDictionary(maybeSv: Option[SubsystemWithVersion]): String = {
    if (maybeSv.isDefined) {
      val subsystemAttr = s"subsystem=${maybeSv.get.subsystem}"
      val componentAttr = maybeSv.get.maybeComponent.map(c => s"&component=$c").getOrElse("")
      val attrs         = s"?$subsystemAttr$componentAttr"
      s"/fitsDictionary$attrs"
    }
    else {
      s"/fitsDictionary"
    }
  }

  /**
   * Returns the route to use to get an archived items report for the given Subsystem with selected components.
   *
   * @param sv       the subsystem
   * @return the URL path to use
   */
  def archivedItemsReport(sv: SubsystemWithVersion, options: PdfOptions): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent,
      maybePdfOptions = Some(options)
    )
    s"/archivedItemsReport/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get an archived items report for the given Subsystem with selected components.
   *
   * @param sv       the subsystem
   * @return the URL path to use
   */
  def archivedItemsReportHtml(sv: SubsystemWithVersion): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent
    )
    s"/archivedItemsReportHtml/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get an archived items report for all subsystems
   *
   * @return the URL path to use
   */
  def archivedItemsReportFull(options: PdfOptions): String = {
    val attrs = getAttrs(
      None,
      None,
      maybePdfOptions = Some(options)
    )
    s"/archivedItemsReportFull$attrs"
  }

  /**
   * Returns the route to use to get an alarms report for the given Subsystem with selected components.
   *
   * @param sv the subsystem
   * @return the URL path to use
   */
  def alarmsReport(sv: SubsystemWithVersion, options: PdfOptions): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent,
      maybePdfOptions = Some(options)
    )
    s"/alarmsReport/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get an alarms report for all subsystems
   *
   * @return the URL path to use
   */
  def alarmsReportFull(options: PdfOptions): String = {
    val attrs = getAttrs(
      None,
      None,
      maybePdfOptions = Some(options)
    )
    s"/alarmsReportFull$attrs"
  }

  /**
   * Returns the route to use to get a missing items report for the given Subsystem with selected components.
   * @return the URL path to use
   */
  def missingItemsReport(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion], options: PdfOptions): String = {
    val attrs = maybeTargetSv match {
      case None =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybePdfOptions = Some(options)
        )
      case Some(targetSv) =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent,
          maybeTarget = maybeTargetSv.map(_.subsystem),
          maybePdfOptions = Some(options)
        )
    }
    s"/missingItemsReport/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get a missing items report HTML for the given Subsystem with selected components.
   *
   * @param sv the subsystem
   * @return the URL path to use
   */
  def missingItemsReportHtml(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): String = {
    val attrs = maybeTargetSv match {
      case None =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent
        )
      case Some(targetSv) =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent,
          maybeTarget = maybeTargetSv.map(_.subsystem)
        )
    }
    s"/missingItemsReportHtml/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get a missing items report for all subsystems
   *
   * @return the URL path to use
   */
  def missingItemsReportFull(options: PdfOptions): String = {
    val attrs = getAttrs(
      None,
      None,
      maybePdfOptions = Some(options)
    )
    s"/missingItemsReportFull$attrs"
  }

  /**
   * Returns the route to use to get a PDF of the API for the given Subsystem with selected components.
   *
   * @param sv     the subsystem
   * @param searchAllSubsystems if true, search all subsystems in the database for references, subscribers, etc.
   * @param clientApi if true, include subcribed events and sent commands
   * @param options options for PDF generation
   * @return the URL path to use
   */
  def apiAsPdf(sv: SubsystemWithVersion, searchAllSubsystems: Boolean, clientApi: Boolean, options: PdfOptions): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent,
      maybeSearchAllSubsystems = Some(searchAllSubsystems),
      maybeClientApi = Some(clientApi),
      maybePdfOptions = Some(options)
    )
    s"/apiAsPdf/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to generate a graph of selected component relationships
   *
   * @param sv       the subsystem
   * @param maybeTargetSv defines the optional target subsystem and version
   * @param icdVersion optional ICD version
   * @param options options for graph generation
   * @return the URL path to use
   */
  def makeGraph(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      icdVersion: Option[String],
      options: IcdVizOptions
  ): String = {
    val attrs = maybeTargetSv match {
      case None =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeGraphOptions = Some(options)
        )
      case Some(targetSv) =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent,
          maybeIcdVersion = icdVersion,
          maybeGraphOptions = Some(options),
          maybeTarget = maybeTargetSv.map(_.subsystem)
        )
    }
    s"/makeGraph/${sv.subsystem}$attrs"
  }

  /**
   * Uploads ICD files from a selected directory, all at once as multipart/formdata
   */
  val uploadFiles = "/uploadFiles"

  /**
   * Gets the detailed information about the versions of a component or subsystem
   */
  def versions(name: String) = s"/versions/$name"

  /**
   * Gets a list of version names for a component or subsystem
   */
  def versionNames(name: String) = s"/versionNames/$name"

  /**
   * Gets a list of published ICD names
   */
  val icdNames = "/icdNames"

  /**
   * Gets a list of versions for the ICD from subsystem to target
   */
  def icdVersions(icdName: IcdName) = s"/icdVersions/${icdName.subsystem}/${icdName.target}"

  /**
   * Gets the differences between two versions (version strings separated by a comma)
   */
  def diff(subsystem: String, versions: List[String]) = s"/diff/$subsystem/${versions.mkString(",")}"

  /**
   * Returns OK(true) if this is running as a public server (where uploads are disabled, publish is enabled)
   */
  val isPublicServer = "/isPublicServer"

  /**
   * Gets PublishInfo for every subsystem
   */
  def getPublishInfo(maybeSubsystem: Option[String]): String = {
    maybeSubsystem match {
      case Some(subsystem) => s"/getPublishInfo?subsystem=$subsystem"
      case None            => "/getPublishInfo"
    }
  }

  /**
   * For POST of PublishApiInfo to publish an API
   */
  val publishApi = "/publishApi"

  /**
   * For POST of PublishIcdInfo to publish an ICD
   */
  val publishIcd = "/publishIcd"

  /**
   * For POST of UnpublishApiInfo to unpublish an API
   */
  val unpublishApi = "/unpublishApi"

  /**
   * For POST of UnpublishIcdInfo to unpublish an ICD
   */
  val unpublishIcd = "/unpublishIcd"

  /**
   * Post: Updates the cache of published APIs and ICDs (in case new ones were published)
   */
  val updatePublished = "/updatePublished"

  /**
   *  Post: Checks if the given GitHub user name and password are valid for publishing
   */
  val checkGitHubCredentials = "/checkGitHubCredentials"

  /**
   *  Post: Checks if the given user name and password are valid for using the web app
   */
  val checkCredentials = "/checkCredentials"

  /**
   *  Post: Checks if the user is logged in
   */
  val checkForCookie = "/checkForCookie"

  /**
   * Post: Log out of the web app
   */
  val logout = "/logout"

  /**
   * Post the OpenApi JSON and get the HTML to display
   *
   * @param subsystem the subsystem providing the service
   * @param component the component provideing the service
   * @param service the name of the service
   * @param maybeVersion optional version of the subsystem
   * @param paths list of methods/paths to include in the OpenApi file
   * @return a URL that will return the OpenApi file
   */
  def openApi(
      subsystem: String,
      component: String,
      service: String,
      maybeVersion: Option[String],
      paths: List[ServicePath]
  ): String = {
    val baseUrl    = s"/openApi/$subsystem/$component/$service"
    val versionArg = maybeVersion.map(v => s"version=$v")
    val pathsStr   = paths.map(p => s"${p.method}:${p.path}").mkString(",")
    val pathsArg   = if (paths.nonEmpty) Some(s"paths=$pathsStr") else None
    val args       = List(versionArg, pathsArg).flatten.mkString("&")
    val url        = if (args.isEmpty) baseUrl else s"$baseUrl?$args"
    URLEncoder.encode(url, "UTF-8")
  }

  /**
   * Returns the route to use to get the data for an events hostogram for the given Subsystems with selected components.
   *
   * @return the URL path to use
   */
  def eventsHistogram(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): String = {
    val attrs = maybeTargetSv match {
      case None =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent
        )
      case Some(targetSv) =>
        getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          maybeTargetVersion = targetSv.maybeVersion,
          maybeTargetCompName = targetSv.maybeComponent,
          maybeTarget = maybeTargetSv.map(_.subsystem)
        )
    }
    s"/eventsHistogram/${sv.subsystem}$attrs"
  }

}
