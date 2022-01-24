package icd.web.client

import java.util.UUID

import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.{HTMLButtonElement, HTMLDivElement, HTMLElement, HTMLTableRowElement}

import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import scala.concurrent.Future
import Components._
import org.scalajs.dom.html.{Anchor, Div, Element}
import play.api.libs.json._

import scala.util.Failure
import scalatags.JsDom.TypedTag
import Headings.idFor

object Components {

  // Id of component info for given component name
  def getComponentInfoId(compName: String): String = compName

  /**
   * Information about a link to a component
   *
   * @param subsystem the component's subsystem
   * @param compName  the component name
   */
  case class ComponentLink(subsystem: String, compName: String)

  trait ComponentListener {

    /**
     * Called when a link for the component is clicked
     *
     * @param link conatins the component's subsystem and name
     */
    def componentSelected(link: ComponentLink): Unit
  }

  // Displayed version for unpublished APIs
  val unpublished = "(unpublished)"

  def yesNo(b: Boolean): String = if (b) "yes" else "no"

  /**
   * Returns a HTML table with the given column headings and list of rows
   *
   * @param headings   the table headings
   * @param rowList    list of row data
   * @param tableStyle optional table style
   * @return an html table element
   */
  def mkTable(
      headings: List[String],
      rowList: List[List[String]],
      tableStyle: scalacss.StyleA = Styles.emptyStyle
  ): TypedTag[HTMLElement] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Returns a table cell markup, checking if the text is already in html format (after markdown processing)
    def mkTableCell(text: String) = {
      if (text.startsWith("<"))
        td(raw(text))
      else
        td(p(text))
    }

    if (rowList.isEmpty) div()
    else {
      val (newHead, newRows) = SharedUtils.compact(headings, rowList)
      if (newHead.isEmpty) div()
      else {
        table(
          tableStyle,
          attr("data-toggle") := "table",
          thead(
            tr(newHead.map(th(_)))
          ),
          tbody(
            for (row <- newRows) yield {
              tr(row.map(mkTableCell))
            }
          )
        )
      }
    }
  }
}

