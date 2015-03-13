package csw.services.icd

import java.io._

import csw.services.icd.gfm.{ Gfm, Level, IcdToGfm }

/**
 * Saves the ICD as a document
 */
object IcdPrinter {

  private def getCss: String = {
    val stream = getClass.getResourceAsStream("/icd.css")
    val lines = scala.io.Source.fromInputStream(stream).getLines()
    lines.mkString("\n")
  }

  /**
   * Parses the set of standard ICD files (see stdNames) in the given dir and its subdirectories
   * and saves a document describing them to the given file in a format determined by the
   * file's suffix, which should be one of (md, html, pdf).
   * @param dir the root directory containing the standard ICD files and optional subdirectories, also containing ICD files
   * @param file the file in which to save the document
   */
  def saveToFile(dir: File, file: File): Unit = {
    // These control the document header numbering
    implicit val counter = Iterator.from(0)
    val level = Level()

    // get one parser object for each subdirectory containing an ICD
    val parsers = (dir :: subDirs(dir)).map(IcdParser)

    if (parsers.nonEmpty) {
      file.getName.split('.').drop(1).lastOption match {
        case Some("md")   ⇒ saveAsGfm()
        case Some("html") ⇒ saveAsHtml()
        case Some("pdf")  ⇒ saveAsPdf()
        case _            ⇒ println(s"Unsupported output format: Expected *.md. *.html or *.pdf")
      }
    }

    // Returns a Gfm link to the given Gfm heading for the TOC. For example:
    // * [3.2 Publish](3.2-publish)
    // Note that whitespace is converted to '-', special chars are ignored, and text is converted to lower case in the target.
    def mkGfmLink(heading: String): String = {
      val indent = "\t" * (heading.takeWhile(_ == '#').length - 2)
      val s = heading.dropWhile(_ == '#')
      // XXX could also parse target from <a> element
      val text = s.substring(s.lastIndexOf("</a>") + 4)
      val target = "#" + Gfm.headingTargetName(text)
      s"$indent* [$text]($target)"
    }

    // Returns a TOC for the given GFM body
    def gfmToToc(body: String): String = {
      val links = for (line ← body.lines if line.startsWith("#")) yield mkGfmLink(line)
      "## Table of Contents\n\n" + links.toList.mkString("\n")
    }

    // Gets the GFM (Github flavored markdown) for the document
    def getAsGfm: String = {
      val title = "#Interface Control Document\n#" + parsers.head.icdModel.map(_.name).getOrElse("")
      val pagebreak = "\n<div class='pagebreak'></div>\n" // new page, see pagebreak in icd.css
      val body = parsers.map(IcdToGfm(_, level.inc1()).gfm).mkString(pagebreak)
      val toc = gfmToToc(body)
      s"$title\n$pagebreak\n$toc\n$pagebreak\n$body\n"
    }

    def getAsHtml: String = {
      import org.pegdown.{ Extensions, PegDownProcessor }

      val pd = new PegDownProcessor(Extensions.TABLES | Extensions.AUTOLINKS)
      val title = parsers.head.icdModel.map(_.name).getOrElse("ICD")
      val body = pd.markdownToHtml(getAsGfm)
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

    // Saves the document in GFM (Github flavored markdown) format
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
      import com.itextpdf.tool.xml.XMLWorkerHelper
      import com.itextpdf.text._
      import com.itextpdf.text.pdf._

      // Adds page number to al the pages except the first.
      object PageStamper extends PdfPageEventHelper {
        override def onEndPage(writer: PdfWriter, document: Document) {
          try {
            val pageNumber = writer.getPageNumber
            val pageSize = document.getPageSize
            val x = pageSize.getRight(40)
            val y = pageSize.getBottom(30)
            val rect = new Rectangle(x, y, x + 40, y - 30)
            val dc = writer.getDirectContent
            dc.setColorFill(BaseColor.GRAY)
            ColumnText.showTextAligned(dc,
              Element.ALIGN_CENTER, new Phrase(s"$pageNumber"),
              (rect.getLeft + rect.getRight) / 2, rect.getBottom - 18, 0)

            // Add the TMT logo on the first page
            if (pageNumber == 1) {
              val url = getClass.getClassLoader.getResource("tmt.png")
              val image = Image.getInstance(url)
              image.setAbsolutePosition(
                pageSize.getLeft + pageSize.getWidth / 2 - image.getWidth / 2,
                pageSize.getBottom + pageSize.getHeight / 2 - image.getHeight / 2)
              dc.addImage(image)
            }

          } catch {
            case e: Throwable ⇒ e.printStackTrace()
          }
        }
      }

      val document = new Document(PageSize.LETTER)
      val out = new FileOutputStream(file)
      val writer = PdfWriter.getInstance(document, out)
      writer.setPageEvent(PageStamper)
      document.open()
      XMLWorkerHelper.getInstance().parseXHtml(writer, document, new ByteArrayInputStream(getAsHtml.getBytes))
      document.close()
      out.close()
    }
  }

}
