package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.{HtmlMarkup, IcdToHtml, NumberedHeadings}
import icd.web.shared.ComponentInfo.{Alarms, EventStreams, Events, Telemetry}
import icd.web.shared.IcdModels.{AttributeModel, ComponentModel, NameDesc}
import icd.web.shared._

import scalatags.Text


object IcdPrinter {

  /**
    * Used where the item description from the other subsystem may not be available
    */
  case class OptionalNameDesc(name: String, opt: Option[NameDesc]) extends NameDesc {
    override val description: String = opt.map(_.description).getOrElse("")
  }

  /**
    * Summary of a published item or received command.
    *
    * @param component the publishing or receiving component
    * @param item      name and description of the item or command
    */
  case class PublishedItem(component: ComponentModel, item: NameDesc)

  /**
    * Summary of a subscribed item.
    *
    * @param publisherSubsystem the publisher's subsystem
    * @param publisherComponent the publisher's component
    * @param publisherOpt       the publisher's component model, if known
    * @param warningOpt         a warning, in case the publisher's component model, is not known
    * @param subscriber         the subscriber's component model
    * @param item               name and description of the published item
    */
  case class SubscribedItem(publisherSubsystem: String, publisherComponent: String,
                            publisherOpt: Option[ComponentModel],
                            warningOpt: Option[String],
                            subscriber: ComponentModel, item: NameDesc)

}

/**
  * Creates an HTML or PDF document for a subsystem, component or ICD based on data from the database
  *
  * @param db used to query the database
  */
