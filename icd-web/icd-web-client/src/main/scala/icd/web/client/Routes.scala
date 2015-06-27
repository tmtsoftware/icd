package icd.web.client

import shared.IcdName

// XXX TODO: Pass settings from server, see ChatJS.main() for example

/**
 * Defines URI routes to access the server API
 * (See icd-web-server/conf/routes file for server side)
 */
object Routes {
  val subsystems = "/subsystems"

  def components(subsystem: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None ⇒ s"/components/$subsystem"
    case Some(version)    ⇒ s"/components/$subsystem?version=$version"
  }

  def componentInfo(subsystem: String, compName: String, versionOpt: Option[String]) = versionOpt match {
    case Some("*") | None ⇒ s"/componentInfo/$subsystem/$compName"
    case Some(version)    ⇒ s"/componentInfo/$subsystem/$compName?version=$version"
  }

  def apiAsHtml(name: String) = s"/apiAsHtml/$name"
  def apiAsPdf(name: String) = s"/apiAsPdf/$name"

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
