package csw.services.icd

import java.io.{File, FileOutputStream}
import java.nio.file.Files

import icd.web.shared.{BuildInfo, SubsystemWithVersion}

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
  private def getFile(sv: SubsystemWithVersion, maybeOrientation: Option[String]): File = {
    val name = s"${sv.subsystem}-${sv.maybeVersion.get}-${maybeOrientation.getOrElse("landscape")}.pdf"
    new File(dir, name)
  }

  // Gets the file used to store the ICD between the two subsystem versions
  private def getFile(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion, maybeOrientation: Option[String]): File = {
    val name =
      s"${sv.subsystem}-${sv.maybeVersion.get}--${targetSv.subsystem}-${targetSv.maybeVersion.get}-${maybeOrientation.getOrElse("landscape")}.pdf"
    new File(dir, name)
  }

  // Gets the PDF data for the given API version
  def getApi(sv: SubsystemWithVersion, maybeOrientation: Option[String], searchAllSubsystems: Boolean): Option[Array[Byte]] = {
    if (softwareVersion == defaultSoftwareVersion || sv.maybeVersion.isEmpty || sv.maybeComponent.isDefined || searchAllSubsystems) {
      None
    } else {
      val file = getFile(sv, maybeOrientation)
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
      maybeOrientation: Option[String],
      searchAllSubsystems: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty && !searchAllSubsystems) {
      dir.mkdirs()
      val file = getFile(sv, maybeOrientation)
      val out  = new FileOutputStream(file)
      out.write(data)
      out.close()
    }
  }

  // Gets the PDF data for the ICD between the two subsystem versions
  def getIcd(
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybeOrientation: Option[String],
      searchAllSubsystems: Boolean
  ): Option[Array[Byte]] = {
    if (softwareVersion == defaultSoftwareVersion || sv.maybeVersion.isEmpty || targetSv.maybeVersion.isEmpty
        || sv.maybeComponent.isDefined || targetSv.maybeComponent.isDefined || searchAllSubsystems) {
      None
    } else {
      val file = getFile(sv, targetSv, maybeOrientation)
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
      maybeOrientation: Option[String],
      searchAllSubsystems: Boolean,
      data: Array[Byte]
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined
        && targetSv.maybeVersion.isDefined && sv.maybeComponent.isEmpty
        && targetSv.maybeComponent.isEmpty && !searchAllSubsystems) {
      dir.mkdirs()
      val file = getFile(sv, targetSv, maybeOrientation)
      val out  = new FileOutputStream(file)
      out.write(data)
      out.close()
    }
  }

  // Saves a copy of the given PDF file for the given API or ICD in the cache (API if maybeTargetSv is None).
  def save(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      maybeOrientation: Option[String],
      searchAllSubsystems: Boolean,
      file: File
  ): Unit = {
    if (softwareVersion != defaultSoftwareVersion && sv.maybeVersion.isDefined
        && maybeTargetSv.forall(_.maybeVersion.isDefined) && sv.maybeComponent.isEmpty
        && maybeTargetSv.forall(_.maybeComponent.isEmpty) && !searchAllSubsystems) {
      val data = Files.readAllBytes(file.toPath)
      if (maybeTargetSv.isDefined)
        saveIcd(sv, maybeTargetSv.get, maybeOrientation, searchAllSubsystems, data)
      else
        saveApi(sv, maybeOrientation, searchAllSubsystems, data)
    }
  }

  // Deletes the cached PDF file (or files) for the given API
  def deleteApi(sv: SubsystemWithVersion): Unit = {
    if (sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty) {
      val files = List(getFile(sv, Some("portrait")), getFile(sv, Some("landscape")))
      files.foreach { file =>
        if (file.exists())
          file.delete()
      }
    }
  }

  // Deletes the cached PDF file (or files) for the ICD between the given APIs
  def deleteIcd(sv: SubsystemWithVersion, targetSv: SubsystemWithVersion): Unit = {
    if (sv.maybeVersion.isDefined && sv.maybeComponent.isEmpty
        && targetSv.maybeVersion.isDefined && targetSv.maybeComponent.isEmpty) {
      val files = List(getFile(sv, targetSv, Some("portrait")), getFile(sv, targetSv, Some("landscape")))
      files.foreach { file =>
        if (file.exists())
          file.delete()
      }
    }
  }

}
