package csw.services.icd

import java.io.{ByteArrayInputStream, File, FileOutputStream, OutputStream}
import java.nio.charset.Charset

/**
 * Handles converting an ICD API from HTML to PDF format
 */
object IcdToPdf {

  import com.itextpdf.text._
  import com.itextpdf.text.pdf._
  import com.itextpdf.tool.xml.XMLWorkerHelper

  // Adds page number to al the pages except the first.
  private case class PageStamper(showLogo: Boolean) extends PdfPageEventHelper {
    override def onEndPage(writer: PdfWriter, document: Document): Unit = {
      try {
        val pageNumber = writer.getPageNumber
        val pageSize = document.getPageSize
        val x = pageSize.getRight(40)
        val y = pageSize.getBottom(30)
        val rect = new Rectangle(x, y, x + 40, y - 30)
        val dc = writer.getDirectContent
        dc.setColorFill(BaseColor.GRAY)
        ColumnText.showTextAligned(
          dc,
          Element.ALIGN_CENTER,
          new Phrase(s"$pageNumber"),
          (rect.getLeft + rect.getRight) / 2,
          rect.getBottom - 18,
          0
        )

        // Add the TMT logo on the first page
        if (showLogo && pageNumber == 1) {
          val url = getClass.getClassLoader.getResource("tmt.png")
          val image = Image.getInstance(url)
          image.setAbsolutePosition(
            pageSize.getLeft + pageSize.getWidth / 2 - image.getWidth / 2,
            pageSize.getBottom + pageSize.getHeight / 2 - image.getHeight / 2
          )
          dc.addImage(image)
        }

      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }

  /**
   * Converts the given HTML to PDF and saves it in the given file.
   *
   * @param file     the name of the file in which to save the PDF
   * @param html     the input doc in HTML format
   * @param showLogo if true insert the TMT logo
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   */
  def saveAsPdf(file: File, html: String, showLogo: Boolean, maybeOrientation: Option[String]): Unit = {
    val out = new FileOutputStream(file)
    saveAsPdf(out, html, showLogo, maybeOrientation)
    out.close()
  }

  /**
   * Converts the given HTML to PDF and saves it in the given file.
   *
   * @param out      the output stream in which to save the PDF
   * @param html     the input doc in HTML format
   * @param showLogo if true insert the TMT logo
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   */
  def saveAsPdf(out: OutputStream, html: String, showLogo: Boolean, maybeOrientation: Option[String]): Unit = {
    val orientation = maybeOrientation.getOrElse("landscape")
    val pageSize = if (orientation == "landscape") PageSize.LETTER.rotate() else PageSize.LETTER
    val document = new Document(pageSize)
    val writer = PdfWriter.getInstance(document, out)
    writer.setPageEvent(PageStamper(showLogo))
    document.open()
    XMLWorkerHelper.getInstance().parseXHtml(writer, document, new ByteArrayInputStream(html.getBytes), Charset.forName("UTF-8"))
    document.close()
    out.close()
  }
}
