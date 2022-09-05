package csw.services.icd.fits

import csw.services.icd.IcdToPdf
import csw.services.icd.html.IcdToHtml
import icd.web.shared.{FitsKeyInfo, HtmlHeadings, PdfOptions}

import java.io.{File, FileOutputStream}

case class IcdFitsPrinter(fitsKeyList: List[FitsKeyInfo]) {
  def saveToFile(pdfOptions: PdfOptions, file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = {
      IcdToPdf.saveAsPdf(file, html, showLogo = false, pdfOptions)
    }

    def getAsHtml: Option[String] = {
      import scalatags.Text.all._
      if (fitsKeyList.isEmpty) None
      else {
        val nh = new HtmlHeadings
        val markup = html(
          head(
            scalatags.Text.tags2.title("FITS Dictionary"),
            scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
          ),
          body(
            IcdToHtml.makeFitsKeyTable(fitsKeyList, nh, withLinks = false)
          )
        )
        Some(markup.render)
      }
    }

    val maybeHtml = getAsHtml
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
}
