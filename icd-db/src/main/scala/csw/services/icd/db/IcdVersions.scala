package csw.services.icd.db

import play.api.libs.json._

/**
 * These definitions determine the JSON format of the files recording the ICD version information.
 * (Be careful to make any changes backward compatible, once in production!)
 */
object IcdVersions {

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
  implicit val icdEntryFormat: OFormat[IcdEntry]       = Json.format[IcdEntry]
  implicit val icdVersionsFormat: OFormat[IcdVersions] = Json.format[IcdVersions]

  /**
   * Creates an IcdVersions object from a string in JSON format
   */
  def fromJson(s: String): IcdVersions = Json.fromJson[IcdVersions](Json.parse(s)).get

//  // Define sorting for IcdVersions
//  implicit def ordering[A <: IcdVersions]: Ordering[A] = Ordering.by(e => e.subsystems.head)
}

/**
 * Holds a list describing ICD versions
 */
case class IcdVersions(subsystems: List[String], icds: List[IcdVersions.IcdEntry]) extends Ordered[IcdVersions] {
  override def compare(that: IcdVersions): Int = {
    subsystems.head.compare(that.subsystems.head)
  }
}

/**
 * These definitions determine the JSON format of the files recording the subsystem API version information.
 * (Be careful to make any changes backward compatible, once in production!)
 */
object ApiVersions {

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
  implicit val apiEntryFormat: OFormat[ApiEntry]       = Json.format[ApiEntry]
  implicit val apiVersionsFormat: OFormat[ApiVersions] = Json.format[ApiVersions]

  /**
   * Creates an IcdVersions object from a string in JSON format
   */
  def fromJson(s: String): ApiVersions = Json.fromJson[ApiVersions](Json.parse(s)).get

//  // Define sorting for ApiVersions
//  implicit def ordering[A <: ApiVersions]: Ordering[A] = Ordering.by(e => e.subsystem)
}

/**
 * Holds a list describing subsystem API versions
 */
case class ApiVersions(subsystem: String, apis: List[ApiVersions.ApiEntry]) extends Ordered[ApiVersions] {
  override def compare(that: ApiVersions): Int = {
    subsystem.compare(that.subsystem)
  }
}
