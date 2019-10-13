package icd.web.client

import java.util.UUID

import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{HTMLButtonElement, HTMLDivElement, HTMLElement, HTMLTableRowElement}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Components._
import org.scalajs.dom.html.Div
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
//noinspection DuplicatedCode
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components._
  import icd.web.shared.JsonSupport._

  /**
   * Gets information about the given components
   *
   * @param sv            the subsystem
   * @param maybeTargetSv optional target subsystem and version
   * @param searchAllSubsystems if true search all TMT subsystems for API dependencies
   * @return future list of objects describing the components
   */
  private def getComponentInfo(
      sv: SubsystemWithVersion,
      maybeTargetSv: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean
  ): Future[List[ComponentInfo]] = {
    Ajax.get(Routes.icdComponentInfo(sv, maybeTargetSv, searchAllSubsystems)).map { r =>
      val list = Json.fromJson[Array[ComponentInfo]](Json.parse(r.responseText)).map(_.toList).getOrElse(Nil)
      if (maybeTargetSv.isDefined) list.map(ComponentInfo.applyIcdFilter) else list
    }
  }

  /**
   * Gets the list of components for the given subsystem and then gets the information for them
   *
   * @param maybeSubsystem       optional subsystem
   * @param maybeTargetSubsystem optional target subsystem
   * @return future list of component info
   */
  private def getComponentInfo(
      maybeSubsystem: Option[SubsystemWithVersion],
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      searchAllSubsystems: Boolean
  ): Future[List[ComponentInfo]] = {
    maybeSubsystem match {
      case None =>
        Future.successful(Nil)
      case Some(sv) =>
        getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems)
    }
  }

  // Gets top level subsystem info from the server
  private def getSubsystemInfo(sv: SubsystemWithVersion): Future[SubsystemInfo] = {
    val path = Routes.subsystemInfo(sv.subsystem, sv.maybeVersion)
    Ajax.get(path).map { r =>
      val subsystemInfo = Json.fromJson[SubsystemInfo](Json.parse(r.responseText)).get
      subsystemInfo.copy(sv = sv) // include the component, if specified
    }
  }

  /**
   * Adds (appends) components to the display.
   *
   * @param sv                   the selected subsystem, version and optional single component
   * @param maybeTargetSubsystem optional target subsystem, version, optional component
   * @param maybeIcd             optional icd version
   */
  def addComponents(
      sv: SubsystemWithVersion,
      maybeTargetSubsystem: Option[SubsystemWithVersion],
      maybeIcd: Option[IcdVersion],
      searchAllSubsystems: Boolean
  ): Future[Unit] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val isIcd           = maybeTargetSubsystem.isDefined
    val subsystemInfoF  = getSubsystemInfo(sv)
    val targetInfoF     = maybeTargetSubsystem.map(getSubsystemInfo(_).map(i => Some(i))).getOrElse(Future.successful(None))
    val infoListF       = getComponentInfo(sv, maybeTargetSubsystem, searchAllSubsystems)
    val targetInfoListF = getComponentInfo(maybeTargetSubsystem, Some(sv), searchAllSubsystems)
    val f = for {
      subsystemInfo            <- subsystemInfoF
      maybeTargetSubsystemInfo <- targetInfoF
      infoList                 <- infoListF
      targetInfoList           <- targetInfoListF
    } yield {
      val titleInfo        = TitleInfo(subsystemInfo, maybeTargetSubsystem, maybeIcd)
      val subsystemVersion = sv.maybeVersion.getOrElse(TitleInfo.unpublished)
      mainContent.clearContent()
      mainContent.setTitle(titleInfo.title, titleInfo.maybeSubtitle, titleInfo.maybeDescription)
      // For ICDs, add the descriptions of the two subsystems at top
      if (isIcd) {
        val targetSubsystemInfo    = maybeTargetSubsystemInfo.get
        val targetSubsystemVersion = maybeTargetSubsystem.map(_.maybeVersion).getOrElse(TitleInfo.unpublished)
        mainContent.appendElement(
          div(
            Styles.component,
            p(strong(s"${subsystemInfo.sv.subsystem}: ${subsystemInfo.title} $subsystemVersion")),
            raw(subsystemInfo.description),
            p(strong(s"${targetSubsystemInfo.sv.subsystem}: ${targetSubsystemInfo.title} $targetSubsystemVersion")),
            raw(targetSubsystemInfo.description)
          ).render
        )
      }
      val summaryTable = SummaryTable.displaySummary(subsystemInfo, maybeTargetSubsystem, infoList).render
      mainContent.appendElement(div(Styles.component, id := "Summary")(raw(summaryTable)).render)
      infoList.foreach(i => displayComponentInfo(i, !isIcd))
      if (isIcd) targetInfoList.foreach(i => displayComponentInfo(i, forApi = false))
    }
    f.onComplete {
      case Failure(ex) => mainContent.displayInternalError(ex)
      case _           =>
    }
    f
  }

