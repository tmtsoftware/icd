package csw.services.icd.db

import spray.json._

/**
 * These definitions determine the JSON format of the files recording the ICD version information.
 * (Be careful to make any changes backward compatible, once in production!)
 */
object IcdVersions extends DefaultJsonProtocol {

  /**
   * Describes a single ICD version between two subsystems
   *
   * @param icdVersion the version of the ICD
   * @param versions   the versions of the two subsystems
   * @param user       the user that made the version
   * @param comment    a comment describing the version
   * @param date       the date the version was made
   */
  case class IcdEntry(icdVersion: String, versions: List[String], user: String, comment: String, date: String)

  // JSON support
  implicit val icdEntryFormat: RootJsonFormat[IcdEntry]       = jsonFormat5(IcdEntry.apply)
  implicit val icdVersionsFormat: RootJsonFormat[IcdVersions] = jsonFormat2(IcdVersions.apply)

  /**
   * Creates an IcdVersions object from a string in JSON format
   */
  def fromJson(s: String): IcdVersions = icdVersionsFormat.read(s.parseJson)
}

/**
 * Holds a list describing ICD versions
 */
case class IcdVersions(subsystems: List[String], icds: List[IcdVersions.IcdEntry])

/**
 * These definitions determine the JSON format of the files recording the subsystem API version information.
 * (Be careful to make any changes backward compatible, once in production!)
 */
object ApiVersions extends DefaultJsonProtocol {

  /**
   * Describes a single subsystem API version
   *
   * @param version the version of the subsystem API
   * @param commit  the git commit id to use for this version
   * @param user    the user that made the version
   * @param comment a comment describing the version
   * @param date    the date the version was made
   */
  case class ApiEntry(version: String, commit: String, user: String, comment: String, date: String)

  // JSON support
  implicit val apiEntryFormat: RootJsonFormat[ApiEntry]       = jsonFormat5(ApiEntry.apply)
  implicit val apiVersionsFormat: RootJsonFormat[ApiVersions] = jsonFormat2(ApiVersions.apply)

  /**
   * Creates an IcdVersions object from a string in JSON format
   */
  def fromJson(s: String): ApiVersions = apiVersionsFormat.read(s.parseJson)
}

/**
 * Holds a list describing subsystem API versions
 */
case class ApiVersions(subsystem: String, apis: List[ApiVersions.ApiEntry])
