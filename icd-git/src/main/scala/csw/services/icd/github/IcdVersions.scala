package csw.services.icd.github

import spray.json._

/**
  * These definitions determine the JSON format of the files recording the ICD version information.
  * (Be careful to make any changes backward compatible, once in production!)
  */
object IcdVersions extends DefaultJsonProtocol {
  case class IcdEntry(icdVersion: String, versions: List[String], user: String, comment: String, date: String)
  implicit val icdEntryFormat = jsonFormat5(IcdEntry.apply)
  implicit val icdVersionsFormat = jsonFormat2(IcdVersions.apply)

  def fromJson(s: String): IcdVersions = icdVersionsFormat.read(s.parseJson)
}

/**
  * Holds a list describing ICD versions
  */
case class IcdVersions(subsystems: List[String], icds: List[IcdVersions.IcdEntry])

