package icd.web.client

import icd.web.shared.{SubsystemWithVersion, IcdName}

/**
 * Defines URI routes to access the server API
 * (See icd-web-server/conf/routes file for server side)
 */
object Routes {

  /**
   * Gets a list of top level subsystem names
   */
  val subsystems = "/subsystems"

  // Return the attributes string based on the options
  private def getAttrs(
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAllSubsystems: Boolean,
      maybeTargetVersion: Option[String] = None,
      maybeTargetCompName: Option[String] = None,
      maybeIcdVersion: Option[String] = None
  ): String = {
    val versionAttr         = maybeVersion.map(v => s"version=$v")
    val componentAttr       = maybeComponent.map(c => s"component=$c")
    val searchAllAttr       = if (searchAllSubsystems) Some("searchAll=true") else None
    val targetVersionAttr   = maybeTargetVersion.map(v => s"tagetVersion=$v")
    val targetComponentAttr = maybeTargetCompName.map(c => s"targetComponent=$c")
    val icdVersionAttr      = maybeIcdVersion.map(v => s"icdVersion=$v")
    val attrs =
      (versionAttr ++ componentAttr ++ searchAllAttr ++ targetVersionAttr ++ targetComponentAttr ++ icdVersionAttr).mkString("&")
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
    val attrs = getAttrs(sv.maybeVersion, sv.maybeComponent, searchAllSubsystems)
    s"/componentInfo/${sv.subsystem}$attrs"
  }

  /**
   * Returns the route to use to get the information for a component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param sv       the subsystem
   * @param maybeTargetSv defines the optional target subsystem and version
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
          searchAllSubsystems = false,
          targetSv.maybeVersion,
          targetSv.maybeComponent
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
   * @return the URL path to use
   */
  def icdAsPdf(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      icdVersion: Option[String],
      searchAllSubsystems: Boolean
  ): String = {
    maybeTargetSv match {
      case None => apiAsPdf(sv, searchAllSubsystems)
      case Some(targetSv) =>
        val attrs = getAttrs(
          sv.maybeVersion,
          sv.maybeComponent,
          searchAllSubsystems = false,
          targetSv.maybeVersion,
          targetSv.maybeComponent,
          icdVersion
        )
        s"/icdAsPdf/${sv.subsystem}/${targetSv.subsystem}$attrs"
    }
  }

  /**
   * Returns the route to use to get a PDF of the API for the given Subsystem with selected components.
   *
   * @param sv     the subsystem
   * @return the URL path to use
   */
  def apiAsPdf(sv: SubsystemWithVersion, searchAllSubsystems: Boolean): String = {
    val attrs = getAttrs(sv.maybeVersion, sv.maybeComponent, searchAllSubsystems)
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
   * Returns OK(true) if Uploads should be allowed in the web app
   */
  def isUploadAllowed = "/isUploadAllowed"

  /**
   * Gets PublishInfo for every subsystem
   */
  def getPublishInfo = "/getPublishInfo"
}
