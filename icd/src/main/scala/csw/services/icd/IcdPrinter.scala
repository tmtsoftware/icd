package csw.services.icd

import java.io._

import csw.services.icd.gfm.{ Gfm, Level, IcdToGfm }
import csw.services.icd.model.IcdModels

/**
 * Saves the ICD as a document
 */
object IcdPrinter {

  /**
   * Parses the set of standard ICD files (see stdNames) in the given dir and its subdirectories
   * and saves a document describing them to the given file in a format determined by the
   * file's suffix, which should be one of (md, html, pdf).
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
   * @param parsers a list of objects describing the parsed ICD, one for each directory in the ICD
   * @param file the file in which to save the document
   */
  def saveToFile(parsers: List[IcdModels], file: File): Unit = {
    // These control the document header numbering
    implicit val counter = Iterator.from(0)
    val level = Level()

    file.getName.split('.').drop(1).lastOption match {
      case Some("md")   ⇒ saveAsGfm()
      case Some("html") ⇒ saveAsHtml()
      case Some("pdf")  ⇒ saveAsPdf()
      case _            ⇒ println(s"Unsupported output format: Expected *.md. *.html or *.pdf")
    }

    // Gets the GFM (Github flavored markdown) for the document
    def getAsGfm: String = {
      val title = "#Interface Control Document\n#" + parsers.head.icdModel.map(_.name).getOrElse("")
      val pagebreak = "\n<div class='pagebreak'></div>\n" // new page, see pagebreak in icd.css
      val body = parsers.map(IcdToGfm(_, level.inc1()).gfm).mkString(pagebreak)
      val toc = IcdToGfm.gfmToToc(body)
      s"$title\n$pagebreak\n$toc\n$pagebreak\n$body\n"
    }

    def getAsHtml: String = {
      val title = parsers.head.icdModel.map(_.name).getOrElse("ICD")
      IcdToHtml.getAsHtml(title, getAsGfm)
    }

    // Saves the document in GFM (Github flavored markdown) format
    def saveAsGfm(): Unit = {
      val out = new FileOutputStream(file)
      out.write(getAsGfm.getBytes)
      out.close()
    }

    def saveAsHtml(): Unit = {
      val out = new FileOutputStream(file)
      out.write(getAsHtml.getBytes)
      out.close()
    }

    def saveAsPdf(): Unit = IcdToPdf.saveAsPdf(file, getAsHtml)

  }
}
