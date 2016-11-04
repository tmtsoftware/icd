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
   * @param part Optional string inserted before the title
   * @return the title related information
   */
  def apply(
    subsystemInfo:   SubsystemInfo,
    targetSubsystem: SubsystemWithVersion,
    icdOpt:          Option[IcdVersion],
    part:            String = ""
  ): TitleInfo = {
    val subsystemName = subsystemInfo.subsystem
    val targetName = targetSubsystem.subsystemOpt.getOrElse("")
    if (icdOpt.isDefined) {
      val icd = icdOpt.get

      val title = if (part.nonEmpty)
        s"ICD $part $subsystemName -> $targetName (version ${icd.icdVersion})"
      else
        s"ICD $subsystemName / $targetName (version ${icd.icdVersion})"

      val subtitle = s"Based on $subsystemName ${subsystemInfo.versionOpt.get} and $targetName ${targetSubsystem.versionOpt.get}"
      TitleInfo(title, Some(subtitle), None)
    } else {
      val version = subsystemInfo.versionOpt.getOrElse(unpublished)
      if (targetSubsystem.subsystemOpt.isDefined) {
        val targetVersion = targetSubsystem.versionOpt.getOrElse(unpublished)
        val title = s"ICD $part $subsystemName -> $targetName $unpublished"
        val subtitle = s"Based on $subsystemName $version and $targetName $targetVersion"
        TitleInfo(title, Some(subtitle), None)
      } else {
        TitleInfo(
          s"API for $subsystemName $version",
          Some(subsystemInfo.title), Some(subsystemInfo.description)
        )
      }
    }
  }
}
