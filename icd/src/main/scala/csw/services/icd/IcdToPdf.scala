package csw.services.icd

import java.io.{ByteArrayInputStream, File, FileOutputStream, OutputStream}
import java.nio.charset.Charset

/**
 * Handles converting an ICD API from HTML to PDF format
 */
object IcdToPdf {
  import com.itextpdf.tool.xml.XMLWorkerHelper
  import com.itextpdf.text._
  import com.itextpdf.text.pdf._

  // Adds page number to al the pages except the first.
  private object PageStamper extends PdfPageEventHelper {
    override def onEndPage(writer: PdfWriter, document: Document) {
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
          Element.ALIGN_CENTER, new Phrase(s"$pageNumber"),
          (rect.getLeft + rect.getRight) / 2, rect.getBottom - 18, 0
        )

        // Add the TMT logo on the first page
        if (pageNumber == 1) {
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
   * @param file the name of the file in which to save the PDF
   * @param html the input doc in HTML format
   */
  def saveAsPdf(file: File, html: String): Unit = {
    val out = new FileOutputStream(file)
    saveAsPdf(out, html)
    out.close()
  }

  /**
   * Converts the given HTML to PDF and saves it in the given file.
   * @param out the output stream in which to save the PDF
   * @param html the input doc in HTML format
   */
  def saveAsPdf(out: OutputStream, html: String): Unit = {
    val document = new Document(PageSize.LETTER)
    val writer = PdfWriter.getInstance(document, out)
    writer.setPageEvent(PageStamper)
    document.open()
    XMLWorkerHelper.getInstance().parseXHtml(writer, document, new ByteArrayInputStream(html.getBytes), Charset.forName("UTF-8"))
    document.close()
    out.close()
  }
}
