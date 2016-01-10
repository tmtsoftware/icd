package icd.web.shared


object SubsystemInfo {

}

/**
  * Holds information about a subsystem
  *
  * @param subsystem       the subsystem
  * @param versionOpt      optional version (default: latest)
  * @param title           the subsystem title
  * @param description     the subsystem description (may contain markdown formatting)
  * @param htmlDescription the subsystem description as HTML (after markdown processing)
  */
case class SubsystemInfo(subsystem: String, versionOpt: Option[String], title: String,
                         description: String, htmlDescription: String)

/**
  * Holds an optional subsystem and optional subsystem version
  *
  * @param subsystemOpt the selected subsystem, or None if no subsystem is selected
  * @param versionOpt   optional version of the subsystem (None means the latest version)
  */
case class SubsystemWithVersion(subsystemOpt: Option[String], versionOpt: Option[String])


