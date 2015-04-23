package csw.services.icd.db

import java.io.File

import com.mongodb.casbah.MongoDB
import csw.services.icd.gfm.Level
import csw.services.icd.{ IcdToHtml, IcdParser, IcdPrinter }

/**
 * Save an ICD from the database to markdown (GFM), HTML or PDF.
 * @param query used to query the database
 */
case class IcdDbPrinter(query: IcdDbQuery) {

  /**
   * Returns an HTML snippet describing the ICD for the given component
   * @param componentName the value of the "name" field in the top level component definition
   */
  def getAsPlainHtml(componentName: String): String = {
    val models = query.getModels(componentName)
    if (models.nonEmpty) IcdToHtml.getAsPlainHtml(IcdPrinter.getAsGfm(models)) else {
      s"<!DOCTYPE html></head><body><p>No ICD named $componentName was found in the database</p></body></html>"
    }
  }

  /**
   * Saves a document describing the ICD for the given component to the given file,
   * in a format determined by the file's suffix, which should be one of (md, html, pdf).
   * @param componentName the value of the "name" field in the top level component definition
   * @param file the file in which to save the document (should end with .md, .html or .pdf)
   */
  def saveToFile(componentName: String, file: File): Unit = {
    val models = query.getModels(componentName)
    if (models.nonEmpty) IcdPrinter.saveToFile(models, file)
  }
}
