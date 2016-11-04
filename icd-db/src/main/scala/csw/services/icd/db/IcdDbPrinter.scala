package csw.services.icd.db

import java.io.{FileOutputStream, File}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{HtmlMarkup, IcdToHtml}
import icd.web.shared.ComponentInfo.{Alarms, EventStreams, Events, Telemetry}
import icd.web.shared.IcdModels.AttributeModel
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

  private def getTitleMarkup(titleInfo: TitleInfo, titleName: String = "title"): Text.TypedTag[String] = {
    import scalatags.Text.all._
    titleInfo.subtitleOpt match {
      case Some(subtitle) =>
        h3(a(name := titleName), cls := "page-header")(titleInfo.title, br, small(subtitle))
      case None =>
        h3(a(name := titleName), cls := "page-header")(titleInfo.title)
    }
  }

  private def publishId(compName: String): String = s"pub-$compName"

  private def publishTitle(compName: String): String = s"Items published by $compName"

  private def attributeListMarkup(nameStr: String, attributesList: List[AttributeModel]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue)
      div(cls := "nopagebreak")(
        h5(s"Attributes for $nameStr"),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  private def parameterListMarkup(nameStr: String, attributesList: List[AttributeModel], requiredArgs: List[String]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue,
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
        div(
          h4(a(s"$pubType Published by $compName")),
          for (t <- telemetryList) yield {
            val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate")
            val rowList = List(List(HtmlMarkup.formatRate(t.telemetryModel.minRate), HtmlMarkup.formatRate(t.telemetryModel.maxRate),
              HtmlMarkup.yesNo(t.telemetryModel.archive),
              HtmlMarkup.formatRate(t.telemetryModel.archiveRate)))
            val subscribers = t.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberDiv = if (t.subscribers.isEmpty) div() else p(strong("Subscribers: "), subscribers)
            div(cls := "nopagebreak")(
              h5(a(s"$compName publishes ${singlePubType(pubType)}: ${t.telemetryModel.name}")),
              if (t.telemetryModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.telemetryModel.requirements.mkString(", ")),
              subscriberDiv,
              raw(t.telemetryModel.description),
              HtmlMarkup.mkTable(headings, rowList),
              attributeListMarkup(t.telemetryModel.name, t.telemetryModel.attributesList), hr
            )
          }
        )
      }
    }

    def publishAlarmListMarkup(alarmList: List[AlarmInfo]): Text.TypedTag[String] = {
      if (alarmList.isEmpty) div()
      else {
        div(
          h4(a(s"Alarms Published by $compName")),
          for (t <- alarmList) yield {
            val headings = List("Severity", "Archive", "Subscribers")
            val rowList = List(List(t.alarmModel.severity, HtmlMarkup.yesNo(t.alarmModel.archive),
              t.subscribers.map(_.subscribeModelInfo.component).mkString(", ")))
            div(cls := "nopagebreak")(
              h5(a(s"$compName publishes Alarm: ${t.alarmModel.name}")),
              if (t.alarmModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.alarmModel.requirements.mkString(", ")),
              raw(t.alarmModel.description),
              HtmlMarkup.mkTable(headings, rowList), hr
            )
          }
        )
      }
    }

    publishesOpt match {
      case None => div()
      case Some(publishes) =>
        if (publishesOpt.nonEmpty && publishesOpt.get.nonEmpty) {
          div(
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

  private def singlePubType(pubType: String): String = {
    val s = pubType.toLowerCase()
    if (s.endsWith("s")) s.dropRight(1) else s
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]): Text.TypedTag[String] = {
      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) = info.warning.map { msg =>
        p(em(" Warning: ", msg))
      }

      if (subscribeList.isEmpty) div()
      else div(
        h4(a(s"$pubType Subscribed to by $compName")),
        for (si <- subscribeList) yield {
          val sInfo = si.subscribeModelInfo
          val from = s"from ${si.subscribeModelInfo.subsystem}.${si.subscribeModelInfo.component}"
          div(cls := "nopagebreak")(
            h5(a(s"$compName subscribes to ${singlePubType(pubType)}: ${sInfo.name} $from")),
            raw(si.description),
            getWarning(si),
            if (sInfo.usage.isEmpty) div() else div(strong("Usage:"), raw(sInfo.usage)),
            table(
              thead(
                tr(th("Subsystem"), th("Component"), th("Prefix.Name"), th("Required Rate"), th("Max Rate"))
              ),
              tbody(
                tr(td(sInfo.subsystem), td(sInfo.component), td(si.path), td(sInfo.requiredRate), td(sInfo.maxRate))
              )
            ),
            si.telemetryModel.map(t => attributeListMarkup(t.name, t.attributesList))
          )
        }
      )
    }

    subscribesOpt match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            h3(a(name := subscribeId(compName))(subscribeTitle(compName))),
            raw(subscribes.description),
            subscribeListMarkup("Telemetry", subscribes.subscribeInfo.filter(_.itemType == Telemetry)),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == Events)),
            subscribeListMarkup("Event Streams", subscribes.subscribeInfo.filter(_.itemType == EventStreams)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
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
      div(
        h4(a(name := receivedCommandsId(compName))(receivedCommandsTitle(compName))),
        for (r <- info) yield {
          val m = r.receiveCommandModel
          val from = r.senders.map(s => s"${s.subsystem}.${s.compName}").mkString(", ")
          val senders = if (from.isEmpty) div() else p(strong("Senders: "), from)
          div(cls := "nopagebreak")(
            h5(a(s"$compName receives configuration: ${m.name}")),
            senders,
            if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
            raw(m.description),
            if (m.args.isEmpty) div() else parameterListMarkup(m.name, m.args, m.requiredArgs)
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
    if (info.isEmpty) div()
    else {
      div(
        h4(a(name := sentCommandsId(compName))(sentCommandsTitle(compName))),
        for (s <- info) yield {
          val receiveCommandModel = s.receiveCommandModel
          val to = s.receiver.map(r => s"to ${r.subsystem}.${r.compName}").getOrElse("")
          div(cls := "nopagebreak")(
            h5(a(s"$compName sends configuration: ${s.name} $to")),
            receiveCommandModel match {
              case Some(m) => div(
                if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                raw(m.description),
                if (m.args.isEmpty) div() else parameterListMarkup(m.name, m.args, m.requiredArgs)
              )
              case None => s.warning.map(msg => p(em(" Warning: ", msg)))
            }
          )
        }
      )
    }
  }

  private def commandsId(compName: String): String = s"commands-$compName"

  private def commandsTitle(compName: String): String = s"Commands for $compName"

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    commandsOpt match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || commands.commandsSent.nonEmpty) {
          div(
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
            td(info.componentModel.subsystem),
            td(info.componentModel.component),
            td(info.componentModel.prefix),
            td(info.componentModel.componentType),
            td(info.componentModel.wbsId)
          )
        )
      )
    )
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "pagebreakBefore")(
      h2(a(name := info.componentModel.component)(info.componentModel.title)),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel.component, info.publishes),
      subscribeMarkup(info.componentModel.component, info.subscribes),
      commandsMarkup(info.componentModel.component, info.commands)
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
    .map(m => SubsystemInfo(m.subsystem, versionOpt, m.title, m.description))

  /**
    * Gets information about the given components
    *
    * @param subsystem       the components' subsystem
    * @param versionOpt      optional version (default: current version)
    * @param compNames       list of component names
    * @param targetSubsystem optional target subsystem and version
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @return future list of objects describing the components
    */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion,
                               targetCompNameOpt: Option[String]): List[ComponentInfo] = {
    for {
      info <- icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem, targetCompNameOpt)
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
    * Generates a TOC entry for a component
    */
  private def makeTocEntry(info: ComponentInfo, forApi: Boolean = true): Text.TypedTag[String] = {
    import scalatags.Text.all._
    val compName = info.componentModel.component
    val commandsReceived = info.commands.toList.flatMap(_.commandsReceived)
    val commandsSent = info.commands.toList.flatMap(_.commandsSent)
    val sections = List(
      info.publishes.map(x => if (x.nonEmpty) li(a(href := "#" + publishId(compName))(publishTitle(compName))) else span()),
      info.subscribes.map(_ => li(a(href := "#" + subscribeId(compName))(subscribeTitle(compName)))),
      info.commands.map(x => if (x.nonEmpty) li(a(href := "#" + commandsId(compName))(commandsTitle(compName)), ul(
        commandsReceived.headOption.map(_ => li(a(href := "#" + receivedCommandsId(compName))(receivedCommandsTitle(compName)))),
        commandsSent.headOption.map(_ => li(a(href := "#" + sentCommandsId(compName))(sentCommandsTitle(compName))))
      )) else span())
    ).flatten

    if (forApi || (info.publishes.isDefined && info.publishes.get.nonEmpty)
      || info.subscribes.isDefined || commandsReceived.nonEmpty || commandsSent.nonEmpty)
      li(a(href := s"#$compName")(info.componentModel.title), ul(sections))
    else span()
  }

  /**
    * Generates the table of contents for an API document based on the list of component info
    */
  private def makeToc(titleStr: String, infoList: List[ComponentInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    ul(li(a(href := "#title")(titleStr), ul(infoList.map(makeTocEntry(_)))))
  }

  /**
    * Generates the table of contents for an ICD document based on the list of component info
    */
  private def makeToc(titleStr1: String,
                      titleStr2: String,
                      infoList1: List[ComponentInfo],
                      infoList2: List[ComponentInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    ul(
      li(
        a(href := "#title")(titleStr1),
        ul(infoList1.map(makeTocEntry(_, forApi = false))),
        a(href := "#title2")(titleStr2),
        ul(infoList2.map(makeTocEntry(_, forApi = false)))
      )
    )
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
    *
    * @param compNames       the names of the components
    * @param sv              the selected subsystem and version
    */
  def getApiAsHtml(compNames: List[String], sv: SubsystemWithVersion): Option[String] = {
    val tv = SubsystemWithVersion(None, None)
    val markup = for {
      subsystem <- sv.subsystemOpt
      subsystemInfo <- getSubsystemInfo(subsystem, sv.versionOpt)
    } yield {
      import scalatags.Text.all._
      val infoList = getComponentInfo(subsystem, sv.versionOpt, compNames, tv, None)
      val titleInfo = TitleInfo(subsystemInfo, tv, None)
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
    * Returns an HTML document describing the given components in the given subsystem.
    * If a target subsystem is given, the information is restricted to the ICD from
    * the subsystem to the target.
    *
    * For ICDs, the complete document consists of two parts: subsystem to target and target to subsystem.
    *
    * @param compNames       the names of the subsystem components to include in the document
    * @param sv              the selected subsystem and version
    * @param tv              the target subsystem and version
    * @param icdVersionOpt   optional ICD version, to be displayed in the title
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
      // XXX TODO: Fix targetCompNameOpt handling
      val targetCompNames = if (targetCompNameOpt.isDefined)
        targetCompNameOpt.toList
      else
        db.versionManager.getComponentNames(targetSubsystem, tv.versionOpt)

      val infoList = getComponentInfo(subsystem, sv.versionOpt, compNames, tv, targetCompNameOpt)
      val titleInfo = TitleInfo(subsystemInfo, tv, icdVersionOpt)
      val titleInfo1 = TitleInfo(subsystemInfo, tv, icdVersionOpt, "(Part 1)")
      val infoList2 = getComponentInfo(targetSubsystem, tv.versionOpt, targetCompNames, sv, None)
      val titleInfo2 = TitleInfo(targetSubsystemInfo, sv, icdVersionOpt, "(Part 2)")
      html(
        head(
          scalatags.Text.tags2.title(titleInfo.title),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
        ),
        body(
          getTitleMarkup(titleInfo),
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          makeToc(titleInfo1.title, titleInfo2.title, infoList, infoList2),

          // ICD from subsystem to target
          makeIntro(titleInfo1),
          infoList.map(displayComponentInfo),

          // ICD from target to subsystem
          getTitleMarkup(titleInfo2, "title2"),
          makeIntro(titleInfo2),
          infoList2.map(displayComponentInfo)
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
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @param icdVersionOpt optional icd version (overrides source and target subsystem versions)
    * @param file          the file in which to save the document (should end with .html or .pdf)
    */
  def saveToFile(subsystemStr: String, compNamesOpt: Option[String],
                 targetOpt: Option[String], targetCompNameOpt: Option[String],
                 icdVersionOpt: Option[String], file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html)

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

    val icdPrinter = IcdDbPrinter(db)
    val htmlOpt = if (targetOpt.isDefined) {
      icdPrinter.getIcdAsHtml(compNames, subsys, targ, icdV, targetCompNameOpt)
    } else {
      icdPrinter.getApiAsHtml(compNames, subsys)
    }

    htmlOpt match {
      case Some(html) =>
        file.getName.split('.').drop(1).lastOption match {
          case Some("html") => saveAsHtml(html)
          case Some("pdf") => saveAsPdf(html)
          case _ => println(s"Unsupported output format: Expected *.html or *.pdf")
        }
      case None =>
        println("Please specify source and optionally target subsystems to print")
    }
  }
}
