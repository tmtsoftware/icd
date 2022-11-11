package csw.services.icd

import java.io.{File, FileOutputStream}
import java.nio.file.Files

import icd.web.shared.{BuildInfo, PdfOptions, SubsystemWithVersion}

/**
 * Implements a cache of PDF files for published API and ICD releases.
 *
 * @param cacheDir the root directory for storing the PDF files (/icd-version will be appended)
 */
class PdfCache(cacheDir: File) {
  val defaultSoftwareVersion  = "dev"
  val softwareVersion: String = BuildInfo.version
  val dir                     = new File(cacheDir, softwareVersion)
  if (softwareVersion != defaultSoftwareVersion)
    dir.mkdirs()

  // Gets the file used to store the given API version
  private def getFile(
      sv: SubsystemWithVersion,
      pdfOptions: PdfOptions
  ): File = {
    import pdfOptions._
    val name = s"${sv.subsystem}-${sv.maybeVersion.get}-$orientation-$fontSize-$lineHeight-$paperSize.pdf"
    new File(dir, name)
  }

  // Returns true if the PDF cache should be used
  private def useCache(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      pdfOptions: PdfOptions
  ): Boolean = {
    useCache(sv, pdfOptions, searchAllSubsystems = false, clientApi = false) &&
    targetSv.maybeVersion.isDefined &&
    targetSv.maybeVersion.get != "master" &&
    targetSv.maybeComponent.isEmpty
  }

  // Returns true if the PDF cache should be used
  private def useCache(
      sv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Boolean = {
    softwareVersion != defaultSoftwareVersion &&
    sv.maybeVersion.isDefined &&
    sv.maybeVersion.get != "master" &&
    sv.maybeComponent.isEmpty &&
    !clientApi &&
    !searchAllSubsystems &&
    pdfOptions.details &&
    pdfOptions.documentNumber.isEmpty
  }

  // Gets the file used to store the ICD between the two subsystem versions
  private def getFile(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      pdfOptions: PdfOptions
  ): File = {
    import pdfOptions._
    val name =
      s"${sv.subsystem}-${sv.maybeVersion.get}--${targetSv.subsystem}-${targetSv.maybeVersion.get}-$orientation-$fontSize-$lineHeight-$paperSize.pdf"
    new File(dir, name)
  }

  // Gets the PDF data for the given API version
  def getApi(
      sv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Option[Array[Byte]] = {
    if (useCache(sv, pdfOptions, searchAllSubsystems, clientApi)) {
      val file = getFile(sv, pdfOptions)
      if (file.exists()) {
        try {
          Some(Files.readAllBytes(file.toPath))
        }
        catch {
          case e: Exception =>
            e.printStackTrace()
            None
        }
      }
      else None
    }
    else None
  }

  // Saves the data for the given API version
  def saveApi(
      sv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      clientApi: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (useCache(sv, pdfOptions, searchAllSubsystems, clientApi)) {
      dir.mkdirs()
      val file = getFile(sv, pdfOptions)
      val out  = new FileOutputStream(file)
      out.write(data)
      out.close()
    }
  }

  // Gets the PDF data for the ICD between the two subsystem versions
  def getIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      pdfOptions: PdfOptions
  ): Option[Array[Byte]] = {
    if (useCache(sv, targetSv, pdfOptions)) {
      val file = getFile(sv, targetSv, pdfOptions)
      if (file.exists()) {
        try {
          Some(Files.readAllBytes(file.toPath))
        }
        catch {
          case e: Exception =>
            e.printStackTrace()
            None
        }
      }
      else None
    }
    else None
  }

  // Saves the PDF data for the ICD between the given subsyetem versions
  def saveIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (useCache(sv, targetSv, pdfOptions)) {
      dir.mkdirs()
      val file = getFile(sv, targetSv, pdfOptions)
      val out  = new FileOutputStream(file)
      out.write(data)
      out.close()
    }
  }

  // Saves a copy of the given PDF file for the given API or ICD in the cache (API if maybeTargetSv is None).
  def save(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      clientApi: Boolean,
      file: File
  ): Unit = {
    val doIt =
      if (maybeTargetSv.isDefined)
        useCache(sv, maybeTargetSv.get, pdfOptions)
      else
        useCache(sv, pdfOptions, searchAllSubsystems, clientApi)

    if (doIt) {
      val data = Files.readAllBytes(file.toPath)
      if (maybeTargetSv.isDefined)
        saveIcd(sv, maybeTargetSv.get, pdfOptions, searchAllSubsystems, data)
      else
        saveApi(sv, pdfOptions, searchAllSubsystems, clientApi, data)
    }
  }

  // Deletes the cached PDF file (or files) for the given API
  def deleteApi(sv: SubsystemWithVersion): Unit = {
    import PdfOptions._
    if (sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty) {
      orientations.foreach { orient =>
        fontSizes.foreach { fs =>
          lineHeights.foreach { lh =>
            paperSizes.foreach { ps =>
              List(true, false).foreach { clientApi =>
                val pdfOptions = PdfOptions(orient, fs, lh, ps, details = true, Nil, processMarkdown = false, documentNumber = "")
                val file       = getFile(sv, pdfOptions)
                if (file.exists())
                  file.delete()
              }
            }
          }
        }
      }
    }
  }

  // Deletes the cached PDF file (or files) for the ICD between the given APIs
  def deleteIcd(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion): Unit = {
    import PdfOptions._
    if (
      sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty
      && targetSv.maybeVersion.isDefined && targetSv.maybeComponent.isEmpty
    ) {
      orientations.foreach { orient =>
        fontSizes.foreach { fs =>
          lineHeights.foreach { lh =>
            paperSizes.foreach { ps =>
              val pdfOptions = PdfOptions(orient, fs, lh, ps, details = true, Nil, processMarkdown = false, documentNumber = "")
              val file       = getFile(sv, targetSv, pdfOptions)
              if (file.exists())
                file.delete()
            }
          }
        }
      }
    }
  }

}
