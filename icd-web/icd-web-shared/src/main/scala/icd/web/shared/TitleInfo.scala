package icd.web.shared

/**
 * Holds title related information for a subsystem
 * @param title the title
 * @param subtitleOpt optional subtitle
 * @param descriptionOpt optional description
 */
case class TitleInfo(title: String, subtitleOpt: Option[String], descriptionOpt: Option[String])

object TitleInfo {
  /**
   * Displayed version for unpublished APIs
   */
  val unpublished = "(unpublished)"

  /**
   * Gets the title and optional subtitle to display based on the given source and target subsystems
   * @param subsystemInfo describes the source subsystem
   * @param targetSubsystem the optional target subsystem and version
   * @param icdOpt optional ICD related information
   * @return the title related information
   */
  def apply(subsystemInfo: SubsystemInfo,
            targetSubsystem: SubsystemWithVersion,
            icdOpt: Option[IcdVersion]): TitleInfo = {
    if (icdOpt.isDefined) {
      val icd = icdOpt.get
      val title = s"ICD from ${icd.subsystem} to ${icd.target} (version ${icd.icdVersion})"
      val subtitle = s"Based on ${icd.subsystem} ${icd.subsystemVersion} and ${icd.target} ${icd.targetVersion}"
      TitleInfo(title, Some(subtitle), None)
    } else {
      val version = subsystemInfo.versionOpt.getOrElse(unpublished)
      if (targetSubsystem.subsystemOpt.isDefined) {
        val target = targetSubsystem.subsystemOpt.get
        val targetVersion = targetSubsystem.versionOpt.getOrElse(unpublished)
        val title = s"ICD from ${subsystemInfo.subsystem} to $target $unpublished"
        val subtitle = s"Based on ${subsystemInfo.subsystem} $version and $target $targetVersion"
        TitleInfo(title, Some(subtitle), None)
      } else {
        TitleInfo(s"API for ${subsystemInfo.subsystem} $version",
          Some(subsystemInfo.title), Some(subsystemInfo.htmlDescription))
      }
    }
  }
}
