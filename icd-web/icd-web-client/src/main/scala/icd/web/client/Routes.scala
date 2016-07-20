package icd.web.client

import icd.web.shared.{SubsystemWithVersion, IcdName}

/**
 * Defines URI routes to access the server API
 * (See icd-web-server/conf/routes file for server side)
 */
object Routes {
  // XXX TODO: Need a URI builder for scala.js (last I checked, scala-uri did not work here)

  val subsystems = "/subsystems"

  def subsystemInfo(subsystem: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None => s"/subsystemInfo/$subsystem"
    case Some(version)    => s"/subsystemInfo/$subsystem?version=$version"
  }

  def components(subsystem: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None => s"/components/$subsystem"
    case Some(version)    => s"/components/$subsystem?version=$version"
  }

  /**
   * Returns the route to use to get the information for a component
   *
   * @param subsystem    the component's subsystem
   * @param versionOpt   the subsystem version (or use current)
   * @param compNameList list of component names to get info about
   * @return the URL path to use
   */
  def componentInfo(subsystem: String, versionOpt: Option[String], compNameList: List[String]) = {
    val compNames = compNameList.mkString(",")
    versionOpt match {
      case Some("*") | None => s"/componentInfo/$subsystem/$compNames"
      case Some(version)    => s"/componentInfo/$subsystem/$compNames?version=$version"
    }
  }

  /**
   * Returns the route to use to get the information for a component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param subsystem       the component's subsystem
   * @param versionOpt      the subsystem version (or use current)
   * @param compNameList    list of component names to get info about
   * @param targetSubsystem defines the optional target subsystem and version
   * @return the URL path to use
   */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compNameList: List[String],
                       targetSubsystem: SubsystemWithVersion) = {
    targetSubsystem.subsystemOpt match {
      case None => componentInfo(subsystem, versionOpt, compNameList)
      case Some(target) =>
        val compNames = compNameList.mkString(",")
        val path = s"/icdComponentInfo/$subsystem/$compNames/$target"
        val targetVersionOpt = targetSubsystem.versionOpt
        versionOpt match {
          case Some("*") | None =>
            targetVersionOpt match {
              case Some("*") | None    => path
              case Some(targetVersion) => s"$path?targetVersion=$targetVersion"
            }
          case Some(version) =>
            targetVersionOpt match {
              case Some(targetVersion) => s"$path?version=$version&targetVersion=$targetVersion"
              case Some("*") | None    => s"$path?version=$version"
            }
        }
    }
  }

  /**
   * Returns the route to use to get a PDF for the given ICD or Subsystem with selected components.
   * If the target subsystem is defined, the document is restricted to the ICD
   * from subsystem to target, otherwise the API for the given subsystem and components is returned.
   *
   * @param subsystem       the component's subsystem
   * @param versionOpt      the subsystem version (or use current)
   * @param compNamesList   the component names to include
   * @param targetSubsystem defines the optional target subsystem and version
   * @param icdVersion      optional ICD version (default: use latest unpublished)
   * @return the URL path to use
   */
  def icdAsPdf(subsystem: String, versionOpt: Option[String],
               compNamesList:   List[String],
               targetSubsystem: SubsystemWithVersion,
               icdVersion:      Option[String]) = {
    targetSubsystem.subsystemOpt match {
      case None => apiAsPdf(subsystem, versionOpt, compNamesList)
      case Some(target) =>
        val path = compNamesList match {
          case Nil =>
            s"/icdAsPdf/$subsystem/$target?"
          case list =>
            val compNames = list.mkString(",")
            s"/icdAsPdf/$subsystem/$target?compNames=$compNames"
        }
        val targetVersionOpt = targetSubsystem.versionOpt
        val path2 = versionOpt match {
          case Some("*") | None =>
            targetVersionOpt match {
              case Some("*") | None    => path
              case Some(targetVersion) => s"$path&targetVersion=$targetVersion"
            }
          case Some(version) =>
            targetVersionOpt match {
              case Some(targetVersion) => s"$path&version=$version&targetVersion=$targetVersion"
              case Some("*") | None    => s"$path&version=$version"
            }
        }
        icdVersion match {
          case Some(v) => s"$path2&icdVersion=$v"
          case None    => path2
        }
    }
  }

  /**
   * Returns the route to use to get a PDF of the API for the given Subsystem with selected components.
   *
   * @param subsystem     the component's subsystem
   * @param versionOpt    the subsystem version (or use current)
   * @param compNamesList the component names to include
   * @return the URL path to use
   */
  def apiAsPdf(subsystem: String, versionOpt: Option[String], compNamesList: List[String]) = {
    compNamesList match {
      case Nil =>
        versionOpt match {
          case Some("*") | None => s"/apiAsPdf/$subsystem"
          case Some(version)    => s"/apiAsPdf/$subsystem?version=$version"
        }
      case list =>
        val compNames = list.mkString(",")
        versionOpt match {
          case Some("*") | None => s"/apiAsPdf/$subsystem?compNames=$compNames"
          case Some(version)    => s"/apiAsPdf/$subsystem?version=$version&compNames=$compNames"
        }
    }
  }

  val uploadFiles = "/uploadFiles"

  def versions(name: String) = s"/versions/$name"

  def versionNames(name: String) = s"/versionNames/$name"

  def publishApi(path: String, majorVersion: Boolean, comment: String, userName: String) =
    s"/publishApi/$path?majorVersion=$majorVersion&comment=$comment&userName=$userName"

  def publishIcd(subsystem: String, version: String,
                 target: String, targetVersion: String,
                 majorVersion: Boolean, comment: String, userName: String) =
    s"/publishIcd/$subsystem/$version/$target/$targetVersion?majorVersion=$majorVersion&comment=$comment&userName=$userName"

  val icdNames = "/icdNames"

  def icdVersions(icdName: IcdName) = s"/icdVersions/${icdName.subsystem}/${icdName.target}"

  def diff(subsystem: String, versions: List[String]) = s"/diff/$subsystem/${versions.mkString(",")}"
}
