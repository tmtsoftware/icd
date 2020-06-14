package icd.web.shared

object PdfOptions {
  val defaultOrientation = "landscape"
  val defaultFontSize    = 10
  val defaultLineHeight  = "1.6"
  val defaultPaperSize   = "Letter"
  val defaultDetails     = true

  val orientations = List("landscape", "portrait")
  val fontSizes    = List(10, 12, 14, 16)
  val lineHeights  = List("1.6", "1.4", "1.2", "1.0")
  val paperSizes   = List("Letter", "Legal", "A4", "A3")

  def apply(
      maybeOrientation: Option[String],
      maybeFontSize: Option[Int],
      maybeLineHeight: Option[String],
      maybePaperSize: Option[String],
      maybeDetails: Option[Boolean],
      expandedIds: List[String]
  ): PdfOptions =
    PdfOptions(
      maybeOrientation.getOrElse(defaultOrientation),
      maybeFontSize.getOrElse(defaultFontSize),
      maybeLineHeight.getOrElse(defaultLineHeight),
      maybePaperSize.getOrElse(defaultPaperSize),
      maybeDetails.getOrElse(defaultDetails),
      expandedIds
    )
}

case class PdfOptions(
    // landscape, portrait
    orientation: String,
    // 10, 12, 14, 16
    fontSize: Int,
    // 1.6, 1.4, 1.2, 1.0
    lineHeight: String,
    // Letter, Legal, A4, A3
    paperSize: String,
    // true: Show the details for all events, commands, alarms (default)
    // false: Include only the details that are expanded in the HTML view
    details: Boolean,
    // List of ids for expanded details
    expandedIds: List[String]
)
