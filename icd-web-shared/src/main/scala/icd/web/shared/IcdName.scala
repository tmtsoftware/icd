package icd.web.shared

/**
 * An ICD from subsystem to target subsystem
 */
case class IcdName(subsystem: String, target: String) {
  override def toString = s"$subsystem / $target"
}

/**
 * An ICD version with the associated source and target subsystem versions
 */
case class IcdVersion(
  icdVersion: String,
  subsystem:  String, subsystemVersion: String,
  target: String, targetVersion: String
)

/**
 * An ICD version with additional history information
 * @param icdVersion describes the ICD version
 * @param user the user that published the version
 * @param comment the publish comment
 * @param date the date the ICD was published
 */
case class IcdVersionInfo(icdVersion: IcdVersion, user: String, comment: String, date: String)

/**
 * A subsystem API version with additional history information
 * @param subsystem the subsystem name
 * @param version the subsystem version
 * @param user the user that published the version
 * @param comment the publish comment
 * @param date the date the ICD was published
 */
case class ApiVersionInfo(subsystem: String, version: String, user: String, comment: String, date: String)