/**
 * Manages the component (Assembly, HCD) display
 *
 * @param mainContent used to display information about selected components
 * @param listener    called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
//noinspection DuplicatedCode,SameParameterValue
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components._
  import icd.web.shared.JsonSupport._

  // Action when user clicks on a component link
  private def clickedOnComponent(subsystem: String, component: String)(e: dom.Event): Unit = {
    e.preventDefault()
    listener.componentSelected(ComponentLink(subsystem, component))
  }

  // Makes the link for a component in the table
  private def makeLinkForComponent(subsystem: String, component: String): TypedTag[Anchor] = {
    import scalatags.JsDom.all._
    a(
      title := s"Show API for $subsystem.$component",
      s"$subsystem.$component ",
      href := "#",
      onclick := clickedOnComponent(subsystem, component) _
    )
  }

  // Makes the link for a component in the table
  private def makeLinkForComponent(componentModel: ComponentModel): TypedTag[Anchor] = {
    makeLinkForComponent(componentModel.subsystem, componentModel.component)
  }

  /**
   * Gets information about the given components
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional target subsystem and version
   * @param searchAllSubsystems if true search all TMT subsystems for API dependencies
   * @param clientApi if true include subscribed events, sent commands in API
   * @return future list of objects describing the components
   */
  private def getComponentInfo(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    Fetch
      .get(ClientRoutes.icdComponentInfo(sv, maybeTargetSv, searchAllSubsystems, clientApi))
      .map { text =>
        val list = Json.fromJson[Array[ComponentInfo]](Json.parse(text)).map(_.toList).getOrElse(Nil)
        if (maybeTargetSv.isDefined) list.map(ComponentInfo.applyIcdFilter).filter(ComponentInfo.nonEmpty) else list
      }
  }

  /**
   * Gets the list of components for the given subsystem and then gets the information for them
   */
  private def getComponentInfo(
      maybeSubsystem: Option[SubsystemWithVersion],
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    maybeSubsystem match {
      case None =>
        Future.successful(Nil)
      case Some(sv) =>
        getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems, clientApi)
    }
  }

  // Gets top level subsystem info from the server
  private def getSubsystemInfo(sv: SubsystemWithVersion): Future[SubsystemInfo] = {
    val path = ClientRoutes.subsystemInfo(sv.subsystem, sv.maybeVersion)
    Fetch.get(path).map { text =>
      val subsystemInfo = Json.fromJson[SubsystemInfo](Json.parse(text)).get
      subsystemInfo.copy(sv = sv) // include the component, if specified
    }
  }

  // Gets additional information about the ICD between the two subsystems
  private def getIcdModelList(sv: SubsystemWithVersion, tv: SubsystemWithVersion): Future[List[IcdModel]] = {
    val path = ClientRoutes.icdModelList(sv, tv)
    Fetch.get(path).map { text =>
      Json.fromJson[List[IcdModel]](Json.parse(text)).get
    }
  }

  /**
   * Adds components to the display.
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param maybeTargetSubsystem optional target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   * @param searchAllSubsystems  if true, search all subsystems for API dependencies
   * @param clientApi            if true include subscribed events and sent commands in API
   * @return a future list of ComponentInfo (one entry for each component in the result)
   */
  def addComponents(
      sv: SubsystemWithVersion,
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      searchAllSubsystems: Boolean,
      clientApi: Boolean
  ): Future[List[ComponentInfo]] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val isIcd = maybeTargetSubsystem.isDefined
    val f = for {
      subsystemInfo <- getSubsystemInfo(sv)
      maybeTargetSubsystemInfo <-
        maybeTargetSubsystem
          .map(getSubsystemInfo(_).map(i => Some(i)))
          .getOrElse(Future.successful(None))
      icdInfoList    <- if (isIcd) getIcdModelList(sv, maybeTargetSubsystem.get) else Future.successful(Nil)
      infoList       <- getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems, clientApi)
      targetInfoList <- getComponentInfo(maybeTargetSubsystem, Some(sv), searchAllSubsystems, clientApi)
    } yield {
      val titleInfo        = TitleInfo(subsystemInfo, maybeTargetSubsystem, maybeIcd)
      val subsystemVersion = sv.maybeVersion.getOrElse(TitleInfo.unpublished)
      mainContent.clearContent()
      mainContent.setTitle(titleInfo.title, titleInfo.maybeSubtitle, titleInfo.maybeDescription)
      // For ICDs, add the descriptions of the two subsystems at top
      if (isIcd) {
        val targetSubsystemInfo    = maybeTargetSubsystemInfo.get
        val targetSubsystemVersion = maybeTargetSubsystem.flatMap(_.maybeVersion).getOrElse(TitleInfo.unpublished)
        mainContent.appendElement(
          div(
            Styles.component,
            p(strong(s"${subsystemInfo.sv.subsystem}: ${subsystemInfo.title} $subsystemVersion")),
            raw(subsystemInfo.description),
            p(strong(s"${targetSubsystemInfo.sv.subsystem}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
            raw(targetSubsystemInfo.description),
            icdInfoList.map(i => div(p(strong(i.titleStr)), raw(i.description)))
          ).render
        )
      }
      // XXX TODO FIXME: Hyperlinks to other subsystems can't be made in the summary table,
      // since the code is shared with non-javascript code on the server side.
      val summaryTable =
        SummaryTable.displaySummary(subsystemInfo, maybeTargetSubsystem, infoList, new HtmlHeadings, clientApi).render

      mainContent.appendElement(div(Styles.component, id := "Summary")(raw(summaryTable)).render)
      infoList.foreach(i => displayComponentInfo(i, !isIcd, clientApi))
      if (isIcd) targetInfoList.foreach(i => displayComponentInfo(i, forApi = false, clientApi))
      infoList ++ targetInfoList
    }
    f.onComplete {
      case Failure(ex) => mainContent.displayInternalError(ex)
      case _           =>
    }
    f
  }

  // Removes the component display
  def removeComponentInfo(compName: String): Unit = {
    val elem = $id(getComponentInfoId(compName))
    if (elem != null) {
      // remove inner content so we can reuse the div and keep the position on the page
      elem.innerHTML = ""
    }
  }

  /**
   * Displays the information for a component, appending to the other selected components, if any.
   *
   * @param info contains the information to display
   */
  private def displayComponentInfo(info: ComponentInfo, forApi: Boolean, clientApi: Boolean): Unit = {
    if (
      forApi || (info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
      || info.commands.get.commandsSent.nonEmpty)
      || info.services.isDefined && (info.services.get.servicesProvided.nonEmpty || info.services.get.servicesRequired.nonEmpty))
    ) {
      val markup     = markupForComponent(info, forApi, clientApi).render
      val oldElement = $id(getComponentInfoId(info.componentModel.component))
      if (oldElement == null) {
        mainContent.appendElement(markup)
      }
      else {
        // Use existing div, so the component's position stays the same
        mainContent.replaceElement(oldElement, markup)
      }
    }
  }

  // HTML id for a table displaying the fields of a struct
  private def structIdStr(name: String): String = s"$name-struct"

  // Add a table for each attribute of type "struct" to show the members of the struct
  private def structParametersMarkup(parameterList: List[ParameterModel]): Seq[TypedTag[Div]] = {
    import scalatags.JsDom.all._
    val headings = List("Name", "Description", "Type", "Units", "Default")
    parameterList.flatMap { attrModel =>
      if (attrModel.typeStr == "struct" || attrModel.typeStr == "array of struct") {
        val rowList2 =
          for (a2 <- attrModel.parameterList)
            yield List(a2.name, a2.description, getTypeStr(a2.name, a2.typeStr), a2.units, a2.defaultValue)
        Some(
          div()(
            p(strong(a(name := structIdStr(attrModel.name))(s"Parameters for ${attrModel.name} struct"))),
            mkTable(headings, rowList2),
            attrModel.parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
            // Handle structs embedded in other structs (or arrays of structs, etc.)
            structParametersMarkup(attrModel.parameterList)
          )
        )
      }
      else None
    }
  }

  /**
   * Returns a table of attributes
   *
   * @param parameterList list of attributes to display
   * @return
   */
  private def parameterListMarkup(parameterList: List[ParameterModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList =
        for (a <- parameterList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units, a.defaultValue)
      div(
        strong("Parameters"),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
      )
    }
  }

  /**
   * Returns a table of parameters
   *
   * @param parameterList list of attributes to display
   * @param requiredArgs   a list of required arguments
   */
  private def parameterListMarkup(
      parameterList: List[ParameterModel],
      requiredArgs: List[String]
  ): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
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
      div(
        strong("Parameters"),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
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

  /**
   * Returns a table listing the attributes of a command result
   *
   * @param parameterList list of attributes to display
   */
  private def resultTypeMarkup(parameterList: List[ParameterModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (parameterList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- parameterList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units)
      div(
        strong("Result Type Parameters"),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        parameterList.filter(_.refError.startsWith("Error:")).map(a => makeErrorDiv(a.refError)),
        structParametersMarkup(parameterList)
      )
    }
  }

  /**
   * Returns a hidden, expandable table row containing the given div item
   *
   * @param item    the contents of the table row
   * @param colSpan the number of columns to span
   * @return a pair of (button, tr) elements, where the button toggles the visibility of the row
   */
  private def hiddenRowMarkup(
      item: TypedTag[HTMLDivElement],
      colSpan: Int
  ): (TypedTag[HTMLButtonElement], TypedTag[HTMLTableRowElement]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    // button to toggle visibility
    val idStr = UUID.randomUUID().toString
    val btn = button(
      Styles.attributeBtn,
      attr("data-toggle") := "collapse",
      attr("data-target") := s"#$idStr",
      title := "Show/hide details"
    )(
      span(cls := "glyphicon glyphicon-collapse-down")
    )
    val row = tr(id := idStr, cls := "collapse panel-collapse")(td(colspan := colSpan)(item))
    (btn, row)
  }

  private def formatRate(maybeRate: Option[Double]) = {
    import scalatags.JsDom.all._
    val (maxRate, defaultMaxRateUsed) = EventModel.getMaxRate(maybeRate)
    val el                            = if (defaultMaxRateUsed) em(s"$maxRate Hz *") else span(s"$maxRate Hz")
    el.render.outerHTML
  }

  private def makeErrorDiv(msg: String): TypedTag[Div] = {
    import scalatags.JsDom.all._
    div(cls := "alert alert-warning", role := "alert")(
      span(cls := "glyphicon glyphicon-warning-sign", attr("aria-hidden") := "true"),
      span(em(s" $msg"))
    )
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(component: ComponentModel, maybePublishes: Option[Publishes], forApi: Boolean, clientApi: Boolean) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val compName = component.component

    // Returns a table row displaying more details for the given event
    def makeEventDetailsRow(eventInfo: EventInfo) = {
      val eventModel = eventInfo.eventModel
      val totalArchiveSpacePerYear =
        if (eventModel.totalArchiveSpacePerYear.isEmpty) ""
        else if (eventModel.maybeMaxRate.isEmpty) em(eventModel.totalArchiveSpacePerYear).render.outerHTML
        else span(eventModel.totalArchiveSpacePerYear).render.outerHTML
      val headings = List("Max Rate", "Archive", "Archive Duration", "Bytes per Event", "Year Accumulation")
      val rowList = List(
        List(
          formatRate(eventModel.maybeMaxRate),
          yesNo(eventModel.archive),
          eventModel.archiveDuration,
          eventModel.totalSizeInBytes.toString,
          totalArchiveSpacePerYear
        )
      )

      div(
        if (eventModel.refError.startsWith("Error:")) makeErrorDiv(eventModel.refError) else div(),
        if (eventModel.requirements.isEmpty) div()
        else p(strong("Requirements: "), eventModel.requirements.mkString(", ")),
        mkTable(headings, rowList),
        if (eventModel.maybeMaxRate.isEmpty) span("* Default maxRate of 1 Hz assumed.")
        else span(),
        parameterListMarkup(eventModel.parameterList)
      )
    }

    // Returns the markup for the published event
    def publishEventListMarkup(pubType: String, eventList: List[EventInfo]) = {
      if (eventList.isEmpty) div()
      else
        div(
          h3(s"$pubType Published by $compName"),
          table(
            attr("data-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description"),
                if (clientApi) th("Subscribers") else span
              )
            ),
            tbody(
              for (t <- eventList) yield {
                val (btn, row) = hiddenRowMarkup(makeEventDetailsRow(t), 3)
                List(
                  tr(
                    td(
                      Styles.attributeCell,
                      p(
                        btn,
                        a(name := idFor(compName, "publishes", pubType, component.subsystem, compName, t.eventModel.name))(
                          t.eventModel.name
                        )
                      )
                    ),
                    td(raw(t.eventModel.description)),
                    if (clientApi)
                      td(p(t.subscribers.map(_.componentModel).distinct.map(makeLinkForComponent)))
                    else span
                  ),
                  row
                )
              }
            )
          )
        )
    }

    // Returns a table row displaying more details for the given alarm
    def makeAlarmDetailsRow(m: AlarmModel) = {
      val headings = List("Severity Levels", "Location", "Alarm Type", "Auto Ack", "Latched")
      val rowList = List(
        List(m.severityLevels.mkString(", "), m.location, m.alarmType, yesNo(m.autoAck), yesNo(m.latched))
      )

      div(
        if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
        p(strong("Probable Cause: "), raw(m.probableCause)),
        p(strong("Operator Response: "), raw(m.operatorResponse)),
        mkTable(headings, rowList)
      )
    }

    // Returns the markup for the published alarms
    def publishAlarmListMarkup(alarmList: List[AlarmModel]) = {
      if (alarmList.isEmpty) div()
      else
        div(
          h3(s"Alarms Published by $compName"),
          table(
            attr("data-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description")
              )
            ),
            tbody(
              for (m <- alarmList) yield {
                val (btn, row) = hiddenRowMarkup(makeAlarmDetailsRow(m), 3)
                List(
                  tr(
                    td(
                      Styles.attributeCell,
                      p(btn, a(name := idFor(compName, "publishes", "Alarms", component.subsystem, compName, m.name))(m.name))
                    ),
                    td(raw(m.description))
                  ),
                  row
                )
              }
            )
          )
        )
    }

    def totalArchiveSpace(): TypedTag[Element] = {
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
        if (publishes.nonEmpty) {
          div(
            Styles.componentSection,
            raw(publishes.description),
            publishEventListMarkup("Events", publishes.eventList),
            publishEventListMarkup("Observe Events", publishes.observeEventList),
            if (forApi) totalArchiveSpace() else span(),
            publishEventListMarkup("Current States", publishes.currentStateList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        }
        else div()
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(component: ComponentModel, maybeSubscribes: Option[Subscribes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val compName = component.component
    // Returns a table row displaying more details for the given subscription
    def makeDetailsRow(si: DetailedSubscribeInfo) = {
      val sInfo   = si.subscribeModelInfo
      val maxRate = si.eventModel.flatMap(_.maybeMaxRate)
      val headings =
        List("Subsystem", "Component", "Prefix.Name", "Max Rate", "Publisher's Max Rate")
      val rowList = List(
        List(
          sInfo.subsystem,
          sInfo.component,
          si.path,
          formatRate(sInfo.maxRate),
          formatRate(maxRate)
        )
      )

      val attrTable = si.eventModel.map(t => parameterListMarkup(t.parameterList)).getOrElse(div())
      div(
        mkTable(headings, rowList),
        if (maxRate.isEmpty) span("* Default maxRate of 1 Hz assumed.") else span(),
        attrTable
      )
    }

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]) = {
      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) =
        info.warning.map(msg => makeErrorDiv(s" Warning: $msg"))

      if (subscribeList.isEmpty) div()
      else
        div(
          h3(s"$pubType Subscribed to by $compName"),
          div(
            Styles.componentSection,
            table(
              Styles.componentTable,
              attr("data-toggle") := "table",
              thead(
                tr(
                  th("Name"),
                  th("Description"),
                  th("Publisher")
                )
              ),
              tbody(
                for (s <- subscribeList) yield {
                  val (btn, row) = hiddenRowMarkup(makeDetailsRow(s), 3)
                  val usage =
                    if (s.subscribeModelInfo.usage.isEmpty) div()
                    else
                      div(
                        strong("Usage:"),
                        raw(s.subscribeModelInfo.usage)
                      )
                  List(
                    tr(
                      td(
                        Styles.attributeCell,
                        p(
                          btn,
                          a(
                            name := idFor(
                              compName,
                              "subscribes",
                              pubType,
                              s.subscribeModelInfo.subsystem,
                              s.subscribeModelInfo.component,
                              s.subscribeModelInfo.name
                            )
                          )(s.subscribeModelInfo.name)
                        )
                      ),
                      td(raw(s.description), getWarning(s), usage),
                      td(p(makeLinkForComponent(s.subscribeModelInfo.subsystem, s.subscribeModelInfo.component)))
                    ),
                    row
                  )
                }
              )
            )
          )
        )
    }

    maybeSubscribes match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            Styles.componentSection,
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

  // Returns a table row displaying more details for the given command
  private def makeReceivedCommandDetailsRow(m: ReceiveCommandModel) = {
    import scalatags.JsDom.all._
    div(
      if (m.refError.startsWith("Error:")) makeErrorDiv(m.refError) else div(),
      if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
      if (m.preconditions.isEmpty) div() else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
      if (m.postconditions.isEmpty) div() else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
      parameterListMarkup(m.parameters, m.requiredArgs),
      p(strong("Completion Type: "), m.completionType),
      resultTypeMarkup(m.resultType),
      if (m.completionConditions.isEmpty) div()
      else div(p(strong("Completion Conditions: "), ol(m.completionConditions.map(cc => li(raw(cc)))))),
      if (m.role.isEmpty) div()
      else div(p(strong("Required User Role: "), m.role.get))
    )
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(component: ComponentModel, info: List[ReceivedCommandInfo], clientApi: Boolean) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Only display non-empty tables
    if (info.isEmpty) div()
    else {
      val compName = component.component
      div(
        Styles.componentSection,
        h4(s"Command Configurations Received by $compName"),
        table(
          Styles.componentTable,
          attr("data-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              if (clientApi) th("Senders") else span
            )
          ),
          tbody(
            for (r <- info) yield {
              val rc         = r.receiveCommandModel
              val (btn, row) = hiddenRowMarkup(makeReceivedCommandDetailsRow(r.receiveCommandModel), 3)
              List(
                tr(
                  td(
                    Styles.attributeCell,
                    p(
                      btn,
                      a(name := idFor(compName, "receives", "Commands", component.subsystem, compName, rc.name))(rc.name)
                    )
                  ),
                  // XXX TODO: Make link to command description page with details
                  td(raw(rc.description)),
                  if (clientApi) td(p(r.senders.distinct.map(makeLinkForComponent))) else span
                ),
                row
              )
            }
          )
        )
      )
    }
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(component: ComponentModel, info: List[SentCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val compName = component.component

    // Warn if no receiver found for sent command
    def getWarning(m: SentCommandInfo) =
      m.warning.map { msg =>
        div(cls := "alert alert-warning", role := "alert")(
          span(cls := "glyphicon glyphicon-warning-sign", attr("aria-hidden") := "true"),
          span(em(s" Warning: $msg"))
        )
      }

    // Returns the layout for an item describing a sent command
    def makeItem(s: SentCommandInfo) = {
      s.receiveCommandModel match {
        case Some(r) =>
          val (btn, row) = hiddenRowMarkup(makeReceivedCommandDetailsRow(r), 3)
          List(
            tr(
              td(
                Styles.attributeCell,
                p(btn, a(name := idFor(compName, "sends", "Commands", s.subsystem, s.component, s.name))(s.name))
              ),
              // XXX TODO: Make link to command description page with details
              td(raw(r.description)),
              td(p(s.receiver.map(makeLinkForComponent)))
            ),
            row
          )
        case None =>
          List(
            tr(
              td(Styles.attributeCell, p(s.name)),
              td(getWarning(s)),
              td(p(s.receiver.map(makeLinkForComponent)))
            )
          )
      }
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else
      div(
        Styles.componentSection,
        h4(s"Command Configurations Sent by $compName"),
        table(
          Styles.componentTable,
          attr("data-toggle") := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Receiver")
            )
          ),
          tbody(
            for (s <- info) yield makeItem(s)
          )
        )
      )
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(component: ComponentModel, maybeCommands: Option[Commands], clientApi: Boolean) = {
    import scalatags.JsDom.all._
    val compName = component.component
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.isEmpty && commands.commandsSent.isEmpty) div()
        else
          div(
            h3(s"Commands for $compName"),
            raw(commands.description),
            receivedCommandsMarkup(component, commands.commandsReceived, clientApi),
            if (clientApi) sentCommandsMarkup(component, commands.commandsSent) else span
          )
    }
  }

  private def servicesProvidedTitle(compName: String): String = s"HTTP Services provided by $compName"
  private def servicesRequiredTitle(compName: String): String = s"HTTP Services required by $compName"

  // Generates the markup for the services section (description plus provides and requires)
  private def servicesMarkup(
      component: ComponentModel,
      maybeServices: Option[Services],
      forApi: Boolean,
      clientApi: Boolean
  ) = {
    import scalatags.JsDom.all._
    maybeServices match {
      case None => div()
      case Some(services) =>
        if (services.servicesProvided.nonEmpty || (services.servicesRequired.nonEmpty && forApi && clientApi)) {
          div(
            h3(s"Services for ${component.component}"),
            raw(services.description),
            servicesProvidedMarkup(component, services.servicesProvided, clientApi),
            if (forApi && clientApi) servicesRequiredMarkup(component, services.servicesRequired) else div()
          )
        }
        else div()
    }
  }

  // Generates the HTML markup to display the HTTP services a component requires
  private def servicesRequiredMarkup(
      component: ComponentModel,
      info: List[ServicesRequiredInfo]
  ) = {
    import scalatags.JsDom.all._

    val compName = component.component
    if (info.isEmpty) div()
    else {
      div(
        h4(servicesRequiredTitle(compName)),
        for (s <- info) yield {
          val m = s.serviceModelClient
          val providerInfo = {
            val provider = s"${s.serviceModelClient.subsystem}.${s.serviceModelClient.component}"
            span(strong(s"Provider: "), provider)
          }
          val openInNewTab = () => {
            val newTab = dom.window.open("url", "XXX")
            newTab.document.write(s.maybeHtml.get)
          }
          div(cls := "nopagebreak")(
            h5(s"HTTP Service: ${m.name}"),
            p(providerInfo),
            if (s.maybeHtml.nonEmpty)
              div(
                a(onclick := openInNewTab, title := s"Open ${m.name} API in new tab.")(s"Open ${m.name} API in new tab.")
              )
            else div()
          )
        }
      )
    }
  }

  // Generates the HTML markup to display the HTTP services a component provides
  private def servicesProvidedMarkup(
      component: ComponentModel,
      info: List[ServiceProvidedInfo],
      clientApi: Boolean
  ) = {
    import scalatags.JsDom.all._

    val compName = component.component
    if (info.isEmpty) div()
    else {
      div(
        h4(servicesProvidedTitle(compName)),
        for (s <- info) yield {
          val m = s.serviceModelProvider
          val consumerInfo = if (clientApi) {
            val consumers = s.requiredBy.distinct.map(s => s"${s.subsystem}.${s.component}").mkString(", ")
            span(strong(s"Consumers: "), if (consumers.isEmpty) "none" else consumers)
          }
          else span
          val openInNewTab = () => {
            val newTab = dom.window.open("url", "XXX")
            newTab.document.write(s.html)
          }
          div(cls := "nopagebreak")(
            h5(s"HTTP Service: ${m.name}"),
            if (clientApi) p(consumerInfo) else div(),
            div(
              a(onclick := openInNewTab, title := s"Open ${m.name} API in new tab.")(s"Open ${m.name} API in new tab.")
            )
          )
        }
      )
    }
  }

  // Generates a one line table with basic component information
  private def componentInfoTableMarkup(info: ComponentInfo) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(
      table(
        Styles.componentTable,
        attr("data-toggle") := "table",
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
  private def markupForComponent(info: ComponentInfo, forApi: Boolean, clientApi: Boolean): TypedTag[Div] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val idStr = getComponentInfoId(info.componentModel.component)

    div(Styles.component, id := idStr)(
      h2(info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel, info.publishes, forApi, clientApi),
      if (clientApi) subscribeMarkup(info.componentModel, info.subscribes) else span,
      commandsMarkup(info.componentModel, info.commands, clientApi),
      servicesMarkup(info.componentModel, info.services, forApi, clientApi)
    )
  }

}
