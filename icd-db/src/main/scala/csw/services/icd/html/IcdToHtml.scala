package csw.services.icd.html

import icd.web.shared.ComponentInfo._
import icd.web.shared._
import scalatags.Text
import Headings.idFor
import HtmlMarkup.yesNo
import icd.web.shared.IcdModels.{AlarmModel, ParameterModel, ComponentModel, EventModel}

/**
 * Handles converting ICD API from GFM to HTML
 */
//noinspection DuplicatedCode
object IcdToHtml {

  /**
   * Returns the CSS for saving to an HTML or PDF file, with changes according to the given options.
   */
  def getCss(pdfOptions: PdfOptions): String = {
    import pdfOptions._
    import PdfOptions._

    val fontIncr = fontSize - defaultFontSize
    val stream   = this.getClass.getResourceAsStream("/icd.css")
    val lines = scala.io.Source
      .fromInputStream(stream)
      .getLines()
      .map { line =>
        // change font size
        if (line.stripLeading().startsWith("font-size: ") && line.stripTrailing().endsWith("px;")) {
          val fontSize = line.substring(line.indexOf(": ") + 2).dropRight(3).toInt + fontIncr
          s"    font-size: ${fontSize}px;"
        }
        else line
      }
      .map { line =>
        // change line height
        if (line.stripLeading().startsWith("line-height: ")) {
          s"    line-height: $lineHeight;"
        }
        else line
      }
    lines.mkString("\n")
  }

