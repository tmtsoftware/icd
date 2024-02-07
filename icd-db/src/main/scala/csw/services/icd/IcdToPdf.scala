package csw.services.icd

import java.io.{ByteArrayInputStream, File, FileOutputStream, OutputStream}

import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.events.{Event, IEventHandler, PdfDocumentEvent}
import com.itextpdf.kernel.geom.{PageSize, Rectangle}
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.{PdfDocument, PdfPage, PdfWriter}
import com.itextpdf.layout.element.{Image, Paragraph}
import com.itextpdf.layout.{Canvas, Document}
import icd.web.shared.PdfOptions

/**
 * Handles converting an ICD API from HTML to PDF format
 */
object IcdToPdf {

  // Adds page number to al the pages except the first.
  private case class PageStamper(showLogo: Boolean) extends IEventHandler {

    def addLogo(pageSize: Rectangle, pdfCanvas: PdfCanvas): Unit = {
      val url   = getClass.getClassLoader.getResource("tmt.png")
      val image = new Image(ImageDataFactory.create(url))
      val x     = pageSize.getLeft + pageSize.getWidth / 2 - image.getImageWidth / 2
      val y     = pageSize.getBottom + pageSize.getHeight / 2
      val rect  = new Rectangle(x, y, x + image.getImageWidth, y - image.getImageHeight)
      new Canvas(pdfCanvas, rect).add(image)
    }

    override def handleEvent(event: Event): Unit = {

      try {
        val docEvent: PdfDocumentEvent = event.asInstanceOf[PdfDocumentEvent]
        val pdfDocument: PdfDocument   = docEvent.getDocument
        val page: PdfPage              = docEvent.getPage
        val pdfCanvas: PdfCanvas       = new PdfCanvas(page.newContentStreamAfter(), page.getResources, pdfDocument)
        val pageSize                   = page.getPageSize
        val x                          = pageSize.getRight - 40
        val y                          = pageSize.getBottom + 30
        val rect                       = new Rectangle(x, y, x + 40, y - 30)
        val canvas: Canvas             = new Canvas(pdfCanvas, rect)
        val pageNumber                 = pdfDocument.getPageNumber(page)
        canvas.add(new Paragraph(String.valueOf(pageNumber)))

        // Add the TMT logo on the first pageOFF
        if (showLogo && pageNumber == 1) {
          addLogo(pageSize, pdfCanvas)
        }
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }

  /**
   * Converts the given HTML to PDF and saves it in the given file.
   *
   * @param file             the name of the file in which to save the PDF
   * @param html             the input doc in HTML format
   * @param showLogo         if true insert the TMT logo
   * @param pdfOptions       options for PDF generation
   */
  def saveAsPdf(file: File, html: String, showLogo: Boolean, pdfOptions: PdfOptions): Unit = {
    val out = new FileOutputStream(file)
    saveAsPdf(out, html, showLogo, pdfOptions)
    out.close()
  }

  /**
   * Converts the given HTML to PDF and saves it in the given file.
   *
   * @param out              the output stream in which to save the PDF
   * @param html             the input doc in HTML format
   * @param showLogo         if true insert the TMT logo
   * @param pdfOptions       options for PDF generation
   */
  def saveAsPdf(out: OutputStream, html: String, showLogo: Boolean, pdfOptions: PdfOptions): Unit = {
    import pdfOptions.*

    val basePageSize = paperSize match {
      case "Legal" => PageSize.LEGAL
      case "A4"    => PageSize.A4
      case "A3"    => PageSize.A3
      case _       => PageSize.LETTER
    }
    val pageSize = if (orientation == "landscape") basePageSize.rotate() else basePageSize

    val writer: PdfWriter        = new PdfWriter(out)
    val pdfDocument: PdfDocument = new PdfDocument(writer)
    val document                 = new Document(pdfDocument)
    pdfDocument.setDefaultPageSize(pageSize)
    val handler: IEventHandler = PageStamper(showLogo)
    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, handler)
    HtmlConverter.convertToPdf(new ByteArrayInputStream(html.getBytes()), pdfDocument)
    out.close()
    document.close()
  }
}
