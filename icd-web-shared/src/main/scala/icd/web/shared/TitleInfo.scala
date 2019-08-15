package icd.web.shared

/**
 * Holds title related information for a subsystem
 *
 * @param title          the title
 * @param subtitleOpt    optional subtitle
 * @param descriptionOpt optional description
 */
case class TitleInfo(title: String, subtitleOpt: Option[String], descriptionOpt: Option[String])

object TitleInfo {
  /**
   * Displayed version for unpublished APIs
   */
  val unpublished = "(unpublished)"

  private def getSubtitle(subsystemName: String, subsystemVersion: String,
                          targetName: String, targetVersion: String): String = {
    if (subsystemName == targetName)
      s"Based on $subsystemName $subsystemVersion"
    else
      s"Based on $subsystemName $subsystemVersion and $targetName $targetVersion"
  }

  /**
   * Gets the title and optional subtitle to display based on the given source and target subsystems
   *
   * @param subsystemInfo   describes the source subsystem
   * @param targetSubsystem the optional target subsystem and version
   * @param icdOpt          optional ICD related information
   * @param component       optional subsystem component (restrict output to parts related to this component)
   * @param targetComponent optional target subsystem component (restrict output to parts related to this component)
   * @param part            Optional string inserted before the title
   * @return the title related information
   */
  def apply(
             subsystemInfo: SubsystemInfo,
             targetSubsystem: SubsystemWithVersion,
             icdOpt: Option[IcdVersion],
             component: Option[String],
             targetComponent: Option[String],
             part: String = ""
           ): TitleInfo = {
    val subsystemName = subsystemInfo.subsystem
    val targetName = targetSubsystem.subsystemOpt.getOrElse("")
    val componentPart = component.map("." + _).getOrElse("")
    val targetComponentPart = targetComponent.map("." + _).getOrElse("")
    if (icdOpt.isDefined) {
      val icd = icdOpt.get

      val title = if (part.nonEmpty)
        s"ICD $part $subsystemName$componentPart -> $targetName$targetComponentPart (version ${icd.icdVersion})"
      else
        s"ICD $subsystemName$componentPart / $targetName$targetComponentPart (version ${icd.icdVersion})"

      val subtitle = getSubtitle(subsystemName, subsystemInfo.versionOpt.get, targetName, targetSubsystem.versionOpt.get)
      TitleInfo(title, Some(subtitle), None)
    } else {
      val version = subsystemInfo.versionOpt.getOrElse(unpublished)
      if (targetSubsystem.subsystemOpt.isDefined) {
        val targetVersion = targetSubsystem.versionOpt.getOrElse(unpublished)
        val title = s"ICD $part $subsystemName$componentPart -> $targetName$targetComponentPart $unpublished"
        val subtitle = getSubtitle(subsystemName, version, targetName, targetVersion)
        TitleInfo(title, Some(subtitle), None)
      } else {
        TitleInfo(
          s"API for $subsystemName$componentPart $version",
          Some(subsystemInfo.title), Some(subsystemInfo.description)
        )
      }
    }
  }
}