  /**
   * Returns HTML describing the given components in the given subsystem.
   *
   * @param maybeSubsystemInfo contains info about the subsystem, if known
   * @param infoList           details about each component and what it publishes, subscribes to, etc.
   * @param pdfOptions         options for pdf generation
   * @param clientApi          if true, include subscribed events, sent commands
   * @return the html tags
   */
  def getApiAsHtml(
      maybeSubsystemInfo: Option[SubsystemInfo],
      infoList: List[ComponentInfo],
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val nh = new NumberedHeadings
    val (titleInfo, summaryTable) =
      if (maybeSubsystemInfo.isDefined) {
        val si = maybeSubsystemInfo.get
        val ti = TitleInfo(si, None, None)
        (ti, SummaryTable.displaySummary(si, None, infoList, nh, clientApi))
      }
      else if (infoList.size == 1) {
        // XXX TODO FIXME: When is this block called?
        val componentModel = infoList.head.componentModel
        val subsys         = componentModel.subsystem
        val comp           = componentModel.component
        val desc           = componentModel.description
        val ti             = TitleInfo(s"API for $subsys.$comp", None, Some(desc))
        val sv             = SubsystemWithVersion(subsys, None, Some(comp))
        val si             = SubsystemInfo(sv, "", "")
        (ti, SummaryTable.displaySummary(si, None, infoList, nh, clientApi))
      }
      else {
        (TitleInfo("", None, None), div())
      }
    val mainContent = div(
      style := "width: 100%;",
      summaryTable,
      displayDetails(infoList, nh, forApi = true, pdfOptions, clientApi)
    )
    val toc   = nh.mkToc()
    val intro = makeIntro(titleInfo)
    html(
      head(
        scalatags.Text.tags2.title(titleInfo.title),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
      ),
      body(
        getTitleMarkup(titleInfo),
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
   * @param pdfOptions   options for pdf gen
   * @param clientApi   if true, display subscribed events, sent commands
   * @return the HTML
   */
  def displayDetails(
      infoList: List[ComponentInfo],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(
      infoList.map(displayComponentInfo(_, nh, forApi, pdfOptions, clientApi))
    )
  }

  /**
   * Displays the information for a component
   */
  private def displayComponentInfo(
      info: ComponentInfo,
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    // For ICDs, only display published items/received commands, for APIs show everything
    // (Note: Need to include even if only subscribed items are defined, to keep summary links from breaking)
    // (XXX FIXME: Aug 2020: APIs should only show pub and recv, added clientApi option to show sub/send)
    if (
      forApi ||
      (info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
      || info.commands.get.commandsSent.nonEmpty))
    ) {
      markupForComponent(info, nh, forApi, pdfOptions, clientApi)
    }
    else div()
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(
      info: ComponentInfo,
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    div(cls := "pagebreakBefore")(
      nh.H2(info.componentModel.title, info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel, info.publishes, nh, forApi, pdfOptions, clientApi),
      if (forApi && clientApi) subscribeMarkup(info.componentModel, info.subscribes, nh, pdfOptions) else div(),
      commandsMarkup(info.componentModel, info.commands, nh, forApi, pdfOptions, clientApi)
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
      nh: NumberedHeadings,
      pdfOptions: PdfOptions
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
          val linkId              = idFor(compName, "sends", "Commands", s.subsystem, s.component, s.name)
          val showDetails         = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
          div(cls := "nopagebreak")(
            nh.H5(s"Command: ${s.name}", linkId),
            p(senderInfo, ", ", receiverInfo),
            receiveCommandModel match {
              case Some(m) if showDetails =>
                div(
                  if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                  if (m.preconditions.isEmpty) div()
                  else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
                  if (m.postconditions.isEmpty) div()
                  else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
                  raw(m.description),
                  if (m.parameters.isEmpty) div() else parameterListMarkup(m.name, m.parameters, m.requiredArgs)
                )
              case Some(m) =>
                div(
                  raw(m.description)
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
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || (commands.commandsSent.nonEmpty && forApi && clientApi)) {
          div(
            nh.H3(s"Commands for ${component.component}"),
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, nh, forApi, pdfOptions, clientApi),
            if (forApi && clientApi) sentCommandsMarkup(component, commands.commandsSent, nh, pdfOptions) else div()
          )
        }
        else div()
    }
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(
      component: ComponentModel,
      info: List[ReceivedCommandInfo],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName     = component.component
    val receiverInfo = span(strong("Receiver: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H4(receivedCommandsTitle(compName)),
        for (r <- info) yield {
          val m = r.receiveCommandModel
          val senderInfo = if (clientApi) {
            val senders = r.senders.distinct.map(s => s"${s.subsystem}.${s.component}").mkString(", ")
            span(strong(s"Senders: "), if (senders.isEmpty) "none" else senders)
          }
          else span
          val linkId      = idFor(compName, "receives", "Commands", component.subsystem, compName, m.name)
          val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
          div(cls := "nopagebreak")(
            nh.H5(s"Command: ${m.name}", linkId),
            if (clientApi) p(senderInfo, ", ", receiverInfo) else p(receiverInfo),
            if (showDetails) {
              div(
                if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                if (m.preconditions.isEmpty) div()
                else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
                if (m.postconditions.isEmpty) div()
                else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
                raw(m.description),
                if (m.refError.startsWith("Error:")) makeErrorDiv(m.refError) else div(),
                parameterListMarkup(m.name, m.parameters, m.requiredArgs),
                p(strong("Completion Type: "), m.completionType),
                resultTypeMarkup(m.resultType),
                if (m.completionConditions.isEmpty) div()
                else div(p(strong("Completion Conditions: "), ol(m.completionConditions.map(cc => li(raw(cc)))))),
                if (m.role.isEmpty) div()
                else div(p(strong("Required User Role: "), m.role.get))
              )
            }
            else
              div(
                raw(m.description)
              )
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

  private def resultTypeMarkup(parameterList: List[ParameterModel]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- parameterList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units)
      div(cls := "nopagebreak")(
        p(strong(a("Result Type Parameters"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
      )
    }
  }

  private def parameterListMarkup(
      nameStr: String,
      parameterList: List[ParameterModel],
      requiredArgs: List[String]
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList =
        for (a <- parameterList)
          yield List(
            a.name,
            a.description,
            getTypeStr(a.name, a.typeStr),
            a.units,
            a.defaultValue,
            yesNo(requiredArgs.contains(a.name))
          )
      div(cls := "nopagebreak")(
        p(strong(a(s"Parameters for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
      )
    }
  }

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(
      component: ComponentModel,
      maybeSubscribes: Option[Subscribes],
      nh: NumberedHeadings,
      pdfOptions: PdfOptions
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName       = component.component
    val subscriberInfo = span(strong("Subscriber: "), s"${component.subsystem}.$compName")

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]): Text.TypedTag[String] = {

      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) =
        info.warning.map { msg =>
          p(em(" Warning: ", msg))
        }

      if (subscribeList.isEmpty) div()
      else
        div(
          for (si <- subscribeList) yield {
            val sInfo = si.subscribeModelInfo
            val publisherInfo =
              span(strong("Publisher: "), s"${si.subscribeModelInfo.subsystem}.${si.subscribeModelInfo.component}")
            val linkId      = idFor(compName, "subscribes", pubType, sInfo.subsystem, sInfo.component, sInfo.name)
            val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${sInfo.name}",
                linkId
              ),
              p(publisherInfo, ", ", subscriberInfo),
              raw(si.description),
              getWarning(si),
              if (sInfo.usage.isEmpty) div() else div(strong("Usage:"), raw(sInfo.usage)),
              if (showDetails) {
                val maxRate = si.eventModel.flatMap(_.maybeMaxRate)
                div(
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
                  si.eventModel.map(t => attributeListMarkup(t.name, t.parameterList))
                )
              }
              else
                div(
                )
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
        }
        else div()
    }
  }

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  // HTML id for a table displaying the fields of a struct
  private def structIdStr(name: String): String = s"$name-struct"

  // Add a table for each parameter of type "struct" to show the members of the struct
  private def structParametersMarkup(parameterList: List[ParameterModel]): Seq[Text.TypedTag[String]] = {
    import scalatags.Text.all._
    val headings = List("Name", "Description", "Type", "Units", "Default")
    parameterList.flatMap { attrModel =>
      if (attrModel.typeStr == "struct" || attrModel.typeStr == "array of struct") {
        val rowList2 =
          for (a2 <- attrModel.parameterList)
            yield List(a2.name, a2.description, getTypeStr(a2.name, a2.typeStr), a2.units, a2.defaultValue)
        Some(
          div()(
            p(strong(a(name := structIdStr(attrModel.name))(s"Parameters for ${attrModel.name} struct"))),
            HtmlMarkup.mkTable(headings, rowList2),
            attrModel.parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
            // Handle structs embedded in other structs (or arrays of structs, etc.)
            structParametersMarkup(attrModel.parameterList)
          )
        )
      }
      else None
    }
  }

  private def makeErrorDiv(msg: String): Text.TypedTag[String] = {
    import scalatags.Text.all._
    div(cls := "alert alert-warning", role := "alert")(
      span(cls := "glyphicon glyphicon-warning-sign", attr("aria-hidden") := "true"),
      span(em(s" $msg"))
    )
  }

  private def attributeListMarkup(
      nameStr: String,
      parameterList: List[ParameterModel]
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList =
        for (a <- parameterList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units, a.defaultValue)
      div(cls := "nopagebreak")(
        p(strong(a(s"Parameters for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
      )
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(
      component: ComponentModel,
      maybePublishes: Option[Publishes],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName      = component.component
    val publisherInfo = span(strong("Publisher: "), s"${component.subsystem}.$compName")
    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]): Text.TypedTag[String] = {
      if (eventList.isEmpty) div()
      else {
        div(
          for (eventInfo <- eventList) yield {
            val eventModel  = eventInfo.eventModel
            val linkId      = idFor(compName, "publishes", pubType, component.subsystem, compName, eventModel.name)
            val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
            val subscribers =
              eventInfo.subscribers
                .map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}")
                .distinct
                .mkString(", ")
            val subscriberInfo =
              if (clientApi)
                span(strong(s"Subscribers: "), if (subscribers.isEmpty) "none" else subscribers)
              else span
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
                  .map(s => // Add required rate for subscribers that set it
                    HtmlMarkup.formatRate(
                      s"${s.componentModel.subsystem}.${s.componentModel.component}",
                      s.subscribeModelInfo.requiredRate
                    )
                  )
                  .mkString(" ")
                  .trim()
              )
            )
            // Include usage text from subscribers that define it
            val subscriberUsage =
              if (clientApi)
                div(
                  eventInfo.subscribers.map(s =>
                    if (s.subscribeModelInfo.usage.isEmpty) div()
                    else
                      div(
                        strong(s"Usage by ${s.componentModel.subsystem}.${s.componentModel.component}: "),
                        raw(s.subscribeModelInfo.usage)
                      )
                  )
                )
              else span
            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${eventModel.name}",
                linkId
              ),
              if (showDetails) {
                div(
                  if (eventModel.requirements.isEmpty) div()
                  else p(strong("Requirements: "), eventModel.requirements.mkString(", ")),
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  raw(eventModel.description),
                  if (eventModel.refError.startsWith("Error:")) makeErrorDiv(eventModel.refError) else div(),
                  subscriberUsage,
                  HtmlMarkup.mkTable(headings, rowList),
                  attributeListMarkup(eventModel.name, eventModel.parameterList),
                  hr
                )
              }
              else
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  raw(eventModel.description),
                  subscriberUsage
                )
            )
          }
        )
      }
    }

    def publishAlarmListMarkup(alarmList: List[AlarmModel], pdfOptions: PdfOptions): Text.TypedTag[String] = {
      if (alarmList.isEmpty) div()
      else {
        div(
          for (m <- alarmList) yield {
            val headings = List("Severity Levels", "Location", "Alarm Type", "Auto Ack", "Latched")
            val rowList = List(
              List(
                m.severityLevels.mkString(", "),
                m.location,
                m.alarmType,
                yesNo(m.autoAck),
                yesNo(m.latched)
              )
            )
            val linkId      = idFor(compName, "publishes", "Alarms", component.subsystem, compName, m.name)
            val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
            div(cls := "nopagebreak")(
              nh.H4(s"Alarm: ${m.name}", linkId),
              if (showDetails) {
                div(
                  if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
                  p(publisherInfo),
                  raw(m.description),
                  p(strong("Probable Cause: "), raw(m.probableCause)),
                  p(strong("Operator Response: "), raw(m.operatorResponse)),
                  HtmlMarkup.mkTable(headings, rowList)
                )
              }
              else
                div(
                  p(publisherInfo),
                  raw(m.description)
                ),
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
            publishAlarmListMarkup(publishes.alarmList, pdfOptions)
          )
        }
        else div()
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
    }
    else div
  }

}