case class IcdDbPrinter(db: IcdDb) {

  import IcdPrinter._

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
        h6(a(s"Attributes for $nameStr")),
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
        h6(a(s"Arguments for $nameStr")),
        HtmlMarkup.mkTable(headings, rowList)
      )
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, publishesOpt: Option[Publishes], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._

    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]): Text.TypedTag[String] = {
      if (telemetryList.isEmpty) div()
      else {
        div(
          for (t <- telemetryList) yield {
            val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate")
            val rowList = List(List(HtmlMarkup.formatRate(t.telemetryModel.minRate), HtmlMarkup.formatRate(t.telemetryModel.maxRate),
              HtmlMarkup.yesNo(t.telemetryModel.archive),
              HtmlMarkup.formatRate(t.telemetryModel.archiveRate)))
            val subscribers = t.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberDiv = if (t.subscribers.isEmpty) div() else p(strong("Subscribers: "), subscribers)
            div(cls := "nopagebreak")(
              nh.H4(s"$compName publishes ${singlePubType(pubType)}: ${t.telemetryModel.name}",
                idFor(compName, "publishes", pubType, t.telemetryModel.name)),
              if (t.telemetryModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.telemetryModel.requirements.mkString(", ")),
              subscriberDiv,
              raw(t.telemetryModel.description),
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
            val headings = List("Severity", "Archive", "Subscribers")
            val rowList = List(List(t.alarmModel.severity, HtmlMarkup.yesNo(t.alarmModel.archive),
              t.subscribers.map(_.subscribeModelInfo.component).mkString(", ")))
            div(cls := "nopagebreak")(
              nh.H4(s"$compName publishes Alarm: ${t.alarmModel.name}",
                idFor(compName, "publishes", "Alarms", t.alarmModel.name)),
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
    val s = pubType.toLowerCase()
    if (s.endsWith("s")) s.dropRight(1) else s
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._

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
          val from = s"from ${si.subscribeModelInfo.subsystem}.${si.subscribeModelInfo.component}"
          div(cls := "nopagebreak")(
            nh.H4(s"$compName subscribes to ${singlePubType(pubType)}: ${sInfo.name} $from",
              idFor(compName, "subscribes", pubType, sInfo.name)),
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
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (info.isEmpty) div()
    else {
      div(
        nh.H3(receivedCommandsTitle(compName)),
        for (r <- info) yield {
          val m = r.receiveCommandModel
          val from = r.senders.map(s => s"${s.subsystem}.${s.compName}").mkString(", ")
          val senders = if (from.isEmpty) div() else p(strong("Senders: "), from)
          div(cls := "nopagebreak")(
            nh.H4(s"$compName receives configuration: ${m.name}",
              idFor(compName, "receives", "Commands", m.name)),
            senders,
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
  private def sentCommandsMarkup(compName: String, info: List[SentCommandInfo], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (info.isEmpty) div()
    else {
      div(
        nh.H3(sentCommandsTitle(compName)),
        for (s <- info) yield {
          val receiveCommandModel = s.receiveCommandModel
          val to = s.receiver.map(r => s"to ${r.subsystem}.${r.component}").getOrElse("")
          div(cls := "nopagebreak")(
            nh.H4(s"$compName sends configuration: ${s.name} $to",
              idFor(compName, "sends", "Commands", s.name)),
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
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    commandsOpt match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || commands.commandsSent.nonEmpty) {
          div(
            raw(commands.description),
            receivedCommandsMarkup(compName, commands.commandsReceived, nh),
            sentCommandsMarkup(compName, commands.commandsSent, nh)
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
  private def markupForComponent(info: ComponentInfo, nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "pagebreakBefore")(
      nh.H2(info.componentModel.title, info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel.component, info.publishes, nh),
      subscribeMarkup(info.componentModel.component, info.subscribes, nh),
      commandsMarkup(info.componentModel.component, info.commands, nh)
    )
  }

  /**
    * Displays the information for a component
    *
    * @param info contains the information to display
    */
  private def displayComponentInfo(info: ComponentInfo, nh: NumberedHeadings, forApi: Boolean = true): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (forApi || (info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
      || info.commands.get.commandsSent.nonEmpty))) {
      markupForComponent(info, nh)
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
    * Returns a unique id for a link target.
    *
    * @param component component name
    * @param action    publishes, subscribes, sends, receives
    * @param itemType  Event, Alarm, etc.
    * @param name      item name
    * @return the id
    */
  private def idFor(component: String, action: String, itemType: String, name: String): String = {
    s"$component-$action-$itemType-$name".replace(" ", "-")
  }

  /**
    * Displays a summary of the events published and commands received by the subsystem
    *
    * @param subsystem subsystem name
    * @param infoList  list of component info
    * @param nh        used for numbered headings and TOC
    * @return the HTML
    */
  private def displaySummary(subsystem: String, infoList: List[ComponentInfo], nh: NumberedHeadings, isIcd: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    // Displays a summary for published items of a given event type or commands received.
    def summary1(itemType: String, list: List[PublishedItem], heading: String): Text.TypedTag[String] = {
      val action = heading.toLowerCase() match {
        case "published by" => "publishes"
        case "received by" => "receives"
      }
      if (list.isEmpty) div() else {
        div(
          nh.H3(s"$itemType $heading $subsystem"),
          table(
            thead(
              tr(
                th("Prefix"),
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
              } yield {
                tr(
                  td(p(a(href := s"#${info.component.component}")(info.component.prefix))),
                  td(p(a(href := s"#${idFor(info.component.component, action, itemType, info.item.name)}")(info.item.name))),
                  td(raw(firstParagraph(info.item.description))))
              }
            )
          )
        )
      }
    }

    // Displays a summary for subscribed items of a given event type or commands sent.
    def summary2(itemType: String, list: List[SubscribedItem], heading: String): Text.TypedTag[String] = {
      val (action, subscriber) = heading.toLowerCase() match {
        case "subscribed to by" => ("subscribes", "Subscriber")
        case "sent by" => ("sends", "Sender")
      }
      if (list.isEmpty) div() else {
        div(
          nh.H3(s"$itemType $heading $subsystem"),
          table(
            thead(
              tr(
                th(subscriber),
                th("Prefix"),
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
              } yield {
                // If this is an ICD or the publisher is in the same subsystem, we can link to it, since it is in this doc
                val prefixItem = info.publisherOpt match {
                  case Some(componentModel) => span(componentModel.prefix)
                  case None => em("unknown")
                }
                val publisherPrefix =
                  if (isIcd || info.publisherSubsystem == info.subscriber.subsystem)
                    a(href := s"#${info.publisherComponent}")(prefixItem)
                  else span(prefixItem)

                val description = info.warningOpt match {
                  case Some(msg) => p(em("Warning: ", msg))
                  case None => raw(firstParagraph(info.item.description))
                }

                tr(
                  td(p(a(href := s"#${info.subscriber.component}")(info.subscriber.component))),
                  td(p(publisherPrefix)),
                  td(p(a(href := s"#${idFor(info.subscriber.component, action, itemType, info.item.name)}")(info.item.name))),
                  td(description)
                )
              }
            )
          )
        )
      }
    }

    val publishedEvents = for {
      info <- infoList
      pub <- info.publishes.toList
      event <- pub.eventList
    } yield PublishedItem(info.componentModel, event.telemetryModel)

    val publishedEventStreams = for {
      info <- infoList
      pub <- info.publishes.toList
      event <- pub.eventStreamList
    } yield PublishedItem(info.componentModel, event.telemetryModel)

    val publishedTelemetry = for {
      info <- infoList
      pub <- info.publishes.toList
      event <- pub.telemetryList
    } yield PublishedItem(info.componentModel, event.telemetryModel)

    val publishedAlarms = for {
      info <- infoList
      pub <- info.publishes.toList
      event <- pub.alarmList
    } yield PublishedItem(info.componentModel, event.alarmModel)

    val receivedCommands = for {
      info <- infoList
      commands <- info.commands.toList
      command <- commands.commandsReceived
    } yield PublishedItem(info.componentModel, command.receiveCommandModel)

    // For subscribed items and sent commands, the info from the other subsystem might not be available
    val allSubscribed = for {
      info <- infoList
      sub <- info.subscribes.toList
      event <- sub.subscribeInfo
    } yield (event.itemType, SubscribedItem(
      event.subscribeModelInfo.subsystem,
      event.subscribeModelInfo.component,
      event.publisher,
      event.warning,
      info.componentModel,
      OptionalNameDesc(event.subscribeModelInfo.name, event.telemetryModel)))
    val subscribedEvents = allSubscribed.filter(_._1 == Events).map(_._2)
    val subscribedEventStreams = allSubscribed.filter(_._1 == EventStreams).map(_._2)
    val subscribedTelemetry = allSubscribed.filter(_._1 == Telemetry).map(_._2)
    val subscribedAlarms = allSubscribed.filter(_._1 == Alarms).map(_._2)

    val sentCommands = for {
      info <- infoList
      commands <- info.commands.toList
      command <- commands.commandsSent
    } yield SubscribedItem(
      command.subsystem,
      command.component,
      command.receiver,
      command.warning,
      info.componentModel,
      OptionalNameDesc(command.name, command.receiveCommandModel))

    div(
      nh.H2(s"$subsystem Event and Command Summary"),

      summary1("Events", publishedEvents, "Published by"),
      summary2("Events", subscribedEvents, "Subscribed to by"),

      summary1("Event Streams", publishedEventStreams, "Published by"),
      summary2("Event Streams", subscribedEventStreams, "Subscribed to by"),

      summary1("Telemetry", publishedTelemetry, "Published by"),
      summary2("Telemetry", subscribedTelemetry, "Subscribed to by"),

      summary1("Alarms", publishedAlarms, "Published by"),
      summary2("Alarms", subscribedAlarms, "Subscribed to by"),

      summary1("Commands", receivedCommands, "Received by"),
      summary2("Commands", sentCommands, "Sent by")
    )
  }

  /**
    * Displays the details of the events published and commands received by the subsystem
    *
    * @param subsystem subsystem name
    * @param infoList  list of component info
    * @param nh        used for numbered headings and TOC
    * @return the HTML
    */
  private def displayDetails(subsystem: String, infoList: List[ComponentInfo], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      // TODO: Should this still be ordered by assembly?
      infoList.map(displayComponentInfo(_, nh))
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
        displaySummary(subsystem, infoList, nh, isIcd = false),
        displayDetails(subsystem, infoList, nh)
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
      val nh = new NumberedHeadings
      val mainContent = div(
        displaySummary(subsystem, infoList, nh, isIcd = true),
        displaySummary(targetSubsystem, infoList2, nh, isIcd = true),
        makeIntro(titleInfo1),
        displayDetails(subsystem, infoList, nh),
        makeIntro(titleInfo2),
        displayDetails(targetSubsystem, infoList2, nh)
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
        println("Please specify source and optionally target subsystems to print")
    }
  }
}
