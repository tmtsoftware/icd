package csw.services.icd

import java.io.{ FileOutputStream, File }

/**
 * Handles converting ICD from GFM to HTML
 */
object IcdToHtml {
  private def getCss: String = {
    val stream = getClass.getResourceAsStream("/icd.css")
    val lines = scala.io.Source.fromInputStream(stream).getLines()
    lines.mkString("\n")
  }

  /**
   * Returns the HTML snippet (without header or styles) for the given markdown (GFM)
   * @param gfm the GFM markdown
   */
  def getAsPlainHtml(gfm: String): String = {
    import org.pegdown.{ Extensions, PegDownProcessor }
    val pd = new PegDownProcessor(Extensions.TABLES | Extensions.AUTOLINKS)
    pd.markdownToHtml(gfm)
  }

  /**
   * Returns the HTML for the given markdown (GFM)
   * @param title title to use for the HTML document
   * @param gfm the GFM markdown
   */
  def getAsHtml(title: String, gfm: String): String = {
    val body = getAsPlainHtml(gfm)
    val css = getCss
    s"""
       |<html>
       |<head>
       |<title>$title</title>
                       |<style type='text/css'>
                       |$css
        |</style>
        |</head>
        |<body>
        |$body
        |</body>
        |</html>
         """.stripMargin
  }
}
