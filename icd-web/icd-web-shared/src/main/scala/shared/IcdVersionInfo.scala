package shared

/**
 * Describes a version of an ICD
 * @param version the ICD version (major.minor), or None for the unpublished, working version
 * @param user the user that created the version
 * @param comment a change comment
 * @param date the date of the change, formatted for display
 */
case class IcdVersionInfo(version: Option[String], user: String, comment: String, date: String)
