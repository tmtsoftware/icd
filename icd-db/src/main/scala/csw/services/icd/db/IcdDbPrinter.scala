package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{HtmlMarkup, IcdToHtml, NumberedHeadings}
import icd.web.shared.ComponentInfo.{Alarms, EventStreams, Events, Telemetry}
import icd.web.shared.IcdModels.{AttributeModel, ComponentModel}
import icd.web.shared.TitleInfo.unpublished
import icd.web.shared._

import scalatags.Text
import Headings.idFor


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

  private def publishTitle(compName: String): String = s"Items published by $compName"

  private def attributeListMarkup(nameStr: String, attributesList: List[AttributeModel], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue)
      div(cls := "nopagebreak")(
        p(strong(a(s"Attributes for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  private def parameterListMarkup(nameStr: String, attributesList: List[AttributeModel], requiredArgs: List[String], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue,
        HtmlMarkup.yesNo(requiredArgs.contains(a.name)))
      div(cls := "nopagebreak")(
        p(strong(a(s"Arguments for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(component: ComponentModel, publishesOpt: Option[Publishes], nh: NumberedHeadings, forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName = component.component
    val subscriberStr = if (forApi) "Subscribers" else "Subscriber"
    val publisherInfo = span(strong("Publisher: "), s"${component.subsystem}.$compName")

    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]): Text.TypedTag[String] = {
      if (telemetryList.isEmpty) div()
      else {
        div(
          for (t <- telemetryList) yield {
            val subscribers = t.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberInfo = span(strong(s"$subscriberStr: "), if (subscribers.isEmpty) "none" else subscribers)
            val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate", "Required Rate")
            val rowList = List(List(
              HtmlMarkup.formatRate(t.telemetryModel.minRate),
              HtmlMarkup.formatRate(t.telemetryModel.maxRate),
              HtmlMarkup.yesNo(t.telemetryModel.archive),
              HtmlMarkup.formatRate(t.telemetryModel.archiveRate),
              t.subscribers.map(s => // Add required rate for subscribers that set it
                HtmlMarkup.formatRate(s"${s.componentModel.subsystem}.${s.componentModel.component}",
                  s.subscribeModelInfo.requiredRate)).mkString(" ").trim()
            ))
            div(cls := "nopagebreak")(
              nh.H4(s"${singlePubType(pubType)}: ${t.telemetryModel.name}", idFor(compName, "publishes", pubType, t.telemetryModel.name)),
              if (t.telemetryModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.telemetryModel.requirements.mkString(", ")),
              p(publisherInfo, ", ", subscriberInfo),
              raw(t.telemetryModel.description),
              // Include usage text from subscribers that define it
              div(t.subscribers.map(s =>
                if (s.subscribeModelInfo.usage.isEmpty) div()
                else div(strong(s"Usage by ${s.componentModel.subsystem}.${s.componentModel.component}: "),
                  raw(s.subscribeModelInfo.usage)))),
              HtmlMarkup.mkTable(headings, rowList),
              attributeListMarkup(t.telemetryModel.name, t.telemetryModel.attributesList, nh), hr
            )
          }
        )
      }
    }

    def publishAlarmListMarkup(alarmList: List[AlarmInfo]): Text.TypedTag[String] = {
      if (alarmList.isEmpty) div()
      else {
        div(
          for (t <- alarmList) yield {
            val subscribers = t.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberInfo = span(strong(s"$subscriberStr: "), if (subscribers.isEmpty) "none" else subscribers)
            val headings = List("Severity", "Archive")
            val rowList = List(List(t.alarmModel.severity, HtmlMarkup.yesNo(t.alarmModel.archive)))
            div(cls := "nopagebreak")(
              nh.H4(s"Alarm: ${t.alarmModel.name}", idFor(compName, "publishes", "Alarms", t.alarmModel.name)),
              if (t.alarmModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.alarmModel.requirements.mkString(", ")),
              p(publisherInfo, ", ", subscriberInfo),
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
            nh.H3(publishTitle(compName)),
            raw(publishes.description), hr,
            publishTelemetryListMarkup("Telemetry", publishes.telemetryList),
            publishTelemetryListMarkup("Events", publishes.eventList),
            publishTelemetryListMarkup("Event Streams", publishes.eventStreamList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        } else div()
    }
  }

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  private def singlePubType(pubType: String): String = {
    if (pubType.endsWith("s")) pubType.dropRight(1) else pubType
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(component: ComponentModel, subscribesOpt: Option[Subscribes], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName = component.component
    val subscriberInfo = span(strong("Subscriber: "), s"${component.subsystem}.$compName")

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]): Text.TypedTag[String] = {
      def formatRate(rate: Double): String = if (rate == 0.0) "" else s"$rate Hz"

      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) = info.warning.map { msg =>
        p(em(" Warning: ", msg))
      }

      if (subscribeList.isEmpty) div()
      else div(
        for (si <- subscribeList) yield {
          val sInfo = si.subscribeModelInfo
          val publisherInfo = span(strong("Publisher: "), s"${si.subscribeModelInfo.subsystem}.${si.subscribeModelInfo.component}")
          div(cls := "nopagebreak")(
            nh.H4(s"${singlePubType(pubType)}: ${sInfo.name}", idFor(compName, "subscribes", pubType, sInfo.name)),
            p(publisherInfo, ", ", subscriberInfo),
            raw(si.description),
            getWarning(si),
            if (sInfo.usage.isEmpty) div() else div(strong("Usage:"), raw(sInfo.usage)),
            table(
              thead(
                tr(th("Subsystem"), th("Component"), th("Prefix.Name"),
                  th("Required Rate"), th("Max Rate"),
                  th("Publisher's Min Rate"), th("Publisher's Max Rate"))
              ),
              tbody(
                tr(td(sInfo.subsystem), td(sInfo.component), td(si.path),
                  td(formatRate(sInfo.requiredRate)), td(formatRate(sInfo.maxRate)),
                  td(formatRate(si.telemetryModel.map(_.minRate).getOrElse(0.0))),
                  td(formatRate(si.telemetryModel.map(_.maxRate).getOrElse(0.0))))
              )
            ),
            si.telemetryModel.map(t => attributeListMarkup(t.name, t.attributesList, nh))
          )
        }
      )
    }

    subscribesOpt match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            nh.H3(subscribeTitle(compName)),
            raw(subscribes.description),
            subscribeListMarkup("Telemetry", subscribes.subscribeInfo.filter(_.itemType == Telemetry)),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == Events)),
            subscribeListMarkup("Event Streams", subscribes.subscribeInfo.filter(_.itemType == EventStreams)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
          )
        } else div()
    }
  }

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(component: ComponentModel,
                                     info: List[ReceivedCommandInfo],
                                     nh: NumberedHeadings,
                                     forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName = component.component
    val senderStr = if (forApi) "Senders" else "Sender"
    val receiverInfo = span(strong("Receiver: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H3(receivedCommandsTitle(compName)),
        for (r <- info) yield {
          val m = r.receiveCommandModel
          val senders = r.senders.map(s => s"${s.subsystem}.${s.component}").mkString(", ")
          val senderInfo = span(strong(s"$senderStr: "), if (senders.isEmpty) "none" else senders)
          div(cls := "nopagebreak")(
            nh.H4(m.name,
              idFor(compName, "receives", "Commands", m.name)),
            p(senderInfo, ", ", receiverInfo),
            if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
            raw(m.description),
            if (m.args.isEmpty) div() else parameterListMarkup(m.name, m.args, m.requiredArgs, nh)
          )
        }
      )
    }
  }

  private def sentCommandsTitle(compName: String): String = s"Command Configurations Sent by $compName"

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(component: ComponentModel, info: List[SentCommandInfo], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName = component.component
    val senderInfo = span(strong("Sender: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H3(sentCommandsTitle(compName)),
        for (s <- info) yield {
          val receiveCommandModel = s.receiveCommandModel
          val receiverStr = s.receiver.map(r => s"${r.subsystem}.${r.component}").getOrElse("none")
          val receiverInfo = span(strong("Receiver: "), receiverStr)
          div(cls := "nopagebreak")(
            nh.H4(s.name, idFor(compName, "sends", "Commands", s.name)),
            p(senderInfo, ", ", receiverInfo),
            receiveCommandModel match {
              case Some(m) => div(
                if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                raw(m.description),
                if (m.args.isEmpty) div() else parameterListMarkup(m.name, m.args, m.requiredArgs, nh)
              )
              case None => s.warning.map(msg => p(em(" Warning: ", msg)))
            }
          )
        }
      )
    }
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(component: ComponentModel, commandsOpt: Option[Commands], nh: NumberedHeadings, forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._
    commandsOpt match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || commands.commandsSent.nonEmpty) {
          div(
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, nh, forApi),
            if (forApi) sentCommandsMarkup(component, commands.commandsSent, nh) else div()
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
  private def markupForComponent(info: ComponentInfo, nh: NumberedHeadings, forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "pagebreakBefore")(
      nh.H2(info.componentModel.title, info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel, info.publishes, nh, forApi),
      if (forApi) subscribeMarkup(info.componentModel, info.subscribes, nh) else div(),
      commandsMarkup(info.componentModel, info.commands, nh, forApi)
    )
  }

  /**
    * Displays the information for a component
    *
    * @param info contains the information to display
    */
  private def displayComponentInfo(info: ComponentInfo, nh: NumberedHeadings, forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._
    // For ICDs, only display published items/received commands, for APIs show everything
    // (Note: Need to include even if only subscribed items are defined, to keep summary links from breaking)
    if (forApi ||
      (info.publishes.isDefined && info.publishes.get.nonEmpty
        || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
        || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
        || info.commands.get.commandsSent.nonEmpty))) {
      markupForComponent(info, nh, forApi)
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
    * Displays the subsystem title and description
    */
  private def makeIntro(titleInfo: TitleInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (titleInfo.descriptionOpt.isDefined) {
      div(raw(titleInfo.descriptionOpt.get))
    } else div
  }

  /**
    * Displays the details of the events published and commands received by the subsystem
    *
    * @param subsystem subsystem name
    * @param infoList  list of component info
    * @param nh        used for numbered headings and TOC
    * @return the HTML
    */
  private def displayDetails(subsystem: String,
                             infoList: List[ComponentInfo],
                             nh: NumberedHeadings,
                             forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      infoList.map(displayComponentInfo(_, nh, forApi))
    )
  }

  /**
    * Returns an HTML document describing the given components in the given subsystem.
    *
    * @param compNames the names of the components
    * @param sv        the selected subsystem and version
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
      val titleMarkup = getTitleMarkup(titleInfo)
      val nh = new NumberedHeadings
      val mainContent = div(
        SummaryTable.displaySummary(subsystemInfo, None, infoList, nh),
        displayDetails(subsystem, infoList, nh, forApi = true)
      )
      val toc = nh.mkToc()
      val intro = makeIntro(titleInfo)
      html(
        head(
          scalatags.Text.tags2.title(titleInfo.title),
          scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
        ),
        body(
          titleMarkup,
          div(cls := "pagebreakBefore"),
          h2("Table of Contents"),
          toc,
          div(cls := "pagebreakBefore"),
          titleMarkup,
          intro,
          mainContent
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
        displayDetails(subsystem, infoList, nh, forApi = false),
        makeIntro(titleInfo2),
        displayDetails(targetSubsystem, infoList2, nh, forApi = false)
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
