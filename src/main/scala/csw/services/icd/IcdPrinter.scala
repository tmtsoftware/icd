package csw.services.icd

import java.io.{ FileOutputStream, File }

import csw.services.icd.gfm.IcdToGfm
import org.pegdown.{ Extensions, PegDownProcessor }

/**
 * Saves the ICD as a document
 */
object IcdPrinter {

  def getCss: String = {
    val stream = getClass.getResourceAsStream("/icd.css")
    val lines = scala.io.Source.fromInputStream(stream).getLines()
    lines.mkString("\n")
  }

  /**
   * Parses the set of standard ICD files (see stdNames) and saves a document describing them
   * to the given file in a format determined by the file's suffix, which should be one of
   * (md, html, pdf).
   * @param dir the directory containing the standard ICD files
   * @param file the file in which to save the document
   */
  def saveToFile(dir: File, file: File): Unit = {
    val parser = IcdParser(dir)
    val name = file.getName
    val suffix = name.substring(name.lastIndexOf('.') + 1)
    if (suffix == "md") {
      // convert model to markdown
      val out = new FileOutputStream(file)
      out.write(IcdToGfm(parser).gfm.getBytes)
      out.close()
    } else if (suffix == "html") {
      val pd = new PegDownProcessor(Extensions.TABLES)
      val out = new FileOutputStream(file)
      val title = parser.icdModel.map(_.name).getOrElse("ICD")
      val body = pd.markdownToHtml(IcdToGfm(parser).gfm)
      val css = getCss
      val html =
        s"""
           |<html>
           | <head>
           |  <title>$title</title>
           |  <style type='text/css'>
           |$css
           |  </style>
           | </head>
           |<body>
           |$body
           |</body>
           |</html>
         """.stripMargin

      out.write(html.getBytes)
      out.close()
    } else {
      // XXX TODO: convert md to html, pdf
      println(s"Unsupported output format: $suffix")
    }
  }
}
