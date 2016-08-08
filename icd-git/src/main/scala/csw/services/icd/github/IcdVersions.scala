package csw.services.icd.github

import icd.web.shared._
import spray.json._


object IcdVersions extends DefaultJsonProtocol {
  implicit val icdVersionFormat = jsonFormat5(IcdVersion.apply)
  implicit val icdVersionInfoFormat = jsonFormat4(IcdVersionInfo.apply)
  implicit val icdVersionsFormat = jsonFormat1(IcdVersions.apply)

  def fromJson(s: String): IcdVersions = icdVersionsFormat.read(s.parseJson)
}

/**
  * Holds a list describing ICD versions
  */
case class IcdVersions(icds: List[IcdVersionInfo])
