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
      maybeOrientation: Option[String] = None,
      maybeFontSize: Option[Int] = None,
      maybeLineHeight: Option[String] = None,
      maybePaperSize: Option[String] = None,
      maybeDetails: Option[Boolean] = None,
      expandedIds: List[String] = Nil,
      processMarkdown: Boolean = true
  ): PdfOptions =
    PdfOptions(
      maybeOrientation.getOrElse(defaultOrientation),
      maybeFontSize.getOrElse(defaultFontSize),
      maybeLineHeight.getOrElse(defaultLineHeight),
      maybePaperSize.getOrElse(defaultPaperSize),
      maybeDetails.getOrElse(defaultDetails),
      expandedIds,
      processMarkdown
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
    expandedIds: List[String],
    // If true (default), process markdown in text, otherwise don't (set to false if you don't need the descriptions)
    processMarkdown: Boolean
)
