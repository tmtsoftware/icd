package icd.web.shared

/**
 * Holds information about a subsystem
 *
 * @param sv          the subsystem
 * @param title       the subsystem title
 * @param description the subsystem description as HTML (after markdown processing)
 */
case class SubsystemInfo(sv: SubsystemWithVersion, title: String, description: String)

object SubsystemWithVersion {

  /**
   * Initialize from a string in the form: subsystem.component:version,
   * where ".component" and ":version" are optional.
   */
  def apply(s: String):  SubsystemWithVersion = {
    val (ss, maybeVersion) = if (s.contains(':')) {
      val ar = s.split(':')
      (ar.head, Some(ar.tail.head))
    } else {
      (s, None)
    }
    val (subsystem, maybeComponent) = if (ss.contains('.')) {
      val ar = ss.split('.')
      (ar.head, Some(ar.tail.mkString(".")))
    } else {
      (ss, None)
    }
    new SubsystemWithVersion(subsystem, maybeVersion, maybeComponent)
  }
}

/**
 * Holds a subsystem and an optional version and component name
 *
 * @param subsystem    the selected subsystem
 * @param maybeVersion optional version of the subsystem (None means the latest unpublished version)
 */
case class SubsystemWithVersion(subsystem: String, maybeVersion: Option[String], maybeComponent: Option[String]) {
  override def toString: String = {
    val compStr = maybeComponent.map(c => s".$c").getOrElse("")
    val versionStr = maybeVersion.map(v => s"-$v").getOrElse("")
    s"$subsystem$compStr$versionStr"
  }
}
