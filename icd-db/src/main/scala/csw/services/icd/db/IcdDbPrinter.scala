package csw.services.icd.db

import java.io.{ FileOutputStream, File }

import csw.services.icd.{ IcdToPdf, IcdToHtml }
import icd.web.shared._

import scalatags.Text

/**
 * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
 * @param db used to query the database
 */
case class IcdDbPrinter(db: IcdDb) {

  // Note: I thought about sharing parts of this code with the scala.js client, but
  // here we import scalatags.Text.all._ and in scala.js its scalatags.JsDom.all._.
  // The difference is that here we generate plain HTML text, while in scala.js you can
  // create a DOM structure with event handlers, etc.

  private def getTitleMarkup(titleInfo: TitleInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    titleInfo.subtitleOpt match {
      case Some(subtitle) ⇒
        h3(a(name := "title"), cls := "page-header")(titleInfo.title, br, small(subtitle))
      case None ⇒
        h3(a(name := "title"), cls := "page-header")(titleInfo.title)
    }
  }

  private def publishId(compName: String): String = s"pub-$compName"

  private def publishTitle(compName: String): String = s"Items published by $compName"

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, pubInfo: List[PublishInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    // Only display non-empty tables
    if (pubInfo.isEmpty) div()
    else div(cls := "nopagebreak")(
      h3(a(name := publishId(compName))(publishTitle(compName))),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Type"),
            th("Description"),
            th("Subscribers"))),
        tbody(
          for (p ← pubInfo) yield {
            tr(
              td(p.name),
              td(p.itemType),
              td(p.description),
              td(p.subscribers.map(_.compName).mkString(", ")))
          })))
  }

  private def subscribeId(compName: String): String = s"sub-$compName"

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subInfo: List[SubscribeInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    if (subInfo.isEmpty) div()
    else div(cls := "nopagebreak")(
      h3(a(name := subscribeId(compName))(subscribeTitle(compName))),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Prefix.Name"),
            th("Type"),
            th("Description"),
            th("Publisher"))),
        tbody(
          for (s ← subInfo) yield {
            val path = s.name.split('.')
            val prefix = path.dropRight(1).mkString(".")
            val name = path.last
            tr(
              td(prefix, br, s".$name"),
              td(s.itemType),
              td(s.description),
              td(s.compName))
          })))
  }

  private def receivedCommandsId(compName: String): String = s"rec-$compName"

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[CommandInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(cls := "nopagebreak")(
      h3(a(name := receivedCommandsId(compName))(receivedCommandsTitle(compName))),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Senders"))),
        tbody(
          for (p ← info) yield {
            tr(
              td(p.name), // XXX TODO: Make link to command description page with details
              td(p.description),
              td(p.otherComponents.map(_.compName).mkString(", ")))
          })))
  }

  private def sentCommandsId(compName: String): String = s"sent-$compName"

  private def sentCommandsTitle(compName: String): String = s"Command Configurations Sent by $compName"

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[CommandInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(cls := "nopagebreak")(
      h3(a(name := sentCommandsId(compName))(sentCommandsTitle(compName))),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Receiver"))),
        tbody(
          for (p ← info) yield {
            tr(
              td(p.name), // XXX TODO: Make link to command description page with details
              td(p.description),
              td(p.otherComponents.map(_.compName).mkString(", ")))
          })))
  }

  // Generates a one line table with basic component informationdiv(
  private def componentInfoTableMarkup(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Subsystem"),
            th("Name"),
            th("Prefix"),
            th("Type"),
            th("WBS ID"))),
        tbody(
          tr(
            td(info.subsystem),
            td(info.compName),
            td(info.prefix),
            td(info.componentType),
            td(info.wbsId)))))
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "pagebreakBefore")(
      h2(a(name := info.compName)(info.title)),
      p(info.description),
      componentInfoTableMarkup(info),
      publishMarkup(info.compName, info.publishInfo),
      subscribeMarkup(info.compName, info.subscribeInfo),
      receivedCommandsMarkup(info.compName, info.commandsReceived),
      sentCommandsMarkup(info.compName, info.commandsSent))
  }

  /**
   * Displays the information for a component, appending to the other selected components, if any.
   * @param info contains the information to display
   */
  private def displayComponentInfo(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (info.publishInfo.nonEmpty || info.subscribeInfo.nonEmpty || info.commandsReceived.nonEmpty || info.commandsSent.nonEmpty) {
      markupForComponent(info)
    } else div()
  }

  /**
   * Gets information about a named subsystem
   */
  private def getSubsystemInfo(subsystem: String, versionOpt: Option[String]): Option[SubsystemInfo] =
    db.versionManager.getSubsystemModel(subsystem, versionOpt)
      .map(m ⇒ SubsystemInfo(m.subsystem, versionOpt, m.title, m.description))

  /**
   * Gets information about the given components
   * @param subsystem the components' subsystem
   * @param versionOpt optional version (default: current version)
   * @param compNames list of component names
   * @param targetSubsystem optional target subsystem and version
   * @return future list of objects describing the components
   */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion): List[ComponentInfo] = {
    for {
      info ← icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem)
    } yield {
      // If there is a target subsystem, filter out any items not referenced by it
      if (targetSubsystem.subsystemOpt.isDefined) applyIcdFilter(info) else info
    }
  }

  /**
   * For ICDs, we are only interested in the interface between the two subsystems.
   * Filter out any published commands with no subscribers, and any commands received,
   * with no senders
   * @param info component info to filter
   * @return the filtered info
   */
  private def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val publishInfo = info.publishInfo.filter(p ⇒ p.subscribers.nonEmpty)
    val commandsReceived = info.commandsReceived.filter(p ⇒ p.otherComponents.nonEmpty)
    ComponentInfo(info.subsystem, info.compName, info.title, info.description, info.prefix,
      info.componentType, info.wbsId, publishInfo, info.subscribeInfo, commandsReceived, info.commandsSent)
  }

  /**
   * Returns information for each component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param subsystem the component's subsystem
   * @param versionOpt the subsystem version (or use current)
   * @param compNames the component names
   * @param targetSubsystem defines the optional target subsystem and version
   * @return list of component info
   */
  private def icdComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion): List[ComponentInfo] = {
    targetSubsystem.subsystemOpt match {
      case None         ⇒ ComponentInfoHelper.getComponentInfoList(db, subsystem, versionOpt, compNames)
      case Some(target) ⇒ IcdComponentInfo.getComponentInfoList(db, subsystem, versionOpt, compNames, target, targetSubsystem.versionOpt)
    }
  }

  /**
   * Generates a TOC entry for a component
   */
  private def makeTocEntry(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    val compName = info.compName
    val sections = List(
      info.publishInfo.headOption.map(_ ⇒ li(a(href := "#" + publishId(compName))(publishTitle(compName)))),
      info.subscribeInfo.headOption.map(_ ⇒ li(a(href := "#" + subscribeId(compName))(subscribeTitle(compName)))),
      info.commandsReceived.headOption.map(_ ⇒ li(a(href := "#" + receivedCommandsId(compName))(receivedCommandsTitle(compName)))),
      info.commandsSent.headOption.map(_ ⇒ li(a(href := "#" + sentCommandsId(compName))(sentCommandsTitle(compName))))).flatten

    li(a(href := s"#$compName")(info.title), ul(sections))
  }

  /**
   * Generates the table of contents based on the list of component info
   */
  private def makeToc(titleStr: String, infoList: List[ComponentInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    ul(li(a(href := "#title")(titleStr), ul(infoList.map(makeTocEntry))))
  }

  /**
   * Displays the subsystem title and description
   */
  private def makeIntro(titleInfo: TitleInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (titleInfo.descriptionOpt.isDefined) {
      p(titleInfo.descriptionOpt.get)
    } else div
  }

  /**
   * Returns an HTML document describing the given components in the given subsystem.
   * If a target subsystem is given, the information is restricted to the ICD from
   * the subsystem to the target.
   * @param compNames the names of the components
   * @param sv the selected subsystem and version
   * @param targetSubsystem the target subsystem (might not be set)
   * @param icdVersionOpt optional ICD version, to be displayed in the title
   */
  def getAsHtml(compNames: List[String],
                sv: SubsystemWithVersion,
                targetSubsystem: SubsystemWithVersion,
                icdVersionOpt: Option[IcdVersion]): Option[String] = {
    val markup = for {
      subsystem ← sv.subsystemOpt
      subsystemInfo ← getSubsystemInfo(subsystem, sv.versionOpt)
    } yield {
      import scalatags.Text.all._
      val infoList = getComponentInfo(subsystem, sv.versionOpt, compNames, targetSubsystem)
      val titleInfo = TitleInfo(subsystemInfo, targetSubsystem, icdVersionOpt)
      html(
        head(
          scalatags.Text.tags2.title(titleInfo.title),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))),
        body(
          getTitleMarkup(titleInfo),
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          makeToc(titleInfo.title, infoList),
          makeIntro(titleInfo),
          infoList.map(displayComponentInfo)))
    }
    markup.map(_.render)
  }

  /**
   * Saves a document describing the ICD for the given component to the given file,
   * in a format determined by the file's suffix, which should be one of (html, pdf).
   *
   * @param subsystemStr the name of the subsystem (or component's subsystem) to print, followed by optional :version
   * @param compNamesOpt optional names of the component to print (separated by ",")
   * @param targetOpt optional target subsystem, followed by optional :version
   * @param icdVersionOpt optional icd version (overrides source and target subsystem versions)
   * @param file the file in which to save the document (should end with .md, .html or .pdf)
   */
  def saveToFile(subsystemStr: String, compNamesOpt: Option[String],
                 targetOpt: Option[String], icdVersionOpt: Option[String], file: File): Unit = {

    // Gets the subsystem and optional version, if defined
    def getSubsystemAndVersion(s: String): (String, Option[String]) = {
      if (s.contains(':')) {
        val ar = s.split(':')
        (ar(0), Some(ar(1)))
      } else (s, None)
    }

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html)

    // ---

    val (subsystem, versionOpt) = getSubsystemAndVersion(subsystemStr)

    val compNames = compNamesOpt match {
      case Some(str) ⇒ str.split(",").toList
      case None      ⇒ db.versionManager.getComponentNames(subsystem, versionOpt)
    }

    val (subsys, targ, icdV) = targetOpt match {
      case Some(t) ⇒ // ICD
        val (target, targetVersionOpt) = getSubsystemAndVersion(t)
        // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
        // if only the subsystem or target versions were given, use those (default to latest versions)
        val v = icdVersionOpt.getOrElse("*")
        val iv = db.versionManager.getIcdVersions(subsystem, target).find(_.icdVersion.icdVersion == v).map(_.icdVersion)
        val (sv, tv) = if (iv.isDefined) {
          val i = iv.get
          (SubsystemWithVersion(Some(i.subsystem), Some(i.subsystemVersion)), SubsystemWithVersion(Some(i.target), Some(i.targetVersion)))
        } else {
          (SubsystemWithVersion(Some(subsystem), versionOpt), SubsystemWithVersion(Some(target), targetVersionOpt))
        }
        (sv, tv, iv)

      case None ⇒ // API
        val sv = SubsystemWithVersion(Some(subsystem), versionOpt)
        val tv = SubsystemWithVersion(None, None)
        (sv, tv, None)
    }

    IcdDbPrinter(db).getAsHtml(compNames, subsys, targ, icdV) match {
      case Some(html) ⇒
        file.getName.split('.').drop(1).lastOption match {
          case Some("html") ⇒ saveAsHtml(html)
          case Some("pdf")  ⇒ saveAsPdf(html)
          case _            ⇒ println(s"Unsupported output format: Expected *.html or *.pdf")
        }
      case None ⇒
        println("Please specify source and optionally target subsystems to print")
    }
  }
}
