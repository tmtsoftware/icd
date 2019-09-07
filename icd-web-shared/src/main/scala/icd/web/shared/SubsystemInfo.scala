package icd.web.shared

object SubsystemInfo {

}

/**
 * Holds information about a subsystem
 *
 * @param sv          the subsystem
 * @param title       the subsystem title
 * @param description the subsystem description as HTML (after markdown processing)
 */
case class SubsystemInfo(sv: SubsystemWithVersion, title: String, description: String)

/**
 * Holds a subsystem and an optional version and component name
 *
 * @param subsystem    the selected subsystem
 * @param maybeVersion optional version of the subsystem (None means the latest unpublished version)
 */
case class SubsystemWithVersion(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String])

