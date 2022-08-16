package csw.services.icd.fits

import icd.web.shared.{FitsKeyInfo, PdfOptions}

import java.io.File

case class IcdFitsPrinter(fitsKeyList: List[FitsKeyInfo]) {
  def saveToFile(pdfOptions: PdfOptions, file: File): Unit = {
    // XXX TODO
  }
}
