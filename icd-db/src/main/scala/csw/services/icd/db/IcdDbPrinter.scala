package csw.services.icd.db

import java.io.{FileOutputStream, File}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{HtmlMarkup, IcdToHtml}
import icd.web.shared._

import scalatags.Text

/**
 * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
 *
 * @param db used to query the database
 */
case class IcdDbPrinter(db: IcdDb) {

  // Note: You would think we could share parts of this code with the scala.js client, but
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

  private def attributeListMarkup(nameStr: String, attributesList: List[AttributeInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a ← attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue)
      div(cls := "nopagebreak")(
        h5(s"Attributes for $nameStr"),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  private def parameterListMarkup(nameStr: String, attributesList: List[AttributeInfo], requiredArgs: List[String]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList = for (a ← attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue,
        HtmlMarkup.yesNo(requiredArgs.contains(a.name)))
      div(cls := "nopagebreak")(
        h5(s"Arguments for $nameStr"),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, publishesOpt: Option[Publishes]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]): Text.TypedTag[String] = {
      if (telemetryList.isEmpty) div()
      else {
        div(cls := "nopagebreak")(
          h4(a(s"$pubType Published by $compName")),
          for (t ← telemetryList) yield {
            val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate", "Subscribers")
            val rowList = List(List(HtmlMarkup.formatRate(t.minRate), HtmlMarkup.formatRate(t.maxRate), HtmlMarkup.yesNo(t.archive),
              HtmlMarkup.formatRate(t.archiveRate), t.subscribers.map(_.compName).mkString(", ")))
            div(cls := "nopagebreak")(
              h5(a(s"$pubType: ${t.name}")),
              if (t.requirements.isEmpty) div() else p(strong("Requirements: "), t.requirements.mkString(", ")),
              raw(t.description),
              HtmlMarkup.mkTable(headings, rowList),
              attributeListMarkup(t.name, t.attributesList), hr
            )
          }
        )
      }
    }

    def publishAlarmListMarkup(alarmList: List[AlarmInfo]): Text.TypedTag[String] = {
      if (alarmList.isEmpty) div()
      else {
        div(cls := "nopagebreak")(
          h4(a(s"Alarms Published by $compName")),
          for (t ← alarmList) yield {
            val headings = List("Severity", "Archive", "Subscribers")
            val rowList = List(List(t.severity, HtmlMarkup.yesNo(t.archive), t.subscribers.map(_.compName).mkString(", ")))
            div(cls := "nopagebreak")(
              h5(a(s"Alarm: ${t.name}")),
              if (t.requirements.isEmpty) div() else p(strong("Requirements: "), t.requirements.mkString(", ")),
              raw(t.description),
              HtmlMarkup.mkTable(headings, rowList), hr
            )
          }
        )
      }
    }

    publishesOpt match {
      case None ⇒ div()
      case Some(publishes) ⇒
        if (publishesOpt.nonEmpty && publishesOpt.get.nonEmpty) {
          div(cls := "nopagebreak")(
            h3(a(name := publishId(compName))(publishTitle(compName))),
            raw(publishes.description), hr,
            publishTelemetryListMarkup("Telemetry", publishes.telemetryList),
            publishTelemetryListMarkup("Events", publishes.eventList),
            publishTelemetryListMarkup("Event Streams", publishes.eventStreamList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        } else div()
    }
  }

  private def subscribeId(compName: String): String = s"sub-$compName"

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    def subscribeListMarkup(pubType: String, subscribeList: List[SubscribeInfo]): Text.TypedTag[String] = {
      if (subscribeList.isEmpty) div()
      else div(cls := "nopagebreak")(
        h4(a(s"$pubType Subscribed to by $compName")),
        for (si ← subscribeList) yield {
          div(cls := "nopagebreak")(
            h5(a(s"$pubType: ${si.name}")),
            raw(si.description),
            if (si.usage.isEmpty) div() else div(strong("Usage:"), raw(si.usage)),
            table(
              thead(
                tr(th("Subsystem"), th("Component"), th("Prefix.Name"), th("Required Rate"), th("Max Rate"))
              ),
              tbody(
                tr(td(si.subsystem), td(si.compName), td(si.path), td(si.requiredRate), td(si.maxRate))
              )
            )
          )
        }
      )
    }

    subscribesOpt match {
      case None ⇒ div()
      case Some(subscribes) ⇒
        if (subscribes.subscribeInfo.nonEmpty) {
          div(cls := "nopagebreak")(
            h3(a(name := subscribeId(compName))(subscribeTitle(compName))),
            raw(subscribes.description),
            subscribeListMarkup("Telemetry", subscribes.subscribeInfo.filter(_.itemType == "Telemetry")),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == "Events")),
            subscribeListMarkup("Event Streams", subscribes.subscribeInfo.filter(_.itemType == "EventStreams")),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == "Alarms"))
          )
        } else div()
    }
  }

  private def receivedCommandsId(compName: String): String = s"rec-$compName"

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (info.isEmpty) div()
    else {
      div(cls := "nopagebreak")(
        h4(a(name := receivedCommandsId(compName))(receivedCommandsTitle(compName))),
        for (r ← info) yield {
          div(cls := "nopagebreak")(
            h5(a(s"Configuration: ${r.name}")),
            if (r.requirements.isEmpty) div() else p(strong("Requirements: "), r.requirements.mkString(", ")),
            raw(r.description),
            if (r.args.isEmpty) div() else parameterListMarkup(r.name, r.args, r.requiredArgs)
          )
        }
      )
    }
  }

  private def sentCommandsId(compName: String): String = s"sent-$compName"

  private def sentCommandsTitle(compName: String): String = s"Command Configurations Sent by $compName"

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[SentCommandInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(cls := "nopagebreak")(
      h4(a(name := sentCommandsId(compName))(sentCommandsTitle(compName))),
      table(
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Receiver")
          )
        ),
        tbody(
          for (s ← info) yield {
            tr(
              td(p(s.name)), // XXX TODO: Make link to command description page with details
              td(raw(s.description)),
              td(p(s.receivers.map(_.compName).mkString(", ")))
            )
          }
        )
      )
    )
  }

  private def commandsId(compName: String): String = s"commands-$compName"

  private def commandsTitle(compName: String): String = s"Commands for $compName"

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    commandsOpt match {
      case None ⇒ div()
      case Some(commands) ⇒
        if (commands.commandsReceived.nonEmpty || commands.commandsSent.nonEmpty) {
          div(cls := "nopagebreak")(
            h3(a(name := commandsId(compName))(commandsTitle(compName))),
            raw(commands.description),
            receivedCommandsMarkup(compName, commands.commandsReceived),
            sentCommandsMarkup(compName, commands.commandsSent)
          )
        } else div()
    }
  }

  // Generates a one line table with basic component informationdiv(
  private def componentInfoTableMarkup(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      table(
        thead(
          tr(
            th("Subsystem"),
            th("Name"),
            th("Prefix"),
            th("Type"),
            th("WBS ID")
          )
        ),
        tbody(
          tr(
            td(info.subsystem),
            td(info.compName),
            td(info.prefix),
            td(info.componentType),
            td(info.wbsId)
          )
        )
      )
    )
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "pagebreakBefore")(
      h2(a(name := info.compName)(info.title)),
      componentInfoTableMarkup(info),
      raw(info.description),
      publishMarkup(info.compName, info.publishes),
      subscribeMarkup(info.compName, info.subscribes),
      commandsMarkup(info.compName, info.commands)
    )
  }

  /**
   * Displays the information for a component, appending to the other selected components, if any.
   *
   * @param info contains the information to display
   */
  private def displayComponentInfo(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty || info.commands.get.commandsSent.nonEmpty)) {
      markupForComponent(info)
    } else div()
  }

  /**
   * Gets information about a named subsystem
   */
  private def getSubsystemInfo(subsystem: String, versionOpt: Option[String]): Option[SubsystemInfo] =
    db.versionManager.getSubsystemModel(subsystem, versionOpt)
      .map(m ⇒ SubsystemInfo(m.subsystem, versionOpt, m.title, HtmlMarkup.gfmToHtml(m.description)))

  /**
   * Gets information about the given components
   *
   * @param subsystem       the components' subsystem
   * @param versionOpt      optional version (default: current version)
   * @param compNames       list of component names
   * @param targetSubsystem optional target subsystem and version
   * @return future list of objects describing the components
   */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion): List[ComponentInfo] = {
    for {
      info ← icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem)
    } yield {
      // If there is a target subsystem, filter out any items not referenced by it
      if (targetSubsystem.subsystemOpt.isDefined) ComponentInfo.applyIcdFilter(info) else info
    }
  }

  /**
   * Returns information for each component.
   * If the target subsystem is defined, the information is restricted to the ICD
   * from subsystem to target, otherwise the component API is returned.
   *
   * @param subsystem       the component's subsystem
   * @param versionOpt      the subsystem version (or use current)
   * @param compNames       the component names
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
    val commandsReceived = info.commands.toList.flatMap(_.commandsReceived)
    val commandsSent = info.commands.toList.flatMap(_.commandsSent)
    val sections = List(
      info.publishes.map(_ ⇒ li(a(href := "#" + publishId(compName))(publishTitle(compName)))),
      info.subscribes.map(_ ⇒ li(a(href := "#" + subscribeId(compName))(subscribeTitle(compName)))),
      info.commands.map(_ ⇒ li(a(href := "#" + commandsId(compName))(commandsTitle(compName)), ul(
        commandsReceived.headOption.map(_ ⇒ li(a(href := "#" + receivedCommandsId(compName))(receivedCommandsTitle(compName)))),
        commandsSent.headOption.map(_ ⇒ li(a(href := "#" + sentCommandsId(compName))(sentCommandsTitle(compName))))
      )))
    ).flatten

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
      div(raw(titleInfo.descriptionOpt.get))
    } else div
  }

  /**
   * Returns an HTML document describing the given components in the given subsystem.
   * If a target subsystem is given, the information is restricted to the ICD from
   * the subsystem to the target.
   *
   * @param compNames       the names of the components
   * @param sv              the selected subsystem and version
   * @param targetSubsystem the target subsystem (might not be set)
   * @param icdVersionOpt   optional ICD version, to be displayed in the title
   */
  def getAsHtml(
    compNames:       List[String],
    sv:              SubsystemWithVersion,
    targetSubsystem: SubsystemWithVersion,
    icdVersionOpt:   Option[IcdVersion]
  ): Option[String] = {
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
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
        ),
        body(
          getTitleMarkup(titleInfo),
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          makeToc(titleInfo.title, infoList),
          makeIntro(titleInfo),
          infoList.map(displayComponentInfo)
        )
      )
    }
    markup.map(_.render)
  }

  /**
   * Saves a document describing the ICD for the given component to the given file,
   * in a format determined by the file's suffix, which should be one of (html, pdf).
   *
   * @param subsystemStr  the name of the subsystem (or component's subsystem) to print, followed by optional :version
   * @param compNamesOpt  optional names of the component to print (separated by ",")
   * @param targetOpt     optional target subsystem, followed by optional :version
   * @param icdVersionOpt optional icd version (overrides source and target subsystem versions)
   * @param file          the file in which to save the document (should end with .md, .html or .pdf)
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
