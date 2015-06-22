package shared

/**
 * An ICD from subsystem to target subsystem
 */
case class IcdName(subsystem: String, target: String) {
  override def toString = s"$subsystem to $target"
}

/**
 * An ICD version with the associated source and target subsystem versions
 */
case class IcdVersion(icdVersion: String,
                      subsystem: String, subsystemVersion: String,
                      target: String, targetVersion: String)
