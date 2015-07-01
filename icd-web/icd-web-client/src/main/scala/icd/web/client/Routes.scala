package icd.web.client

import icd.web.client.Subsystem.SubsystemWithVersion
import shared.IcdName

// XXX TODO: Pass settings from server, see ChatJS.main() for example

/**
 * Defines URI routes to access the server API
 * (See icd-web-server/conf/routes file for server side)
 */
object Routes {
  val subsystems = "/subsystems"

  def subsystemInfo(subsystem: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None ⇒ s"/subsystemInfo/$subsystem"
    case Some(version)    ⇒ s"/subsystemInfo/$subsystem?version=$version"
  }

  def components(subsystem: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None ⇒ s"/components/$subsystem"
    case Some(version)    ⇒ s"/components/$subsystem?version=$version"
  }

  /**
   * Returns the route to use to get the information for a component
   * @param subsystem the component's subsystem
   * @param versionOpt the subsystem version (or use current)
   * @param compName the component name
   * @return the URL path to use
   */
  def componentInfo(subsystem: String, versionOpt: Option[String], compName: String) = versionOpt match {
    case Some("*") | None ⇒ s"/componentInfo/$subsystem/$compName"
    case Some(version)    ⇒ s"/componentInfo/$subsystem/$compName?version=$version"
  }

  /**
   * Returns the route to use to get the information for a component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param subsystem the component's subsystem
   * @param versionOpt the subsystem version (or use current)
   * @param compName the component name
   * @param targetSubsystem defines the optional target subsystem and version
   * @return the URL path to use
   */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compName: String,
                       targetSubsystem: SubsystemWithVersion) = {
    targetSubsystem.subsystemOpt match {
      case None ⇒ componentInfo(subsystem, versionOpt, compName)
      case Some(target) ⇒
        val path = s"/icdComponentInfo/$subsystem/$compName/$target"
        val targetVersionOpt = targetSubsystem.versionOpt
        versionOpt match {
          case Some("*") | None ⇒
            targetVersionOpt match {
              case Some("*") | None    ⇒ path
              case Some(targetVersion) ⇒ s"$path?targetVersion=$targetVersion"
            }
          case Some(version) ⇒
            targetVersionOpt match {
              case Some(targetVersion) ⇒ s"$path?version=$version&targetVersion=$targetVersion"
              case Some("*") | None    ⇒ s"$path?version=$version"
            }
        }
    }
  }

  //  def apiAsHtml(name: String) = s"/apiAsHtml/$name"
  //  def apiAsPdf(name: String) = s"/apiAsPdf/$name"

  val uploadFiles = "/uploadFiles"

  def versions(name: String) = s"/versions/$name"

  def versionNames(name: String) = s"/versionNames/$name"

  def publishApi(path: String, majorVersion: Boolean, comment: String) =
    s"/publishApi/$path?majorVersion=$majorVersion&comment=$comment"

  def publishIcd(subsystem: String, version: String,
                 target: String, targetVersion: String,
                 majorVersion: Boolean, comment: String) =
    s"/publishIcd/$subsystem/$version/$target/$targetVersion?majorVersion=$majorVersion&comment=$comment"

  val icdNames = "/icdNames"

  def icdVersions(icdName: IcdName) = s"/icdVersions/${icdName.subsystem}/${icdName.target}"
}
