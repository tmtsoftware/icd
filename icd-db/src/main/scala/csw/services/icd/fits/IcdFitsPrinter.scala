package csw.services.icd.fits

import csw.services.icd.IcdToPdf
import csw.services.icd.html.IcdToHtml
import icd.web.shared.{FitsDictionary, HtmlHeadings, PdfOptions}

import java.io.{ByteArrayOutputStream, File, FileOutputStream}

case class IcdFitsPrinter(
    fitsDict: FitsDictionary,
    maybeSubsystem: Option[String] = None,
    maybeComponent: Option[String] = None
) {

  private def getAsHtml(maybeTag: Option[String], pdfOptions: PdfOptions): Option[String] = {
    import scalatags.Text.all._
    if (fitsDict.fitsKeys.isEmpty) None
    else {
      val str = List(
        maybeTag.map(t => s"tag: $t"),
        maybeSubsystem.map(s => s"subsystem: $s"),
        maybeComponent.map(c => s"component: $c")
      ).flatten.mkString(", ")
      val s = if (str.isEmpty) "" else s" ($str)"
      val titleStr = s"Fits Dictionary$s"
      val nh = new HtmlHeadings
      val markup = html(
        head(
          scalatags.Text.tags2.title(titleStr),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
        ),
        body(
          IcdToHtml.makeFitsKeyTable(maybeTag, fitsDict, nh, titleStr, withLinks = false, maybeSubsystem, maybeComponent),
          maybeTag.map(_ => span()).getOrElse(p(i("* Tags: DL = Diffraction-limited, SL = Seeing-limited")))
        )
      )
      Some(markup.render)
    }
  }

  def saveToFile(maybeTag: Option[String], pdfOptions: PdfOptions, file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = {
      IcdToPdf.saveAsPdf(file, html, showLogo = false, pdfOptions)
    }

    val maybeHtml = getAsHtml(maybeTag, pdfOptions)
    maybeHtml match {
      case Some(html) =>
        file.getName.split('.').drop(1).lastOption match {
          case Some("html") =>
            saveAsHtml(html)
          case Some("pdf") =>
            saveAsPdf(html)
          case _ => println(s"Unsupported output format: Expected *.html or *.pdf")
        }
      case None =>
        println(s"Failed to generate $file. You might need to run: 'icd-fits --ingest' first to update the database.")
    }
  }

  /**
   * Generates a PDF for FITS keywords returns a byte array containing the PDF
   * data, if successful.
   * @param maybeTag if defined, restrict to given tag
   * @param pdfOptions PDF generation options
   * @return byte array with the PDF data
   */
  def saveAsPdf(maybeTag: Option[String], pdfOptions: PdfOptions): Option[Array[Byte]] = {
    getAsHtml(maybeTag, pdfOptions).map { html =>
      val out = new ByteArrayOutputStream()
      IcdToPdf.saveAsPdf(out, html, showLogo = false, pdfOptions)
      out.toByteArray
    }
  }

}
