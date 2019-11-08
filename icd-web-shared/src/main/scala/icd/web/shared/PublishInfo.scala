package icd.web.shared

/**
* Holds publish related information for a subsystem.
 *
 * @param subsystem the subsystem name
 * @param maybeApiVersionInfo the latest published version of the subsystem, or None if there are no published versions
 * @param icdVersions list of published ICDs involving the latest API version
 * @param readyToPublish true if there are unpublished changes to the subsystem's model files
 */
case class PublishInfo(
    subsystem: String,
    maybeApiVersionInfo: Option[ApiVersionInfo],
    icdVersions: List[IcdVersionInfo],
    readyToPublish: Boolean
)
