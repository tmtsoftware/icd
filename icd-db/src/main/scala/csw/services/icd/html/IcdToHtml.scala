package csw.services.icd.html

import icd.web.shared.ComponentInfo.*
import icd.web.shared.*
import scalatags.Text
import Headings.idFor
import HtmlMarkup.yesNo
import csw.services.icd.db.IcdDb
import icd.web.shared.IcdModels.{AlarmModel, CommandResultModel, ComponentModel, EventModel, MetadataModel, ParameterModel}

/**
 * Handles converting model files to static HTML for use in generating a PDF
 */
//noinspection DuplicatedCode
object IcdToHtml {

  /**
   * Returns the CSS for saving to an HTML or PDF file, with changes according to the given options.
   */
  def getCss(pdfOptions: PdfOptions): String = {
    import pdfOptions.*
    import PdfOptions.*

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

  // Makes the link for a FITS keyword source to the event that is the source of the keyword
  private def makeLinkForFitsKeySource(
      fitsKey: FitsKeyInfo,
      fitsChannel: FitsChannel,
      index: Int,
      withLinks: Boolean,
      tagMap: Map[FitsKeywordAndChannel, String]
  ) = {
    import scalatags.Text.all.*
    val fitsSource = fitsChannel.source
    val channel    = if (fitsChannel.name.nonEmpty) Some(fitsChannel.name) else None
    // Get tag for key
    val maybeTag = tagMap.get(FitsKeywordAndChannel(fitsKey.name, channel))

    div(
      if (index != 0) hr else span(),
      maybeTag.map(tag => span(tag, ": ")),
      if (withLinks) {
        a(
          title := s"Go to event parameter that is the source of this FITS keyword",
          s"${fitsSource.toLongString} ",
          href := s"#${Headings.idForParam(
              fitsSource.componentName,
              "publishes",
              "Event",
              fitsSource.subsystem,
              fitsSource.componentName,
              fitsSource.eventName,
              fitsSource.parameterName
            )}"
        )
      }
      else span(s"${fitsSource.toLongString} ")
    )
  }

  /**
   * Generates table with related FITS key information
   * @param fitsDictionary fits keywords and tags
   * @param nh used for headings
   * @param titleStr title for table
   * @param includeTags if true, include tag in keyword source/channel column
   * @param withLinks if true, include links to event parameters for key sources
   * @param maybeSubsystem optional subsystem (restrict channels)
   * @param maybeComponent optional component (restrict channels)
   */
  def makeFitsKeyTable(
      fitsDictionary: FitsDictionary,
      nh: Headings,
      titleStr: String = "FITS Dictionary",
      includeTags: Boolean,
      withLinks: Boolean = true,
      maybeSubsystem: Option[String] = None,
      maybeComponent: Option[String] = None
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    // Map from FITS keyword and channel to the tag for that keyword/channel
    val tagMap: Map[FitsKeywordAndChannel, String] =
      if (includeTags)
        fitsDictionary.fitsTags.tags.view.values
          .flatMap(_.map(f => (FitsKeywordAndChannel(f.keyword, f.channel), f.tag)))
          .toMap
      else Map.empty

    def makeFitsTableRows() = {
      fitsDictionary.fitsKeys.map { fitsKey =>
        // If a subsystem and optional component are given, restrict channels to those
        val channels =
          if (maybeSubsystem.isEmpty) fitsKey.channels
          else
            fitsKey.channels.filter(c =>
              maybeSubsystem.contains(c.source.subsystem) && (maybeComponent.isEmpty ||
                maybeComponent.contains(c.source.componentName))
            )
        val iList = channels.indices.toList
        val zList = channels.zip(iList)
        tr(
          td(if (withLinks) a(id := fitsKey.name, name := fitsKey.name)(fitsKey.name) else fitsKey.name),
          td(raw(fitsKey.description)),
          td(fitsKey.`type`),
          td(fitsKey.units),
          td(zList.map(p => makeLinkForFitsKeySource(fitsKey, p._1, p._2, withLinks, tagMap)))
        )
      }
    }

    div(id := "FITS-Keys")(
      nh.H3(titleStr, "FITS-Keys"),
      table(
        attr("data-bs-toggle") := "table",
        thead(
          tr(th("Name"), th("Description"), th("Type"), th("Units"), th("Tag: Source"))
        ),
        tbody(
          makeFitsTableRows()
        )
      )
    )
  }

  /**
   * Returns HTML describing the given components in the given subsystem.
   *
   * @param db                 the icd database
   * @param subsystemInfo      contains info about the subsystem
   * @param infoList           details about each component and what it publishes, subscribes to, etc.
   * @param pdfOptions         options for pdf generation
   * @param clientApi          if true, include subscribed events, sent commands
   * @param fitsDictionary     FITS keys and tags
   * @return the html tags
   */
  def getApiAsHtml(
      db: IcdDb,
      subsystemInfo: SubsystemInfo,
      infoList: List[ComponentInfo],
      pdfOptions: PdfOptions,
      clientApi: Boolean,
      fitsDictionary: FitsDictionary
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    val nh                 = new NumberedHeadings
    val titleInfo          = TitleInfo(subsystemInfo, None, None, documentNumber = pdfOptions.documentNumber)
    val summaryTable       = SummaryTable(subsystemInfo, None, infoList, nh, clientApi, displayTitle = true)
    val summaryTableMarkup = summaryTable.displaySummary()
    val fitsTable =
      if (fitsDictionary.fitsKeys.nonEmpty)
        makeFitsKeyTable(
          fitsDictionary,
          nh,
          "FITS Keywords",
          includeTags = false,
          maybeSubsystem = Some(subsystemInfo.sv.subsystem),
          maybeComponent = subsystemInfo.sv.maybeComponent
        )
      else div()

    val mainContent = div(
      style := "width: 100%;",
      summaryTableMarkup,
      fitsTable,
      displayDetails(db, infoList, summaryTable, nh, forApi = true, pdfOptions, clientApi)
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
    import scalatags.Text.all.*
    titleInfo.maybeSubtitle match {
      case Some(subtitle) =>
        titleInfo.maybeDocumentNumber match {
          case Some(documentNumber) =>
            h3(a(name := titleId), cls := "page-header")(
              titleInfo.title,
              br,
              small(cls := "text-secondary")(subtitle),
              br,
              small(cls := "text-secondary")(documentNumber)
            )
          case None =>
            h3(a(name := titleId), cls := "page-header")(titleInfo.title, br, small(cls := "text-secondary")(subtitle))
        }
      case None =>
        titleInfo.maybeDocumentNumber match {
          case Some(documentNumber) =>
            h3(a(name := titleId), cls := "page-header")(titleInfo.title, br, small(cls := "text-secondary")(documentNumber))
          case None =>
            h3(a(name := titleId), cls := "page-header")(titleInfo.title)
        }
    }
  }

  /**
   * Displays the details of the events published and commands received by the subsystem
   *
   * @param db the icd database
   * @param infoList list of component info
   * @param summaryTable object used to create a summary table (of subsciber/client info)
   * @param nh       used for numbered headings and TOC
   * @param forApi   true if this is for an API document, false for ICD
   * @param pdfOptions   options for pdf gen
   * @param clientApi   if true, display subscribed events, sent commands
   * @return the HTML
   */
  def displayDetails(
      db: IcdDb,
      infoList: List[ComponentInfo],
      summaryTable: SummaryTable,
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    div(
      infoList.map(displayComponentInfo(db, _, summaryTable, nh, forApi, pdfOptions, clientApi))
    )
  }

  /**
   * Displays the information for a component
   */
  private def displayComponentInfo(
      db: IcdDb,
      info: ComponentInfo,
      summaryTable: SummaryTable,
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    // For ICDs, only display published items/received commands, for APIs show everything
    // (Note: Need to include even if only subscribed items are defined, to keep summary links from breaking)
    // (XXX Aug 2020: APIs should only show pub and recv, added clientApi option to show sub/send)
    // (XXX Apr 2024: ICDs should show client summary with links to provider side)
    if (
      forApi ||
      (info.publishes.isDefined && info.publishes.get.nonEmpty
        || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
        || info.commands.isDefined && info.commands.get.nonEmpty
        || info.services.isDefined && info.services.get.nonEmpty)
    ) {
      markupForComponent(db, info, summaryTable, nh, forApi, pdfOptions, clientApi)
    }
    else div()
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(
      db: IcdDb,
      info: ComponentInfo,
      summaryTable: SummaryTable,
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    div(cls := "pagebreakBefore")(
      nh.H2(info.componentModel.title, info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel, info.publishes, nh, forApi, pdfOptions, clientApi),
      if (forApi && clientApi) subscribeMarkup(info.componentModel, info.subscribes, nh, forApi, pdfOptions) else div(),
      commandsMarkup(info.componentModel, info.commands, nh, forApi, pdfOptions, clientApi),
      servicesMarkup(db, info.componentModel, info.services, nh, forApi, pdfOptions, clientApi),
      if (!forApi)
        summaryTable
          .copy(
            infoList = List(info),
            subsystemInfo = summaryTable.subsystemInfo
              .copy(sv = summaryTable.subsystemInfo.sv.copy(maybeComponent = Some(info.componentModel.component)))
          )
          .subscribedSummary()
      else div()
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
    import scalatags.Text.all.*

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
    import scalatags.Text.all.*
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.nonEmpty || (commands.commandsSent.nonEmpty && forApi && clientApi)) {
          div(
            nh.H3(s"Commands for ${component.component}"),
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, nh, pdfOptions, clientApi),
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
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

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
                resultMarkup(m.maybeResult),
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

  // Generates the markup for the services section (description plus provides and requires)
  private def servicesMarkup(
      db: IcdDb,
      component: ComponentModel,
      maybeServices: Option[Services],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    maybeServices match {
      case None => div()
      case Some(services) =>
        if (services.servicesProvided.nonEmpty || (services.servicesRequired.nonEmpty && forApi && clientApi)) {
          div(
            raw(services.description),
            servicesProvidedMarkup(component, services.servicesProvided, nh, pdfOptions, clientApi),
            if (forApi && clientApi) servicesRequiredMarkup(db, component, services.servicesRequired, nh) else div()
          )
        }
        else div()
    }
  }

//  private def servicesRequiredesRequiredTitle(compName: String): String = s"Services Required by $compName"

  // Generates the HTML markup to display the HTTP services a component requires
  private def servicesRequiredMarkup(
      db: IcdDb,
      component: ComponentModel,
      info: List[ServicesRequiredInfo],
      nh: NumberedHeadings
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    if (info.isEmpty) div()
    else {
      div(
        for (s <- info) yield {
          val maybeOpenApi = s.maybeServiceModelProvider.map(_.openApi) match {
            case Some(openApi) => Some(openApi)
            case None =>
              db.getOpenApi(
                s.serviceModelClient.subsystem,
                s.serviceModelClient.component,
                s.serviceModelClient.name,
                None,
                s.serviceModelClient.paths
              )
          }
          val m = s.serviceModelClient
          val providerInfo = {
            val provider = s"${s.serviceModelClient.subsystem}.${s.serviceModelClient.component}"
            span(strong(s"Provider: "), provider)
          }
          val compName = component.component
          val linkId   = idFor(compName, "requires", "Services", m.subsystem, m.component, m.name)
          div(cls := "nopagebreak")(
            nh.H3(s"HTTP Service: ${m.name}", linkId),
            p(providerInfo),
            maybeOpenApi match {
              case Some(openApi) =>
                val filteredOpenApi = OpenApiToHtml.filterOpenApiJson(openApi, s.serviceModelClient.paths)
                div(
                  raw(OpenApiToHtml.getHtml(filteredOpenApi))
                )
              case None =>
                div()
            }
          )
        }
      )
    }
  }

  // Generates the HTML markup to display the HTTP services a component provides
  private def servicesProvidedMarkup(
      component: ComponentModel,
      info: List[ServiceProvidedInfo],
      nh: NumberedHeadings,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    val compName     = component.component
    val providerInfo = span(strong("Service Provider: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        for (s <- info) yield {
          val m = s.serviceModelProvider
          val consumerInfo = if (clientApi) {
            val consumers = s.requiredBy.distinct.map(s => s"${s.component.subsystem}.${s.component.component}").mkString(", ")
            span(strong(s"Consumers: "), if (consumers.isEmpty) "none" else consumers)
          }
          else span
          val linkId          = idFor(compName, "provides", "Services", component.subsystem, compName, m.name)
          val showDetails     = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
          val paths           = s.requiredBy.flatMap(_.paths).distinct
          val filteredOpenApi = OpenApiToHtml.filterOpenApiJson(m.openApi, paths)
          div(cls := "nopagebreak")(
            nh.H3(s"HTTP Service: ${m.name}", linkId),
            if (clientApi) p(consumerInfo, ", ", providerInfo) else p(providerInfo),
            if (showDetails) {
              div(
                raw(OpenApiToHtml.getHtml(filteredOpenApi))
              )
            }
            else
              div(
                p(m.description)
              )
          )
        }
      )
    }
  }

  private def resultTypeMarkup(parameterList: List[ParameterModel]): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- parameterList) yield List(a.name, a.description, a.typeStr, a.units)
      div(cls := "nopagebreak")(
        p(strong(a("Command Result Parameters"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  private def resultMarkup(maybeResult: Option[CommandResultModel]): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    if (maybeResult.isEmpty) div()
    else {
      val result = maybeResult.get
      div(cls := "nopagebreak")(
        if (result.description.nonEmpty) div(p(strong("Command Results")), raw(result.description))
        else div(),
        resultTypeMarkup(result.parameters)
      )
    }
  }

  // Used for command parameters
  private def parameterListMarkup(
      nameStr: String,
      parameterList: List[ParameterModel],
      requiredArgs: List[String]
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "Required")
      val rowList =
        for (a <- parameterList)
          yield List(
            a.name,
            a.description,
            a.typeStr,
            a.units,
            a.defaultValue,
            yesNo(requiredArgs.contains(a.name))
          )
      div(cls := "nopagebreak")(
        p(strong(a(s"Parameters for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  private def receivedCommandsTitle(compName: String): String = s"Command Configurations Received by $compName"

//  private def servicesProvidedTitle(compName: String): String = s"HTTP Services provided by $compName"

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(
      component: ComponentModel,
      maybeSubscribes: Option[Subscribes],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    val compName       = component.component
    val subscriberInfo = span(strong("Subscriber: "), s"${component.subsystem}.$compName")

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]): Text.TypedTag[String] = {

      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) =
        info.warning.map { msg =>
          p(em(" Warning: ", msg))
        }

      def subscribeDetailsMarkup(si: DetailedSubscribeInfo) = {
        val sInfo = si.subscribeModelInfo

        def getImageDetailsTable = {
          // Markup for subscribed image
          val imageModel = si.imageModel.get
          val maxRate    = imageModel.maybeMaxRate
          val imageSize  = s"${imageModel.size._1} x ${imageModel.size._2}"
          div(
            table(
              thead(
                tr(
                  th("Subsystem"),
                  th("Component"),
                  th("Prefix.Name"),
                  th("Channel"),
                  th("Size"),
                  th("Pixel Size"),
                  th("Max Rate")
                )
              ),
              tbody(
                tr(
                  td(sInfo.subsystem),
                  td(sInfo.component),
                  td(si.path),
                  td(imageModel.channel),
                  td(imageSize),
                  td(imageModel.pixelSize.toString),
                  td(HtmlMarkup.formatRate(maxRate))
                )
              )
            ),
            si.imageModel.map(t => imageMetadataListMarkup(t.name, t.metadataList))
          )
        }

        def getEventDetailsTable = {
          // Markup for subscribed event
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
            si.eventModel.map(t => eventParameterListListMarkup(t.name, t.parameterList, forApi))
          )
        }

        if (si.imageModel.nonEmpty)
          getImageDetailsTable
        else if (si.eventModel.nonEmpty)
          getEventDetailsTable
        else div()
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
              if (showDetails) subscribeDetailsMarkup(si) else div()
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
            subscribeListMarkup("Images", subscribes.subscribeInfo.filter(_.itemType == Images)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
          )
        }
        else div()
    }
  }

  private def subscribeTitle(compName: String): String = s"Items subscribed to by $compName"

  private def makeErrorDiv(msg: String): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    div(cls := "alert alert-warning", role := "alert")(
      i(cls := "bi bi-exclamation-triangle"),
      span(em(s" $msg"))
    )
  }

  // Used for event parameters
  private def eventParameterListListMarkup(
      nameStr: String,
      parameterList: List[ParameterModel],
      forApi: Boolean,
      linkId: Option[String] = None
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "FITS Keywords")
      val rowList =
        for (a <- parameterList) yield {
          val paramId    = linkId.map(s => s"$s.${a.name}")
          val nameAnchor = paramId.map(p => s"<a id='$p' name='$p'>${a.name}</a>").getOrElse(a.name)
          val fitsKeywordLinks =
            if (forApi)
              a.getFitsKeys.map(k => s"<a href=#$k>$k</a>").mkString(", ")
            else
              a.getFitsKeys.mkString(", ")
          List(nameAnchor, a.description, a.typeStr, a.units, a.defaultValue, fitsKeywordLinks)
        }
      div(cls := "nopagebreak")(
        p(strong(a(s"Parameters for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
      )
    }
  }

  private def imageMetadataListMarkup(
      nameStr: String,
      metadataList: List[MetadataModel],
      linkId: Option[String] = None
  ): Text.TypedTag[String] = {
    import scalatags.Text.all.*
    if (metadataList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Keyword")
      val rowList =
        for (a <- metadataList) yield {
          val metadataId = linkId.map(s => s"$s.${a.name}")
          val nameAnchor = metadataId.map(p => s"<a id='$p' name='$p'>${a.name}</a>").getOrElse(a.name)
          List(nameAnchor, a.description, a.keyword)
        }
      div(cls := "nopagebreak")(
        p(strong(a(s"Image Metadata for $nameStr"))),
        HtmlMarkup.mkTable(headings, rowList)
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
    import scalatags.Text.all.*

    val compName      = component.component
    val publisherInfo = span(strong("Publisher: "), s"${component.subsystem}.$compName")

    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]): Text.TypedTag[String] = {
      def categoryItem(eventModel: EventModel) = {
        if (pubType == "Events") span(strong("Category: "), eventModel.getCategory) else span()
      }
      if (eventList.isEmpty) div()
      else {
        val showArchiveInfo = pubType != "Observe Events"
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
            val rowList =
              if (showArchiveInfo)
                List(
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
              else Nil
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
                  categoryItem(eventModel),
                  raw(eventModel.description),
                  if (eventModel.refError.startsWith("Error:")) makeErrorDiv(eventModel.refError) else div(),
                  subscriberUsage,
                  if (showArchiveInfo) HtmlMarkup.mkTable(headings, rowList) else div(),
                  eventParameterListListMarkup(eventModel.name, eventModel.parameterList, forApi, Some(linkId)),
                  hr
                )
              }
              else
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  categoryItem(eventModel),
                  raw(eventModel.description),
                  subscriberUsage
                )
            )
          }
        )
      }
    }

    def publishImageListMarkup(pubType: String, imageList: List[ImageInfo]): Text.TypedTag[String] = {
      if (imageList.isEmpty) div()
      else {
        div(
          for (imageInfo <- imageList) yield {
            val imageModel  = imageInfo.imageModel
            val linkId      = idFor(compName, "publishes", pubType, component.subsystem, compName, imageModel.name)
            val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
            val subscribers =
              imageInfo.subscribers
                .map(s => s"${s.componentModel.subsystem}.${s.componentModel.component}")
                .distinct
                .mkString(", ")
            val subscriberInfo =
              if (clientApi)
                span(strong(s"Subscribers: "), if (subscribers.isEmpty) "none" else subscribers)
              else span
            // Include usage text from subscribers that define it
            val subscriberUsage =
              if (clientApi)
                div(
                  imageInfo.subscribers.map(s =>
                    if (s.subscribeModelInfo.usage.isEmpty) div()
                    else
                      div(
                        strong(s"Usage by ${s.componentModel.subsystem}.${s.componentModel.component}: "),
                        raw(s.subscribeModelInfo.usage)
                      )
                  )
                )
              else span

            val imageSize = imageModel.size match {
              case (0, 0) => ""
              case (w, h) => s"$w x $h"
            }
            val headings =
              List("Channel", "Format", "Size", "Pixel Size", "Max Rate")
            val rowList =
              List(
                List(
                  imageModel.channel,
                  imageModel.format,
                  imageSize,
                  imageModel.pixelSize.toString,
                  HtmlMarkup.formatRate(imageModel.maybeMaxRate).render
                )
              )

            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${imageModel.name}",
                linkId
              ),
              if (showDetails) {
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  HtmlMarkup.mkTable(headings, rowList),
                  raw(imageModel.description),
                  subscriberUsage,
                  imageMetadataListMarkup(imageModel.name, imageModel.metadataList, Some(linkId)),
                  hr
                )
              }
              else
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  HtmlMarkup.mkTable(headings, rowList),
                  raw(imageModel.description),
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
                  if (m.probableCause.isEmpty) div() else p(strong("Probable Cause: "), raw(m.probableCause)),
                  if (m.operatorResponse.isEmpty) div() else p(strong("Operator Response: "), raw(m.operatorResponse)),
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
            publishImageListMarkup("Images", publishes.imageList),
            publishAlarmListMarkup(publishes.alarmList, pdfOptions)
          )
        }
        else div()
    }
  }

  // Generates a one line table with basic component information
  private def componentInfoTableMarkup(info: ComponentInfo): Text.TypedTag[String] = {
    import scalatags.Text.all.*
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
    import scalatags.Text.all.*
    if (titleInfo.maybeDescription.isDefined) {
      div(raw(titleInfo.maybeDescription.get))
    }
    else div
  }

}
