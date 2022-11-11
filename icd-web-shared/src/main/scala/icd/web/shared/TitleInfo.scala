package icd.web.shared

/**
 * Holds title related information for a subsystem
 *
 * @param title            the title
 * @param maybeSubtitle    optional subtitle
 * @param maybeDescription optional description
 * @param maybeDocumentNumber optional document number to include in PDF
 */
case class TitleInfo(
    title: String,
    maybeSubtitle: Option[String],
    maybeDescription: Option[String],
    maybeDocumentNumber: Option[String]
)

object TitleInfo {

  /**
   * Displayed version for unpublished APIs
   */
  val unpublished = "(unpublished)"

  private def getSubtitle(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): String = {
    if (maybeTargetSv.isEmpty)
      s"Based on ${sv.subsystem} ${sv.maybeVersion.getOrElse(unpublished)}"
    else
      s"Based on ${sv.subsystem} ${sv.maybeVersion.getOrElse(unpublished)} and ${maybeTargetSv.get.subsystem} ${maybeTargetSv.get.maybeVersion
        .getOrElse(unpublished)}"
  }

  /**
   * Gets the title and optional subtitle to display based on the given source and target subsystems
   *
   * @param subsystemInfo   describes the source subsystem
   * @param maybeTargetSv  optional target subsystem and version
   * @param maybeIcd        optional ICD related information
   * @param part            optional string inserted before the title
   * @param documentNumber  optional document number to include in PDF
   * @return the title related information
   */
  def apply(
      subsystemInfo: SubsystemInfo,
      maybeTargetSv: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      part: String = "",
      documentNumber: String = ""
  ): TitleInfo = {
    val sv                  = subsystemInfo.sv
    val targetName          = maybeTargetSv.map(_.subsystem).getOrElse("")
    val componentPart       = subsystemInfo.sv.maybeComponent.map("." + _).getOrElse("")
    val targetComponentPart = maybeTargetSv.flatMap(_.maybeComponent).map("." + _).getOrElse("")
    val maybeDocumentNumber = if (documentNumber.nonEmpty) Some(documentNumber) else None
    if (maybeIcd.isDefined) {
      val icd = maybeIcd.get
      val title =
        if (part.nonEmpty)
          s"ICD SDB $part ${sv.subsystem}$componentPart -> $targetName$targetComponentPart (version ${icd.icdVersion})"
        else
          s"ICD SDB ${sv.subsystem}$componentPart / $targetName$targetComponentPart (version ${icd.icdVersion})"
      val subtitle = getSubtitle(sv, maybeTargetSv)
      TitleInfo(title, Some(subtitle), None, maybeDocumentNumber)
    }
    else {
      if (maybeTargetSv.isDefined) {
        val title    = s"ICD SDB $part ${sv.subsystem}$componentPart -> $targetName$targetComponentPart $unpublished"
        val subtitle = getSubtitle(sv, maybeTargetSv)
        TitleInfo(title, Some(subtitle), None, maybeDocumentNumber)
      }
      else {
        TitleInfo(
          s"API SDB for ${sv.subsystem}$componentPart ${sv.maybeVersion.getOrElse(unpublished)}",
          Some(subsystemInfo.title),
          Some(subsystemInfo.description),
          maybeDocumentNumber
        )
      }
    }
  }
}
