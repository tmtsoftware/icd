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

/**
 * Handles converting an ICD API from HTML to PDF format
 */
object IcdToPdf {

  // Adds page number to al the pages except the first.
  private case class PageStamper(showLogo: Boolean) extends IEventHandler {

    def addLogo(pageSize: Rectangle, pdfCanvas: PdfCanvas, pdfDocument: PdfDocument): Unit = {
      val url   = getClass.getClassLoader.getResource("tmt.png")
      val image = new Image(ImageDataFactory.create(url))
      val x     = pageSize.getLeft + pageSize.getWidth / 2 - image.getImageWidth / 2
      val y     = pageSize.getBottom + pageSize.getHeight / 2
      val rect = new Rectangle(x, y, x + image.getImageWidth, y - image.getImageHeight)
      new Canvas(pdfCanvas, pdfDocument, rect).add(image)
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
        val canvas: Canvas             = new Canvas(pdfCanvas, pdfDocument, rect)
        val pageNumber                 = pdfDocument.getPageNumber(page)
        canvas.add(new Paragraph(String.valueOf(pageNumber)))

        // Add the TMT logo on the first pageOFF
        if (showLogo && pageNumber == 1) {
          addLogo(pageSize, pdfCanvas, pdfDocument)
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
   * @param out              the output stream in which to save the PDF
   * @param html             the input doc in HTML format
   * @param showLogo         if true insert the TMT logo
   * @param maybeOrientation If set, should be "portrait" or "landscape" (default: landscape)
   */
  def saveAsPdf(out: OutputStream, html: String, showLogo: Boolean, maybeOrientation: Option[String]): Unit = {
    val orientation              = maybeOrientation.getOrElse("landscape")
    val pageSize                 = if (orientation == "landscape") PageSize.LETTER.rotate() else PageSize.LETTER
    val writer: PdfWriter        = new PdfWriter(out)
    val pdfDocument: PdfDocument = new PdfDocument(writer)
    val document                 = new Document(pdfDocument)
    pdfDocument.setDefaultPageSize(pageSize)
    val handler: IEventHandler = PageStamper(showLogo)
    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, handler)
    HtmlConverter.convertToPdf(new ByteArrayInputStream(html.getBytes()), pdfDocument)
    out.close()
    //    pdfDocument.close()
    document.close()
  }
}
