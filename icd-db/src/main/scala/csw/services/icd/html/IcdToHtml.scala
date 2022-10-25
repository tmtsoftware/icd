package csw.services.icd.html

import icd.web.shared.ComponentInfo._
import icd.web.shared._
import scalatags.Text
import Headings.idFor
import HtmlMarkup.yesNo
import icd.web.shared.IcdModels.{AlarmModel, ComponentModel, EventModel, MetadataModel, ParameterModel}

/**
 * Handles converting model files to static HTML for use in generating a PDF
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

  // Makes the link for a FITS keyword source to the event that is the source of the keyword
  private def makeLinkForFitsKeySource(
      fitsKey: FitsKeyInfo,
      fitsChannel: FitsChannel,
      index: Int,
      withLinks: Boolean,
      tagMap: Map[String, String]
  ) = {
    import scalatags.Text.all._
    val fitsSource = fitsChannel.source
    // Get tag for key
    val maybeTag =
      if (fitsChannel.name.isEmpty) {
        tagMap.get(fitsKey.name)
      }
      else {
        tagMap.get(s"${fitsKey.name}/${fitsChannel.name}")
      }

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
   * @param maybeTag if defined, list is restricted to tag
   * @param fitsDictionary fits keywords and tags
   * @param nh used for headings
   * @param withLinks if true, include links to event parameters for key sources
   */
  def makeFitsKeyTable(
      maybeTag: Option[String],
      fitsDictionary: FitsDictionary,
      nh: Headings,
      withLinks: Boolean = true
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    import icd.web.shared.SharedUtils.MapInverter

    // Map from FITS keyword to list of tags for that keyword
    val tagMap = if (maybeTag.isEmpty) fitsDictionary.fitsTags.tags.invert else Map.empty[String, String]

    def makeFitsTableRows() = {
      fitsDictionary.fitsKeys.map { fitsKey =>
        val iList = fitsKey.channels.indices.toList
        val zList = fitsKey.channels.zip(iList)
        tr(
          td(if (withLinks) a(id := fitsKey.name, name := fitsKey.name)(fitsKey.name) else fitsKey.name),
          td(raw(fitsKey.description)),
          td(fitsKey.typ),
          td(fitsKey.units),
          td(zList.map(p => makeLinkForFitsKeySource(fitsKey, p._1, p._2, withLinks, tagMap)))
        )
      }
    }

    val s = maybeTag.map(t => s" (tag: $t)").getOrElse("")
    div(id := "FITS-Keys")(
      nh.H3(s"FITS Dictionary$s", "FITS-Keys"),
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
   * @param maybeSubsystemInfo contains info about the subsystem, if known
   * @param infoList           details about each component and what it publishes, subscribes to, etc.
   * @param pdfOptions         options for pdf generation
   * @param clientApi          if true, include subscribed events, sent commands
   * @param fitsDictionary     FITS keys and tags
   * @return the html tags
   */
  def getApiAsHtml(
      maybeSubsystemInfo: Option[SubsystemInfo],
      infoList: List[ComponentInfo],
      pdfOptions: PdfOptions,
      clientApi: Boolean,
      fitsDictionary: FitsDictionary
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
//      if (fitsDictionary.fitsKeys.nonEmpty) makeFitsKeyTable(None, fitsDictionary, nh) else div(),
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
        h3(a(name := titleId), cls := "page-header")(titleInfo.title, br, small(cls := "text-secondary")(subtitle))
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
      || info.commands.isDefined && info.commands.get.nonEmpty
      || info.services.isDefined && info.services.get.nonEmpty)
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
      commandsMarkup(info.componentModel, info.commands, nh, forApi, pdfOptions, clientApi),
      servicesMarkup(info.componentModel, info.services, nh, forApi, pdfOptions, clientApi)
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

  // Generates the markup for the services section (description plus provides and requires)
  private def servicesMarkup(
      component: ComponentModel,
      maybeServices: Option[Services],
      nh: NumberedHeadings,
      forApi: Boolean,
      pdfOptions: PdfOptions,
      clientApi: Boolean
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    maybeServices match {
      case None => div()
      case Some(services) =>
        if (services.servicesProvided.nonEmpty || (services.servicesRequired.nonEmpty && forApi && clientApi)) {
          div(
            nh.H3(s"Services for ${component.component}"),
            raw(services.description),
            servicesProvidedMarkup(component, services.servicesProvided, nh, pdfOptions, clientApi),
            if (forApi && clientApi) servicesRequiredMarkup(component, services.servicesRequired, nh) else div()
          )
        }
        else div()
    }
  }

  private def servicesRequiredTitle(compName: String): String = s"Services Required by $compName"

  // Generates the HTML markup to display the HTTP services a component requires
  private def servicesRequiredMarkup(
      component: ComponentModel,
      info: List[ServicesRequiredInfo],
      nh: NumberedHeadings
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._

    val compName = component.component
    if (info.isEmpty) div()
    else {
      div(
        nh.H4(servicesRequiredTitle(compName)),
        for (s <- info) yield {
          val maybeOpenApi = s.maybeServiceModelProvider.map(_.openApi)
          val m            = s.serviceModelClient
          val providerInfo = {
            val provider = s"${s.serviceModelClient.subsystem}.${s.serviceModelClient.component}"
            span(strong(s"Provider: "), provider)
          }
          div(cls := "nopagebreak")(
            nh.H5(s"HTTP Service: ${m.name}"),
            p(providerInfo),
//            p("Note: Only the routes required by the client are listed here."),
            maybeOpenApi match {
              case Some(openApi) =>
                div(
                  raw(OpenApiToHtml.getHtml(openApi))
                )
              case None => div()
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
    import scalatags.Text.all._

    val compName     = component.component
    val providerInfo = span(strong("Service Provider: "), s"${component.subsystem}.$compName")

    if (info.isEmpty) div()
    else {
      div(
        nh.H4(servicesProvidedTitle(compName)),
        for (s <- info) yield {
          val m = s.serviceModelProvider
          val consumerInfo = if (clientApi) {
            val consumers = s.requiredBy.distinct.map(s => s"${s.subsystem}.${s.component}").mkString(", ")
            span(strong(s"Consumers: "), if (consumers.isEmpty) "none" else consumers)
          }
          else span
          val linkId      = idFor(compName, "provides", "Services", component.subsystem, compName, m.name)
          val showDetails = pdfOptions.details || pdfOptions.expandedIds.contains(linkId)
          div(cls := "nopagebreak")(
            nh.H5(s"HTTP Service: ${m.name}", linkId),
            if (clientApi) p(consumerInfo, ", ", providerInfo) else p(providerInfo),
            if (showDetails) {
              div(
                raw(OpenApiToHtml.getHtml(m.openApi))
              )
            }
            else
              div(
                // XXX TODO: Need an extra description field?
                p(s"Details of the ${m.name} HTTP service are not included.")
              )
          )
        }
      )
    }
  }

  private def resultTypeMarkup(parameterList: List[ParameterModel]): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- parameterList) yield List(a.name, a.description, a.typeStr, a.units)
      div(cls := "nopagebreak")(
        p(strong(a("Result Type Parameters"))),
        HtmlMarkup.mkTable(headings, rowList),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError))
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

  private def servicesProvidedTitle(compName: String): String = s"HTTP Services provided by $compName"

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

      def subscribeDetailsMarkup(si: DetailedSubscribeInfo) = {
        val sInfo = si.subscribeModelInfo
        if (si.imageModel.nonEmpty) {
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
        else {
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
            si.eventModel.map(t => attributeListMarkup(t.name, t.parameterList))
          )
        }
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
    import scalatags.Text.all._
    div(cls := "alert alert-warning", role := "alert")(
      i(cls := "bi bi-exclamation-triangle"),
      span(em(s" $msg"))
    )
  }

  private def attributeListMarkup(
      nameStr: String,
      parameterList: List[ParameterModel],
      linkId: Option[String] = None
  ): Text.TypedTag[String] = {
    import scalatags.Text.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default", "FITS Keywords")
      val rowList =
        for (a <- parameterList) yield {
          val paramId          = linkId.map(s => s"$s.${a.name}")
          val nameAnchor       = paramId.map(p => s"<a id='$p' name='$p'>${a.name}</a>").getOrElse(a.name)
          val fitsKeywordLinks = a.fitsKeys.map(k => s"<a href=#$k>$k</a>").mkString(", ")
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
    import scalatags.Text.all._
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
    import scalatags.Text.all._

    val compName      = component.component
    val publisherInfo = span(strong("Publisher: "), s"${component.subsystem}.$compName")
    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]): Text.TypedTag[String] = {
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
                  raw(eventModel.description),
                  if (eventModel.refError.startsWith("Error:")) makeErrorDiv(eventModel.refError) else div(),
                  subscriberUsage,
                  if (showArchiveInfo) HtmlMarkup.mkTable(headings, rowList) else div(),
                  attributeListMarkup(eventModel.name, eventModel.parameterList, Some(linkId)),
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
            div(cls := "nopagebreak")(
              nh.H4(
                s"${singlePubType(pubType)}: ${imageModel.name}",
                linkId
              ),
              if (showDetails) {
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
                  raw(imageModel.description),
                  subscriberUsage,
                  imageMetadataListMarkup(imageModel.name, imageModel.metadataList, Some(linkId)),
                  hr
                )
              }
              else
                div(
                  if (clientApi) p(publisherInfo, ", ", subscriberInfo) else p(publisherInfo),
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
            publishImageListMarkup("Images", publishes.imageList),
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
