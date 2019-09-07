package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{IcdToHtml, NumberedHeadings}
import icd.web.shared.TitleInfo.unpublished
import icd.web.shared.{SubsystemWithVersion, _}
import IcdToHtml._


/**
 * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
 *
 * @param db used to query the database
 */
//noinspection DuplicatedCode
case class IcdDbPrinter(db: IcdDb) {

  /**
   * Gets information about a named subsystem
   */
  private def getSubsystemInfo(sv: SubsystemWithVersion): Option[SubsystemInfo] =
    db.versionManager.getSubsystemModel(sv)
      .map(m => SubsystemInfo(sv, m.title, m.description))

  /**
   * Gets information about the given components
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional target subsystem
   * @return future list of objects describing the components
   */
  private def getComponentInfo(sv: SubsystemWithVersion,
                               maybeTargetSv: Option[SubsystemWithVersion]): List[ComponentInfo] = {
    maybeTargetSv match {
      case Some(targetSv) => IcdComponentInfo.getComponentInfoList(db, sv, targetSv)
      case None => ComponentInfoHelper.getComponentInfoList(db, sv)
    }
  }

  /**
   * Returns an HTML document describing the given components in the given subsystem.
   *
   * @param sv the selected subsystem and version
   */
  def getApiAsHtml(sv: SubsystemWithVersion): Option[String] = {
    val markup = for {
      subsystemInfo <- getSubsystemInfo(sv)
    } yield {
      val infoList = getComponentInfo(sv, None)
      IcdToHtml.getApiAsHtml(Some(subsystemInfo), infoList)
    }
    markup.map(_.render)
  }


  /**
   * Returns an HTML document describing the the given subsystem.
   * If a target subsystem is given, the information is restricted to the ICD from
   * the subsystem to the target. If components are given, the output is restricted to
   * parts that related to the components.
   *
   * For ICDs, the complete document consists of two parts: subsystem to target and target to subsystem.
   *
   * @param sv              the selected subsystem and version
   * @param targetSv        the target subsystem and version
   * @param maybeIcdVersion optional ICD version, to be displayed in the title
   */
  def getIcdAsHtml(sv: SubsystemWithVersion,
                   targetSv: SubsystemWithVersion,
                   maybeIcdVersion: Option[IcdVersion]
                  ): Option[String] = {

    val markup = for {
      subsystemInfo <- getSubsystemInfo(sv)
      targetSubsystemInfo <- getSubsystemInfo(targetSv)
    } yield {
      import scalatags.Text.all._
      val infoList = getComponentInfo(sv, Some(targetSv))
      val titleInfo = TitleInfo(subsystemInfo, Some(targetSv), maybeIcdVersion)
      val titleInfo1 = TitleInfo(subsystemInfo, Some(targetSv), maybeIcdVersion, "(Part 1)")
      val infoList2 = getComponentInfo(targetSv, Some(sv))
      val titleInfo2 = TitleInfo(targetSubsystemInfo, Some(sv), maybeIcdVersion, "(Part 2)")
      val nh = new NumberedHeadings
      val subsystemVersion = subsystemInfo.sv.maybeVersion.getOrElse(unpublished)
      val targetSubsystemVersion = targetSubsystemInfo.sv.maybeVersion.getOrElse(unpublished)
      val mainContent = div(
        p(strong(s"${subsystemInfo.sv}: ${subsystemInfo.title} $subsystemVersion")),
        raw(subsystemInfo.description),
        p(strong(s"${targetSubsystemInfo.sv}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
        raw(targetSubsystemInfo.description),
        SummaryTable.displaySummary(subsystemInfo, Some(targetSv), infoList, nh),
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
   * @param subsystemStr         the name of the subsystem (or component's subsystem) to print, followed by optional :version
   * @param maybeComponent       optional names of the component to print (separated by ",")
   * @param maybeTarget          optional target subsystem, followed by optional :version
   * @param maybeTargetComponent optional name of target component (default is to use all target components)
   * @param maybeIcdVersion      optional icd version (overrides source and target subsystem versions)
   * @param file                 the file in which to save the document (should end with .html or .pdf)
   */
  def saveToFile(subsystemStr: String, maybeComponent: Option[String],
                 maybeTarget: Option[String], maybeTargetComponent: Option[String],
                 maybeIcdVersion: Option[String], file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html, showLogo = true)

    val s1 = IcdVersionManager.SubsystemAndVersion(subsystemStr)

    val (subsys, targ, icdV) = maybeTarget match {
      case Some(t) => // ICD
        val s2 = IcdVersionManager.SubsystemAndVersion(t)
        // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
        // if only the subsystem or target versions were given, use those (default to latest versions)
        val v = maybeIcdVersion.getOrElse("*")
        val iv = db.versionManager.getIcdVersions(s1.subsystem, s2.subsystem).find(_.icdVersion.icdVersion == v).map(_.icdVersion)
        val (sv, targetSv) = if (iv.isDefined) {
          val i = iv.get
          (Some(SubsystemWithVersion(i.subsystem, Some(i.subsystemVersion), maybeComponent)),
            Some(SubsystemWithVersion(i.target, Some(i.targetVersion), maybeTargetComponent)))
        } else {
          (Some(SubsystemWithVersion(s1.subsystem, s1.maybeVersion, maybeComponent)),
            Some(SubsystemWithVersion(s2.subsystem, s2.maybeVersion, maybeTargetComponent)))
        }
        (sv, targetSv, iv)

      case None => // API
        val sv = Some(SubsystemWithVersion(s1.subsystem, s1.maybeVersion, maybeComponent))
        val targetSv = None
        (sv, targetSv, None)
    }

    val maybeHtml = if (maybeTarget.isDefined) {
      getIcdAsHtml(subsys.get, targ.get, icdV)
    } else {
      getApiAsHtml(subsys.get)
    }

    maybeHtml match {
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
