package icd.web.shared

/**
 * Describes a version of a subsystem
 * @param version the subsystem version (major.minor), or None for the unpublished, working version
 * @param user the user that created the version
 * @param comment a change comment
 * @param date the date of the change, formatted for display
 */
case class VersionInfo(version: Option[String], user: String, comment: String, date: String)

/**
 * Describes a single change
 */
case class Diff(key: String, value: String)

/**
 * Describes the difference between two subsystem versions
 * @param pointer pointer down to the nested json part that changed
 * @param changes a list of operation name (add, remove, move, remove, etc.) and the part that changed
 */
case class DiffItem(pointer: List[String], changes: List[Diff])

/**
 * Top level diff of two subsystem versions
 * @param path path to the top level part that changed (subsystem.component.publish, for example)
 * @param jsonDiff JsonDiff formatted JSON (See https://github.com/gnieh/diffson)
 */
case class DiffInfo(path: String, jsonDiff: String)
