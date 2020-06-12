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
      searchAllSubsystems: Boolean
  ): Option[Array[Byte]] = {
    if (softwareVersion == defaultSoftwareVersion || sv.maybeVersion.isEmpty || sv.maybeComponent.isDefined || searchAllSubsystems || !pdfOptions.details) {
      None
    } else {
      val file = getFile(sv, pdfOptions)
      if (file.exists()) {
        try {
          Some(Files.readAllBytes(file.toPath))
        } catch {
          case e: Exception =>
            e.printStackTrace()
            None
        }
      } else None
    }
  }

  // Saves the data for the given API version
  def saveApi(
      sv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty && !searchAllSubsystems || !pdfOptions.details) {
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
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean
  ): Option[Array[Byte]] = {
    if (softwareVersion == defaultSoftwareVersion || sv.maybeVersion.isEmpty || targetSv.maybeVersion.isEmpty
        || sv.maybeComponent.isDefined || targetSv.maybeComponent.isDefined || searchAllSubsystems || !pdfOptions.details) {
      None
    } else {
      val file = getFile(sv, targetSv, pdfOptions)
      if (file.exists()) {
        try {
          Some(Files.readAllBytes(file.toPath))
        } catch {
          case e: Exception =>
            e.printStackTrace()
            None
        }
      } else None
    }
  }

  // Saves the PDF data for the ICD between the given subsyetem versions
  def saveIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      pdfOptions: PdfOptions,
      searchAllSubsystems: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined
        && targetSv.maybeVersion.isDefined && sv.maybeComponent.isEmpty
        && targetSv.maybeComponent.isEmpty && !searchAllSubsystems) {
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
      file: File
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined
        && maybeTargetSv.forall(_.maybeVersion.isDefined) && sv.maybeComponent.isEmpty
        && maybeTargetSv.forall(_.maybeComponent.isEmpty) && !searchAllSubsystems || !pdfOptions.details) {
      val data = Files.readAllBytes(file.toPath)
      if (maybeTargetSv.isDefined)
        saveIcd(sv, maybeTargetSv.get, pdfOptions, searchAllSubsystems, data)
      else
        saveApi(sv, pdfOptions, searchAllSubsystems, data)
    }
  }

  //  val orientations = List("landscape", "portrait")
  //  val fontSizes    = List(10, 12, 14, 16)
  //  val lineHeights  = List(1.6, 1.4, 1.2, 1.0)
  //  val paperSizes   = List("Letter", "Legal", "A4", "A3")

  // Deletes the cached PDF file (or files) for the given API
  def deleteApi(sv: SubsystemWithVersion): Unit = {
    import PdfOptions._
    if (sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty) {
      orientations.foreach { orient =>
        fontSizes.foreach { fs =>
          lineHeights.foreach { lh =>
            paperSizes.foreach { ps =>
              val pdfOptions = PdfOptions(orient, fs, lh, ps, details = true)
              val file       = getFile(sv, pdfOptions)
              if (file.exists())
                file.delete()
            }
          }
        }
      }
    }
  }

  // Deletes the cached PDF file (or files) for the ICD between the given APIs
  def deleteIcd(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion): Unit = {
    import PdfOptions._
    if (sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty
        && targetSv.maybeVersion.isDefined && targetSv.maybeComponent.isEmpty) {
      orientations.foreach { orient =>
        fontSizes.foreach { fs =>
          lineHeights.foreach { lh =>
            paperSizes.foreach { ps =>
              val pdfOptions = PdfOptions(orient, fs, lh, ps, details = true)
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
