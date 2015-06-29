package shared

/**
 * Holds information about a subsystem
 */
case class SubsystemInfo(subsystem: String, versionOpt: Option[String], title: String, description: String)
