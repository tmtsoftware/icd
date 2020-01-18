package icd.web.shared

/**
 * Holds publish related information for a subsystem.
 *
 * @param subsystem the subsystem name
 * @param apiVersions list of published API versions for this subsystem
 * @param icdVersions list of published ICDs involving the latest API version
 * @param readyToPublish true if there are unpublished changes to the subsystem's model files
 */
case class PublishInfo(
    subsystem: String,
    apiVersions: List[ApiVersionInfo],
    icdVersions: List[IcdVersionInfo],
    readyToPublish: Boolean
)

/**
 * Holds the credentials needed to publish a subsystem API or ICD
 *
 * @param user GitHub user name
 * @param password GitHub password
 */
case class GitHubCredentials(user: String, password: String)

/**
 * Holds the information needed to publish a subsystem API
 *
 * @param subsystem the subsystem to publish
 * @param majorVersion if true, increment the major version
 * @param user GitHub user name
 * @param password GitHub password
 * @param comment Comment for the new API version
 */
case class PublishApiInfo(subsystem: String, majorVersion: Boolean, user: String, password: String, comment: String)

/**
 * Holds the information needed to publish an ICD
 *
 * @param subsystem the first subsystem in the ICD
 * @param subsystemVersion the version of the first subsystem to use
 * @param target the second subsystem in the ICD
 * @param targetVersion the version of the second subsystem to use
 * @param majorVersion if true, increment the major version
 * @param user GitHub user name
 * @param password GitHub password
 * @param comment Comment for the new API version
 */
case class PublishIcdInfo(
    subsystem: String,
    subsystemVersion: String,
    target: String,
    targetVersion: String,
    majorVersion: Boolean,
    user: String,
    password: String,
    comment: String
)
