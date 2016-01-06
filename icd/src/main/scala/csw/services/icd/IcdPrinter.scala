package csw.services.icd

import java.io._

import csw.services.icd.html.IcdToHtml
import csw.services.icd.model.IcdModels

/**
 * Saves the ICD/API as a document
 */
object IcdPrinter {

  /**
   * Parses the set of standard ICD files (see stdNames) in the given dir and its subdirectories
   * and saves a document describing them to the given file in a format determined by the
   * file's suffix, which should be one of (md, html, pdf).
   *
   * @param dir the root directory containing the standard ICD files and optional subdirectories, also containing ICD files
   * @param file the file in which to save the document
   */
  def saveToFile(dir: File, file: File): Unit = {
    // get one parser object for each subdirectory containing an ICD
    val parsers = (dir :: subDirs(dir)).map(IcdParser)
    if (parsers.nonEmpty) saveToFile(parsers, file)
  }

  /**
   * Saves a document for the ICD to the given file in a format determined by the
   * file's suffix, which should be one of (md, html, pdf).
   *
   * @param models a list of objects describing the parsed ICD, one for each directory in the ICD
   * @param file the file in which to save the document
   */
  def saveToFile(models: List[IcdModels], file: File): Unit = {
    file.getName.split('.').drop(1).lastOption match {
      case Some("html") ⇒ saveAsHtml()
      case Some("pdf")  ⇒ saveAsPdf()
      case _            ⇒ println(s"Unsupported output format: Expected *.md. *.html or *.pdf")
    }

    def saveAsHtml(): Unit = {
      val out = new FileOutputStream(file)
      out.write(IcdToHtml.getAsHtml(models).getBytes)
      out.close()
    }

    def saveAsPdf(): Unit = IcdToPdf.saveAsPdf(file, IcdToHtml.getAsHtml(models))
  }
}
