package icd.web.client

import icd.web.shared.{IcdName, PdfOptions, SubsystemWithVersion}

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
      maybeTargetVersion: Option[String] = None,
      maybeTargetCompName: Option[String] = None,
      maybeIcdVersion: Option[String] = None,
      maybePdfOptions: Option[PdfOptions] = None
  ): String = {
    val versionAttr         = maybeVersion.map(v => s"version=$v")
    val componentAttr       = maybeComponent.map(c => s"component=$c")
    val searchAllAttr       = maybeSearchAllSubsystems.map(b => s"searchAll=$b")
    val targetVersionAttr   = maybeTargetVersion.map(v => s"targetVersion=$v")
    val targetComponentAttr = maybeTargetCompName.map(c => s"targetComponent=$c")
    val icdVersionAttr      = maybeIcdVersion.map(v => s"icdVersion=$v")
    val pdfAttrs = maybePdfOptions.map(
      o =>
        List(
          s"orientation=${o.orientation}",
          s"fontSize=${o.fontSize}",
          s"lineHeight=${o.lineHeight}",
          s"paperSize=${o.paperSize}",
          s"details=${o.details}"
        ).mkString("&")
    )
    val attrs =
      (versionAttr ++ componentAttr ++ searchAllAttr ++ targetVersionAttr ++ targetComponentAttr ++ icdVersionAttr ++ pdfAttrs)
        .mkString("&")
    if (attrs.isEmpty) "" else s"?$attrs"
  }

  /**
   * Gets top level information about a given version of the given subsystem
   */
  def subsystemInfo(subsystem: String, maybeVersion: Option[String]): String = maybeVersion match {
    case Some("*") | None => s"/subsystemInfo/$subsystem"
    case Some(version)    => s"/subsystemInfo/$subsystem?version=$version"
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, maybeVersion: Option[String]): String = maybeVersion match {
    case Some("*") | None => s"/components/$subsystem"
    case Some(version)    => s"/components/$subsystem?version=$version"
  }

  /**
   * Returns the route to use to get the information for a component
   *
   * @param sv      the subsystem
   * @return the URL path to use
   */
  def componentInfo(sv: SubsystemWithVersion, searchAllSubsystems: Boolean): String = {
    val attrs = getAttrs(sv.maybeVersion, sv.maybeComponent, Some(searchAllSubsystems))
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
   * @return the URL path to use
   */
  def icdComponentInfo(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean
  ): String = {
    maybeTargetSv match {
      case None => componentInfo(sv, searchAllSubsystems)
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
   * @param pdfOptions options for PDF generation
   * @return the URL path to use
   */
  def icdAsPdf(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      icdVersion: Option[String],
      searchAllSubsystems: Boolean,
      pdfOptions: PdfOptions
  ): String = {
    maybeTargetSv match {
      case None => apiAsPdf(sv, searchAllSubsystems, pdfOptions)
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
   * Returns the route to use to get a PDF of the API for the given Subsystem with selected components.
   *
   * @param sv     the subsystem
   * @param searchAllSubsystems if true, search all subsystems in the database for references, subscribers, etc.
   * @param options options for PDF generation
   * @return the URL path to use
   */
  def apiAsPdf(sv: SubsystemWithVersion, searchAllSubsystems: Boolean, options: PdfOptions): String = {
    val attrs = getAttrs(
      sv.maybeVersion,
      sv.maybeComponent,
      maybeSearchAllSubsystems = Some(searchAllSubsystems),
      maybePdfOptions = Some(options)
    )
    s"/apiAsPdf/${sv.subsystem}$attrs"
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
}
