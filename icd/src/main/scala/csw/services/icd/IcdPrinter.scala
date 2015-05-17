package csw.services.icd

import java.io._

import csw.services.icd.gfm.{ Level, IcdToGfm }
import csw.services.icd.model.IcdModels

/**
 * Saves the ICD as a document
 */
object IcdPrinter {

  /**
   * Gets the GFM (Github flavored markdown) for the document
   * @param models list of ICD models for the different parts of the ICD
   * @return a string in GFM format
   */
  def getAsGfm(models: List[IcdModels]): String = {

    // These control the document header numbering
    implicit val counter = Iterator.from(0)
    val level: Level = Level()

    val includesSubsystem = models.head.subsystemModel.isDefined
    val subtitle = if (includesSubsystem)
      models.head.subsystemModel.get.title
    else
      models.head.componentModel.get.subsystem
    val title = "#Interface Control Document\n#" + subtitle
    val pagebreak = "\n<div class='pagebreak'></div>\n" // new page, see pagebreak in icd.css
    val body = models.map(IcdToGfm(_, level.inc1()).gfm).mkString(pagebreak)
    val toc = IcdToGfm.gfmToToc(body, includesSubsystem)
    s"$title\n$pagebreak\n$toc\n$pagebreak\n$body\n"
  }

  /**
   * Gets the HTML for the document
   * @param models list of ICD models for the different parts of the ICD
   * @return a string in HTML format
   */
  def getAsHtml(models: List[IcdModels]): String = {
    val title = models.head.subsystemModel.map(_.title).getOrElse("ICD")
    IcdToHtml.getAsHtml(title, getAsGfm(models))
  }

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
   * @param models a list of objects describing the parsed ICD, one for each directory in the ICD
   * @param file the file in which to save the document
   */
  def saveToFile(models: List[IcdModels], file: File): Unit = {
    file.getName.split('.').drop(1).lastOption match {
      case Some("md")   ⇒ saveAsGfm()
      case Some("html") ⇒ saveAsHtml()
      case Some("pdf")  ⇒ saveAsPdf()
      case _            ⇒ println(s"Unsupported output format: Expected *.md. *.html or *.pdf")
    }

    // Saves the document in GFM (Github flavored markdown) format
    def saveAsGfm(): Unit = {
      val out = new FileOutputStream(file)
      out.write(getAsGfm(models).getBytes)
      out.close()
    }

    def saveAsHtml(): Unit = {
      val out = new FileOutputStream(file)
      out.write(getAsHtml(models).getBytes)
      out.close()
    }

    def saveAsPdf(): Unit = IcdToPdf.saveAsPdf(file, getAsHtml(models))

  }
}