//  /**
//   * Adds (appends) a component to the display
//   *
//   * @param sv              the selected subsystem
//   * @param maybeTargetSv optional target subsystem
//   */
//  def addComponent(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Unit = {
//    getComponentInfo(sv, maybeTargetSv)
//      .map { list =>
//        list.foreach(i => displayComponentInfo(i, maybeTargetSv.isEmpty))
//      }
//      .recover {
//        case ex => mainContent.displayInternalError(ex)
//      }
//  }

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
  private def displayComponentInfo(info: ComponentInfo, forApi: Boolean): Unit = {
    if (forApi || (info.publishes.isDefined && info.publishes.get.nonEmpty
        || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
        || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty
        || info.commands.get.commandsSent.nonEmpty))) {
      val markup     = markupForComponent(info, forApi).render
      val oldElement = $id(getComponentInfoId(info.componentModel.component))
      if (oldElement == null) {
        mainContent.appendElement(markup)
      } else {
        // Use existing div, so the component's position stays the same
        mainContent.replaceElement(oldElement, markup)
      }
    }
  }

  // HTML id for a table displaying the fields of a struct
  private def structIdStr(name: String): String = s"$name-struct"

  // Add a table for each attribute of type "struct" to show the members of the struct
  private def structAttributesMarkup(headings: List[String], attributesList: List[AttributeModel]): Seq[TypedTag[Div]] = {
    import scalatags.JsDom.all._
    attributesList.flatMap { attrModel =>
      if (attrModel.typeStr == "struct" || attrModel.typeStr == "array of struct") {
        val rowList2 =
          for (a2 <- attrModel.attributesList) yield List(a2.name, a2.description, a2.typeStr, a2.units, a2.defaultValue)
        Some(
          div()(
            p(strong(a(name := structIdStr(attrModel.name))(s"Attributes for ${attrModel.name} struct"))),
            mkTable(headings, rowList2)
          )
        )
      } else None
    }
  }

  /**
   * Returns a table of attributes
   *
   * @param titleStr       title to display above the table
   * @param attributesList list of attributes to display
   * @return
   */
  private def attributeListMarkup(titleStr: String, attributesList: List[AttributeModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList =
        for (a <- attributesList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units, a.defaultValue)
      div(
        strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        structAttributesMarkup(headings, attributesList)
      )
    }
  }

  /**
   * Returns a table of parameters
   *
   * @param titleStr       title to display above the table
   * @param attributesList list of attributes to display
   * @param requiredArgs   a list of required arguments
   */
  private def parameterListMarkup(
      titleStr: String,
      attributesList: List[AttributeModel],
      requiredArgs: List[String]
  ): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
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
      div(
        strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        structAttributesMarkup(headings, attributesList)
      )
    }
  }

  // XXX TODO FIXME: Deal with arbitrary nested array/struct types?

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
   * @param attributesList list of attributes to display
   */
  private def resultTypeMarkup(attributesList: List[AttributeModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units")
      val rowList  = for (a <- attributesList) yield List(a.name, a.description, getTypeStr(a.name, a.typeStr), a.units)
      div(
        strong("Result Type Fields"),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable),
        structAttributesMarkup(headings, attributesList)
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

  private def formatRate(rate: Double): String = if (rate == 0.0) "" else s"$rate Hz"

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, maybePublishes: Option[Publishes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(info.componentModel.subsystem, info.componentModel.component))
    }

    // Makes the link for a subscriber component in the table
    def makeLinkForSubscriber(info: SubscribeInfo) = {
      a(
        title := s"Show API for ${info.componentModel.subsystem}.${info.componentModel.component}",
        s"${info.componentModel.subsystem}.${info.componentModel.component} ",
        href := "#",
        onclick := clickedOnSubscriber(info) _
      )
    }

    // Returns a table row displaying more details for the given event
    def makeEventDetailsRow(eventInfo: EventInfo) = {
      val headings = List("Min Rate", "Max Rate", "Archive", "Archive Duration", "Archive Rate")
      val rowList = List(
        List(
          formatRate(eventInfo.eventModel.minRate),
          formatRate(eventInfo.eventModel.maxRate),
          yesNo(eventInfo.eventModel.archive),
          eventInfo.eventModel.archiveDuration,
          formatRate(eventInfo.eventModel.archiveRate)
        )
      )

      div(
        if (eventInfo.eventModel.requirements.isEmpty) div()
        else p(strong("Requirements: "), eventInfo.eventModel.requirements.mkString(", ")),
        mkTable(headings, rowList),
        attributeListMarkup("Attributes", eventInfo.eventModel.attributesList)
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
                th("Subscribers")
              )
            ),
            tbody(
              for (t <- eventList) yield {
                val (btn, row) = hiddenRowMarkup(makeEventDetailsRow(t), 3)
                List(
                  tr(
                    td(
                      Styles.attributeCell,
                      p(btn, a(name := idFor(compName, "publishes", pubType, t.eventModel.name))(t.eventModel.name))
                    ),
                    td(raw(t.eventModel.description)),
                    td(p(t.subscribers.map(makeLinkForSubscriber)))
                  ),
                  row
                )
              }
            )
          )
        )
    }

    // Returns a table row displaying more details for the given alarm
    def makeAlarmDetailsRow(t: AlarmInfo) = {
      val headings = List("Severity Levels", "Archive", "Location", "Alarm Type", "Acknowledge", "Latched")
      val m        = t.alarmModel
      val rowList = List(
        List(m.severityLevels.mkString(", "), yesNo(m.archive), m.location, m.alarmType, yesNo(m.acknowledge), yesNo(m.latched))
      )

      div(
        if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
        p(strong("Probable Cause: "), raw(m.probableCause)),
        p(strong("Operator Response: "), raw(m.operatorResponse)),
        mkTable(headings, rowList)
      )
    }

    // Returns the markup for the published alarms
    def publishAlarmListMarkup(alarmList: List[AlarmInfo]) = {
      if (alarmList.isEmpty) div()
      else
        div(
          h3(s"Alarms Published by $compName"),
          table(
            attr("data-toggle") := "table",
            thead(
              tr(
                th("Name"),
                th("Description"),
                th("Subscribers")
              )
            ),
            tbody(
              for (t <- alarmList) yield {
                val m          = t.alarmModel
                val (btn, row) = hiddenRowMarkup(makeAlarmDetailsRow(t), 3)
                List(
                  tr(
                    td(Styles.attributeCell, p(btn, a(name := idFor(compName, "publishes", "Alarms", m.name))(m.name))),
                    td(raw(m.description)),
                    td(p(t.subscribers.map(makeLinkForSubscriber)))
                  ),
                  row
                )
              }
            )
          )
        )
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
            publishEventListMarkup("Current States", publishes.currentStateList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        } else div()
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, maybeSubscribes: Option[Subscribes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnPublisher(info: DetailedSubscribeInfo)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(
        ComponentLink(
          info.subscribeModelInfo.subsystem,
          info.subscribeModelInfo.component
        )
      )
    }

    // Makes the link for a publisher component in the table
    def makeLinkForPublisher(info: DetailedSubscribeInfo) = {
      val comp = s"${info.subscribeModelInfo.subsystem}.${info.subscribeModelInfo.component}"
      a(
        title := s"Show API for $comp",
        comp,
        href := "#",
        onclick := clickedOnPublisher(info) _
      )
    }

    // Returns a table row displaying more details for the given subscription
    def makeDetailsRow(si: DetailedSubscribeInfo) = {
      val sInfo = si.subscribeModelInfo
      val headings =
        List("Subsystem", "Component", "Prefix.Name", "Required Rate", "Max Rate", "Publisher's Min Rate", "Publisher's Max Rate")
      val rowList = List(
        List(
          sInfo.subsystem,
          sInfo.component,
          si.path,
          formatRate(sInfo.requiredRate),
          formatRate(sInfo.maxRate),
          formatRate(si.eventModel.map(_.minRate).getOrElse(0.0)),
          formatRate(si.eventModel.map(_.maxRate).getOrElse(0.0))
        )
      )

      val attrTable = si.eventModel.map(t => attributeListMarkup("Attributes", t.attributesList)).getOrElse(div())
      div(mkTable(headings, rowList), attrTable)
    }

    def subscribeListMarkup(pubType: String, subscribeList: List[DetailedSubscribeInfo]) = {
      // Warn if no publisher found for subscibed item
      def getWarning(info: DetailedSubscribeInfo) = info.warning.map { msg =>
        div(cls := "alert alert-warning", role := "alert")(
          span(cls := "glyphicon glyphicon-warning-sign", attr("aria-hidden") := "true"),
          span(em(s" Warning: $msg"))
        )
      }

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
                          a(name := idFor(compName, "subscribes", pubType, s.subscribeModelInfo.name))(s.subscribeModelInfo.name)
                        )
                      ),
                      td(raw(s.description), getWarning(s), usage),
                      td(p(makeLinkForPublisher(s)))
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
        } else div()
    }
  }

  // Returns a table row displaying more details for the given command
  private def makeReceivedCommandDetailsRow(m: ReceiveCommandModel) = {
    import scalatags.JsDom.all._
    div(
      if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
      if (m.preconditions.isEmpty) div() else div(p(strong("Preconditions: "), ol(m.preconditions.map(pc => li(raw(pc)))))),
      if (m.postconditions.isEmpty) div() else div(p(strong("Postconditions: "), ol(m.postconditions.map(pc => li(raw(pc)))))),
      parameterListMarkup("Arguments", m.args, m.requiredArgs),
      p(strong("Completion Type: "), m.completionType),
      resultTypeMarkup(m.resultType),
      if (m.completionConditions.isEmpty) div()
      else div(p(strong("Completion Conditions: "), ol(m.completionConditions.map(cc => li(raw(cc))))))
    )
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a sender link
    def clickedOnSender(sender: ComponentModel)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(sender.subsystem, sender.component))
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: ComponentModel) = {
      a(s"${sender.subsystem}.${sender.component} ", href := "#", onclick := clickedOnSender(sender) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else
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
              th("Senders")
            )
          ),
          tbody(
            for (r <- info) yield {
              val rc         = r.receiveCommandModel
              val (btn, row) = hiddenRowMarkup(makeReceivedCommandDetailsRow(r.receiveCommandModel), 3)
              List(
                tr(
                  td(Styles.attributeCell, p(btn, a(name := idFor(compName, "receives", "Commands", rc.name))(rc.name))),
                  // XXX TODO: Make link to command description page with details
                  td(raw(rc.description)),
                  td(p(r.senders.map(makeLinkForSender)))
                ),
                row
              )
            }
          )
        )
      )
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[SentCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a receiver link
    def clickedOnReceiver(receiver: ComponentModel)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(receiver.subsystem, receiver.component))
    }

    // Makes the link for a receiver component in the table
    def makeLinkForReceiver(receiver: ComponentModel) = {
      a(s"${receiver.subsystem}.${receiver.component} ", href := "#", onclick := clickedOnReceiver(receiver) _)
    }

    // Warn if no receiver found for sent command
    def getWarning(m: SentCommandInfo) = m.warning.map { msg =>
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
              td(Styles.attributeCell, p(btn, a(name := idFor(compName, "sends", "Commands", r.name))(r.name))),
              // XXX TODO: Make link to command description page with details
              td(raw(r.description)),
              td(p(s.receiver.map(makeLinkForReceiver)))
            ),
            row
          )
        case None =>
          List(
            tr(
              td(Styles.attributeCell, p(s.name)),
              td(getWarning(s)),
              td(p(s.receiver.map(makeLinkForReceiver)))
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
  private def commandsMarkup(compName: String, maybeCommands: Option[Commands], forApi: Boolean) = {
    import scalatags.JsDom.all._
    maybeCommands match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.isEmpty && commands.commandsSent.isEmpty) div()
        else
          div(
            h3(s"Commands for $compName"),
            raw(commands.description),
            receivedCommandsMarkup(compName, commands.commandsReceived),
            sentCommandsMarkup(compName, commands.commandsSent)
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
  private def markupForComponent(info: ComponentInfo, forApi: Boolean): TypedTag[Div] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val idStr = getComponentInfoId(info.componentModel.component)

    div(Styles.component, id := idStr)(
      h2(info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel.component, info.publishes),
      subscribeMarkup(info.componentModel.component, info.subscribes),
      commandsMarkup(info.componentModel.component, info.commands, forApi)
    )
  }

}
