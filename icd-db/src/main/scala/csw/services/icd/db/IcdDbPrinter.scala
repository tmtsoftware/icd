package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{IcdToHtml, NumberedHeadings}
import icd.web.shared.TitleInfo.unpublished
import icd.web.shared._
import IcdToHtml._


/**
  * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
  *
  * @param db used to query the database
  */
case class IcdDbPrinter(db: IcdDb) {

  /**
    * Gets information about a named subsystem
    */
  private def getSubsystemInfo(subsystem: String, versionOpt: Option[String]): Option[SubsystemInfo] =
    db.versionManager.getSubsystemModel(subsystem, versionOpt)
      .map(m => SubsystemInfo(m.subsystem, versionOpt, m.title, m.description))

  /**
    * Gets information about the given components
    *
    * @param subsystem         the components' subsystem
    * @param versionOpt        optional version (default: current version)
    * @param compNames         list of component names
    * @param targetSubsystem   optional target subsystem and version
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @return future list of objects describing the components
    */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion,
                               targetCompNameOpt: Option[String]): List[ComponentInfo] = {
      icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem, targetCompNameOpt)
  }

  /**
    * Returns information for each component.
    * If the target subsystem is defined, the information is restricted to the ICD
    * from subsystem to target, otherwise the component API is returned.
    *
    * @param subsystem         the component's subsystem
    * @param versionOpt        the subsystem version (or use current)
    * @param compNames         the component names
    * @param targetSubsystem   defines the optional target subsystem and version
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @return list of component info
    */
  private def icdComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion,
                               targetCompNameOpt: Option[String] = None): List[ComponentInfo] = {
    targetSubsystem.subsystemOpt match {
      case None =>
        ComponentInfoHelper.getComponentInfoList(db, subsystem, versionOpt, compNames)
      case Some(target) =>
        IcdComponentInfo.getComponentInfoList(db, subsystem, versionOpt, compNames, target,
          targetSubsystem.versionOpt, targetCompNameOpt)
    }
  }


  /**
    * Returns an HTML document describing the given components in the given subsystem.
    *
    * @param compNames the names of the components
    * @param sv        the selected subsystem and version
    */
  def getApiAsHtml(compNames: List[String], sv: SubsystemWithVersion): Option[String] = {
    val markup = for {
      subsystem <- sv.subsystemOpt
      subsystemInfo <- getSubsystemInfo(subsystem, sv.versionOpt)
    } yield {
      val tv = SubsystemWithVersion(None, None)
      val infoList = getComponentInfo(subsystem, sv.versionOpt, compNames, tv, None)
      IcdToHtml.getApiAsHtml(Some(subsystemInfo), infoList)
    }
    markup.map(_.render)
  }


  /**
    * Returns an HTML document describing the given components in the given subsystem.
    * If a target subsystem is given, the information is restricted to the ICD from
    * the subsystem to the target.
    *
    * For ICDs, the complete document consists of two parts: subsystem to target and target to subsystem.
    *
    * @param compNames         the names of the subsystem components to include in the document
    * @param sv                the selected subsystem and version
    * @param tv                the target subsystem and version
    * @param icdVersionOpt     optional ICD version, to be displayed in the title
    * @param targetCompNameOpt optional name of target subsystem component (default: Use all target components)
    */
  def getIcdAsHtml(compNames: List[String],
                   sv: SubsystemWithVersion,
                   tv: SubsystemWithVersion,
                   icdVersionOpt: Option[IcdVersion],
                   targetCompNameOpt: Option[String] = None): Option[String] = {

    val markup = for {
      subsystem <- sv.subsystemOpt
      subsystemInfo <- getSubsystemInfo(subsystem, sv.versionOpt)
      targetSubsystem <- tv.subsystemOpt
      targetSubsystemInfo <- getSubsystemInfo(targetSubsystem, tv.versionOpt)
    } yield {
      import scalatags.Text.all._
      val targetCompNames = if (targetCompNameOpt.isDefined)
        targetCompNameOpt.toList
      else
        db.versionManager.getComponentNames(targetSubsystem, tv.versionOpt)

      val infoList = getComponentInfo(subsystem, sv.versionOpt, compNames, tv, targetCompNameOpt)
      val titleInfo = TitleInfo(subsystemInfo, tv, icdVersionOpt)
      val titleInfo1 = TitleInfo(subsystemInfo, tv, icdVersionOpt, "(Part 1)")
      val infoList2 = getComponentInfo(targetSubsystem, tv.versionOpt, targetCompNames, sv, None)
      val titleInfo2 = TitleInfo(targetSubsystemInfo, sv, icdVersionOpt, "(Part 2)")
      val nh = new NumberedHeadings
      val subsystemVersion = subsystemInfo.versionOpt.getOrElse(unpublished)
      val targetSubsystemVersion = targetSubsystemInfo.versionOpt.getOrElse(unpublished)
      val mainContent = div(
        p(strong(s"${subsystemInfo.subsystem}: ${subsystemInfo.title} $subsystemVersion")),
        raw(subsystemInfo.description),
        p(strong(s"${targetSubsystemInfo.subsystem}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
        raw(targetSubsystemInfo.description),
        SummaryTable.displaySummary(subsystemInfo, Some(targetSubsystem), infoList, nh),
        makeIntro(titleInfo1),
        displayDetails(infoList, nh, forApi = false),
        makeIntro(titleInfo2),
        displayDetails(infoList2, nh, forApi = false)
      )
      val toc = nh.mkToc()

      html(
        head(
          scalatags.Text.tags2.title(titleInfo.title),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
        ),
        body(
          getTitleMarkup(titleInfo),
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          toc,
          div(cls := "pagebreakBefore"),
          getTitleMarkup(titleInfo),
          mainContent
        )
      )
    }
    markup.map(_.render)
  }

  /**
    * Saves a document describing the ICD for the given component to the given file,
    * in a format determined by the file's suffix, which should be one of (html, pdf).
    *
    * @param subsystemStr      the name of the subsystem (or component's subsystem) to print, followed by optional :version
    * @param compNamesOpt      optional names of the component to print (separated by ",")
    * @param targetOpt         optional target subsystem, followed by optional :version
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @param icdVersionOpt     optional icd version (overrides source and target subsystem versions)
    * @param file              the file in which to save the document (should end with .html or .pdf)
    */
  def saveToFile(subsystemStr: String, compNamesOpt: Option[String],
                 targetOpt: Option[String], targetCompNameOpt: Option[String],
                 icdVersionOpt: Option[String], file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html, showLogo = true)

    val s1 = IcdVersionManager.SubsystemAndVersion(subsystemStr)

    val (subsys, targ, icdV) = targetOpt match {
      case Some(t) => // ICD
        val s2 = IcdVersionManager.SubsystemAndVersion(t)
        // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
        // if only the subsystem or target versions were given, use those (default to latest versions)
        val v = icdVersionOpt.getOrElse("*")
        val iv = db.versionManager.getIcdVersions(s1.subsystem, s2.subsystem).find(_.icdVersion.icdVersion == v).map(_.icdVersion)
        val (sv, tv) = if (iv.isDefined) {
          val i = iv.get
          (SubsystemWithVersion(Some(i.subsystem), Some(i.subsystemVersion)), SubsystemWithVersion(Some(i.target), Some(i.targetVersion)))
        } else {
          (SubsystemWithVersion(Some(s1.subsystem), s1.versionOpt), SubsystemWithVersion(Some(s2.subsystem), s2.versionOpt))
        }
        (sv, tv, iv)

      case None => // API
        val sv = SubsystemWithVersion(Some(s1.subsystem), s1.versionOpt)
        val tv = SubsystemWithVersion(None, None)
        (sv, tv, None)
    }

    val compNames = compNamesOpt match {
      case Some(str) => str.split(",").toList
      case None => db.versionManager.getComponentNames(s1.subsystem, s1.versionOpt)
    }

    val htmlOpt = if (targetOpt.isDefined) {
      getIcdAsHtml(compNames, subsys, targ, icdV, targetCompNameOpt)
    } else {
      getApiAsHtml(compNames, subsys)
    }

    htmlOpt match {
      case Some(html) =>
        file.getName.split('.').drop(1).lastOption match {
          case Some("html") => saveAsHtml(html)
          case Some("pdf") => saveAsPdf(html)
          case _ => println(s"Unsupported output format: Expected *.html or *.pdf")
        }
      case None =>
        println(s"Failed to generate $file. You might need to run: 'icd-git --ingest' first to update the database.")
    }
  }
}
