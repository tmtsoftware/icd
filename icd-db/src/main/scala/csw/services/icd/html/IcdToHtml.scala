package csw.services.icd.html

import icd.web.shared.ComponentInfo._
import icd.web.shared._
import scalatags.Text
import Headings.idFor
import HtmlMarkup.yesNo
import icd.web.shared.IcdModels.{AttributeModel, ComponentModel, EventModel}

/**
 * Handles converting ICD API from GFM to HTML
 */
//noinspection DuplicatedCode
object IcdToHtml {

  def getCss: String = {
    val stream = getClass.getResourceAsStream("/icd.css")
    val lines  = scala.io.Source.fromInputStream(stream).getLines()
    lines.mkString("\n")
  }

  /**
   * Returns an HTML tags describing the given components in the given subsystem.
   *
   * @param maybeSubsystemInfo contains info about the subsystem, if known
   * @param infoList           details about each component and what it publishes, subscribes to, etc.
   * @return the html tags
   */
  def getApiAsHtml(maybeSubsystemInfo: Option[SubsystemInfo], infoList: List[ComponentInfo]): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val nh = new NumberedHeadings
    val (titleInfo, summaryTable) =
      if (maybeSubsystemInfo.isDefined) {
        val si = maybeSubsystemInfo.get
        val ti = TitleInfo(si, None, None)
        (ti, SummaryTable.displaySummary(si, None, infoList, nh))
      } else if (infoList.size == 1) {
        // XXX TODO FIXME: When is this block called?
        val componentModel = infoList.head.componentModel
        val subsys         = componentModel.subsystem
        val comp           = componentModel.component
        val desc           = componentModel.description
        val ti             = TitleInfo(s"API for $subsys.$comp", None, Some(desc))
        val sv             = SubsystemWithVersion(subsys, None, Some(comp))
        val si             = SubsystemInfo(sv, "", "")
        (ti, SummaryTable.displaySummary(si, None, infoList, nh))
      } else {
        (TitleInfo("", None, None), div())
      }
    val mainContent = div(
      style := "width: 100%;",
      summaryTable,
      displayDetails(infoList, nh, forApi = true)
    )
    val toc   = nh.mkToc()
    val intro = makeIntro(titleInfo)
    html(
      head(
        scalatags.Text.tags2.title(titleInfo.title),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
      ),
      body(
        getTitleMarkup(titleInfo, titleId = "title"),
        div(cls := "pagebreakBefore"),
        h2("Table of Contents"),
        toc,
        div(cls := "pagebreakBefore"),
        getTitleMarkup(titleInfo, titleId = "title2"),
        intro,
        mainContent
      )
    )
  }

  def getTitleMarkup(titleInfo: TitleInfo, titleId: String = "title"): Text.TypedTag[String] = {
    import scalatags.Text.all._
    titleInfo.maybeSubtitle match {
      case Some(subtitle) =>
        h3(a(name := titleId), cls := "page-header")(titleInfo.title, br, small(subtitle))
      case None =>
        h3(a(name := titleId), cls := "page-header")(titleInfo.title)
    }
  }

  /**
   * Displays the details of the events published and commands received by the subsystem
   *
   * @param infoList list of component info
   * @param nh       used for numbered headings and TOC
   * @param forApi   true if this is for an API document, false for ICD
   * @return the HTML
   */
  def displayDetails(infoList: List[ComponentInfo], nh: NumberedHeadings, forApi: Boolean): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      infoList.map(displayComponentInfo(_, nh, forApi))
    )
  }

  /**
   * Displays the information for a component
   *
   * @param info   contains the information to display
   * @param nh     used for numbered headings and TOC
   * @param forApi true if this is for an API document, false for ICD
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

  private def publishTitle(compName: String): String = s"Items published by $compName"

  private def singlePubType(pubType: String): String = {
    if (pubType.endsWith("s")) pubType.dropRight(1) else pubType
  }

  private def sentCommandsTitle(compName: String): String = s"Command Configurations Sent by $compName"

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(
      component: ComponentModel,
      info: List[SentCommandInfo],
      nh: NumberedHeadings
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName   = component.component
    val senderInfo = span(strong("Sender: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H4(sentCommandsTitle(compName)),
        for (s <- info) yield {
          val receiveCommandModel = s.receiveCommandModel
          val receiverStr         = s.receiver.map(r => s"${r.subsystem}.${r.component}").getOrElse("none")
          val receiverInfo        = span(strong("Receiver: "), receiverStr)
          div(cls := "nopagebreak")(
            nh.H5(s.name, idFor(compName, "sends", "Commands", s.subsystem, s.component, s.name)),
            p(senderInfo, ", ", receiverInfo),
            receiveCommandModel match {
              case Some(m) =>
                div(
                  if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                  if (m.preconditions.isEmpty) div()
                  else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
                  if (m.postconditions.isEmpty) div()
                  else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
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
  private def commandsMarkup(
      component: ComponentModel,
      maybeCommands: Option[Commands],
      nh: NumberedHeadings,
      forApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || commands.commandsSent.nonEmpty) {
          div(
            nh.H3(s"Commands for ${component.component}"),
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, nh, forApi),
            if (forApi) sentCommandsMarkup(component, commands.commandsSent, nh) else div()
          )
        } else div()
    }
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(
      component: ComponentModel,
      info: List[ReceivedCommandInfo],
      nh: NumberedHeadings,
      forApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName     = component.component
    val senderStr    = if (forApi) "Senders" else "Sender"
    val receiverInfo = span(strong("Receiver: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H4(receivedCommandsTitle(compName)),
        for (r <- info) yield {
          val m          = r.receiveCommandModel
          val senders    = r.senders.map(s => s"${s.subsystem}.${s.component}").mkString(", ")
          val senderInfo = span(strong(s"$senderStr: "), if (senders.isEmpty) "none" else senders)
          div(cls := "nopagebreak")(
            nh.H5(m.name, idFor(compName, "receives", "Commands", component.subsystem, compName, m.name)),
            p(senderInfo, ", ", receiverInfo),
            if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
            if (m.preconditions.isEmpty) div() else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
            if (m.postconditions.isEmpty) div()
            else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
            raw(m.description),
            parameterListMarkup(m.name, m.args, m.requiredArgs, nh),
            p(strong("Completion Type: "), m.completionType),
            resultTypeMarkup(m.resultType, nh),
            if (m.completionConditions.isEmpty) div()
            else div(p(strong("Completion Conditions: "), ol(m.completionConditions.map(cc => li(raw(cc))))))
          )
        }
      )
    }
  }

  // Insert a hyperlink from "struct" to the table listing the fields in the struct
  private def getTypeStr(fieldName: String, typeStr: String): String = {
    import scalatags.Text.all._
    if (typeStr == "struct" || typeStr == "array of struct")
      a(href := s"#${structIdStr(fieldName)}")(typeStr).render
    else typeStr
  }

  private def resultTypeMarkup(attributesList: List[AttributeModel], nh: NumberedHeadings): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- attributesList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units)
      div(cls := "nopagebreak")(
        p(strong(a("Result Type Fields"))),
        HtmlMarkup.mkTable(headings, rowList),
        structAttributesMarkup(attributesList)
      )
    }
  }

  private def parameterListMarkup(
      nameStr: String,
      attributesList: List[AttributeModel],
      requiredArgs: List[String],
      nh: NumberedHeadings
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList =
        for (a <- attributesList)
          yield List(
            a.name,
            a.description,
            getTypeStr(a.name, a.typeStr),
            a.units,
            a.defaultValue,
            yesNo(requiredArgs.contains(a.name))
          )
      div(cls := "nopagebreak")(
        p(strong(a(s"Arguments for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        structAttributesMarkup(attributesList)
      )
    }
  }

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(
      component: ComponentModel,
      maybeSubscribes: Option[Subscribes],
      nh: NumberedHeadings
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName       = component.component
    val subscriberInfo = span(strong("Subscriber: "), s"${component.subsystem}.$compName")

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]): Text.TypedTag[String] = {

      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) = info.warning.map { msg =>
        p(em(" Warning: ", msg))
      }

      if (subscribeList.isEmpty) div()
      else
        div(
          for (si <- subscribeList) yield {
            val sInfo = si.subscribeModelInfo
            val publisherInfo =
              span(strong("Publisher: "), s"${si.subscribeModelInfo.subsystem}.${si.subscribeModelInfo.component}")
            val maxRate = si.eventModel.flatMap(_.maybeMaxRate)
            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${sInfo.name}",
                idFor(compName, "subscribes", pubType, sInfo.subsystem, sInfo.component, sInfo.name)
              ),
              p(publisherInfo, ", ", subscriberInfo),
              raw(si.description),
              getWarning(si),
              if (sInfo.usage.isEmpty) div() else div(strong("Usage:"), raw(sInfo.usage)),
              table(
                thead(
                  tr(
                    th("Subsystem"),
                    th("Component"),
                    th("Prefix.Name"),
                    th("Max Rate"),
                    th("Publisher's Max Rate")
                  )
                ),
                tbody(
                  tr(
                    td(sInfo.subsystem),
                    td(sInfo.component),
                    td(si.path),
                    td(HtmlMarkup.formatRate(sInfo.maxRate)),
                    td(HtmlMarkup.formatRate(maxRate))
                  )
                )
              ),
              if (maxRate.isEmpty) span("* Default maxRate of 1 Hz assumed.") else span(),
              si.eventModel.map(t => attributeListMarkup(t.name, t.attributesList))
            )
          }
        )
    }

    maybeSubscribes match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            nh.H3(subscribeTitle(compName)),
            raw(subscribes.description),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == Events)),
            subscribeListMarkup("Observe Events", subscribes.subscribeInfo.filter(_.itemType == ObserveEvents)),
            subscribeListMarkup("Current States", subscribes.subscribeInfo.filter(_.itemType == CurrentStates)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
          )
        } else div()
    }
  }

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  // HTML id for a table displaying the fields of a struct
  private def structIdStr(name: String): String = s"$name-struct"

  // Add a table for each attribute of type "struct" to show the members of the struct
  private def structAttributesMarkup(attributesList: List[AttributeModel]): Seq[Text.TypedTag[String]] = {
    import scalatags.Text.all._
    val headings = List("Name", "Description", "Type", "Units", "Default")
    attributesList.flatMap { attrModel =>
      if (attrModel.typeStr == "struct" || attrModel.typeStr == "array of struct") {
        val rowList2 =
          for (a2 <- attrModel.attributesList)
            yield List(a2.name, a2.description, getTypeStr(a2.name, a2.typeStr), a2.units, a2.defaultValue)
        Some(
          div()(
            p(strong(a(name := structIdStr(attrModel.name))(s"Attributes for ${attrModel.name} struct"))),
            HtmlMarkup.mkTable(headings, rowList2),
            // Handle structs embedded in other structs (or arrays of structs, etc.)
            structAttributesMarkup(attrModel.attributesList)
          )
        )
      } else None
    }
  }

  private def attributeListMarkup(
      nameStr: String,
      attributesList: List[AttributeModel]
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList =
        for (a <- attributesList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units, a.defaultValue)
      div(cls := "nopagebreak")(
        p(strong(a(s"Attributes for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        structAttributesMarkup(attributesList)
      )
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(
      component: ComponentModel,
      maybePublishes: Option[Publishes],
      nh: NumberedHeadings,
      forApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName      = component.component
    val subscriberStr = if (forApi) "Subscribers" else "Subscriber"
    val publisherInfo = span(strong("Publisher: "), s"${component.subsystem}.$compName")
    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]): Text.TypedTag[String] = {
      if (eventList.isEmpty) div()
      else {
        div(
          for (eventInfo <- eventList) yield {
            val eventModel = eventInfo.eventModel
            val subscribers =
              eventInfo.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberInfo = span(strong(s"$subscriberStr: "), if (subscribers.isEmpty) "none" else subscribers)
            val totalArchiveSpacePerYear =
              if (eventModel.totalArchiveSpacePerYear.isEmpty) ""
              else if (eventModel.maybeMaxRate.isEmpty) em(eventModel.totalArchiveSpacePerYear).render
              else span(eventModel.totalArchiveSpacePerYear).render
            val headings =
              List("Max Rate", "Archive", "Archive Duration", "Bytes per Event", "Year Accumulation", "Required Rate")
            val rowList = List(
              List(
                HtmlMarkup.formatRate(eventModel.maybeMaxRate).render,
                yesNo(eventModel.archive),
                eventModel.archiveDuration,
                eventModel.totalSizeInBytes.toString,
                totalArchiveSpacePerYear,
                eventInfo.subscribers
                  .map(
                    s => // Add required rate for subscribers that set it
                      HtmlMarkup.formatRate(
                        s"${s.componentModel.subsystem}.${s.componentModel.component}",
                        s.subscribeModelInfo.requiredRate
                      )
                  )
                  .mkString(" ")
                  .trim()
              )
            )
            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${eventModel.name}",
                idFor(compName, "publishes", pubType, component.subsystem, compName, eventModel.name)
              ),
              if (eventModel.requirements.isEmpty) div()
              else p(strong("Requirements: "), eventModel.requirements.mkString(", ")),
              p(publisherInfo, ", ", subscriberInfo),
              raw(eventModel.description),
              // Include usage text from subscribers that define it
              div(
                eventInfo.subscribers.map(
                  s =>
                    if (s.subscribeModelInfo.usage.isEmpty) div()
                    else
                      div(
                        strong(s"Usage by ${s.componentModel.subsystem}.${s.componentModel.component}: "),
                        raw(s.subscribeModelInfo.usage)
                      )
                )
              ),
              HtmlMarkup.mkTable(headings, rowList),
              attributeListMarkup(eventModel.name, eventModel.attributesList),
              hr
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
            val m = t.alarmModel
            val subscribers =
              t.subscribers.map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}").mkString(", ")
            val subscriberInfo = span(strong(s"$subscriberStr: "), if (subscribers.isEmpty) "none" else subscribers)
            val headings       = List("Severity Levels", "Location", "Alarm Type", "Acknowledge", "Latched")
            val rowList = List(
              List(
                m.severityLevels.mkString(", "),
                m.location,
                m.alarmType,
                yesNo(m.acknowledge),
                yesNo(m.latched)
              )
            )
            div(cls := "nopagebreak")(
              nh.H4(s"Alarm: ${m.name}", idFor(compName, "publishes", "Alarms", component.subsystem, compName, m.name)),
              if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
              p(publisherInfo, ", ", subscriberInfo),
              raw(m.description),
              p(strong("Probable Cause: "), raw(m.probableCause)),
              p(strong("Operator Response: "), raw(m.operatorResponse)),
              HtmlMarkup.mkTable(headings, rowList),
              hr
            )
          }
        )
      }
    }

    def totalArchiveSpace(): Text.TypedTag[String] = {
      val totalYearlyArchiveSpace = {
        val eventList = maybePublishes.toList.flatMap(p => (p.eventList ++ p.observeEventList).map(_.eventModel))
        EventModel.getTotalArchiveSpace(eventList)
      }
      if (totalYearlyArchiveSpace.nonEmpty)
        strong(
          p(
            s"Total yearly space required for archiving events published by ${component.subsystem}.$compName: $totalYearlyArchiveSpace"
          )
        )
      else span()

    }

    maybePublishes match {
      case None => div()
      case Some(publishes) =>
        if (maybePublishes.nonEmpty && maybePublishes.get.nonEmpty) {
          div(
            nh.H3(publishTitle(compName)),
            raw(publishes.description),
            hr,
            publishEventListMarkup("Events", publishes.eventList),
            publishEventListMarkup("Observe Events", publishes.observeEventList),
            if (forApi) totalArchiveSpace() else span(),
            publishEventListMarkup("Current States", publishes.currentStateList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        } else div()
    }
  }

  // Generates a one line table with basic component information
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

  /**
   * Displays the subsystem title and description
   */
  def makeIntro(titleInfo: TitleInfo): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (titleInfo.maybeDescription.isDefined) {
      div(raw(titleInfo.maybeDescription.get))
    } else div
  }

}
