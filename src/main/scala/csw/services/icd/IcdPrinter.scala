package csw.services.icd

import java.io.{ ByteArrayInputStream, File, FileInputStream, FileOutputStream }

import com.itextpdf.text.PageSize
import csw.services.icd.gfm.IcdToGfm

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

    file.getName.split('.').drop(1).lastOption match {
      case Some("md")   ⇒ saveAsGfm()
      case Some("html") ⇒ saveAsHtml()
      case Some("pdf")  ⇒ saveAsPdf()
      case _            ⇒ println(s"Unsupported output format: Expected *.md. *.html or *.pdf")
    }

    def getAsGfm: String = IcdToGfm(parser).gfm

    def getAsHtml: String = {
      import org.pegdown.{ Extensions, PegDownProcessor }

      val pd = new PegDownProcessor(Extensions.TABLES)
      val title = parser.icdModel.map(_.name).getOrElse("ICD")
      val body = pd.markdownToHtml(IcdToGfm(parser).gfm)
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

    def saveAsGfm(): Unit = {
      // convert model to markdown
      val out = new FileOutputStream(file)
      out.write(getAsGfm.getBytes)
      out.close()
    }

    def saveAsHtml(): Unit = {
      val out = new FileOutputStream(file)
      out.write(getAsHtml.getBytes)
      out.close()
    }

    def saveAsPdf(): Unit = {
      import com.itextpdf.text.Document
      import com.itextpdf.text.pdf.PdfWriter
      import com.itextpdf.tool.xml.XMLWorkerHelper

      val document = new Document(PageSize.LETTER)
      val out = new FileOutputStream(file)
      val writer = PdfWriter.getInstance(document, out)
      document.open()
      XMLWorkerHelper.getInstance().parseXHtml(writer, document, new ByteArrayInputStream(getAsHtml.getBytes))
      document.close()
      out.close()
    }

  }
}
