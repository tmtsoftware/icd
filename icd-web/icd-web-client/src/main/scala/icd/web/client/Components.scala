package icd.web.client

import java.util.UUID

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{ HTMLTableRowElement, HTMLButtonElement, HTMLDivElement }
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Components._

import scalatags.JsDom.TypedTag

object Components {
  // Id of component info for given component name
  def getComponentInfoId(compName: String) = s"$compName-info"

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

  /**
   * Returns a HTML table with the given column headings and list of rows
   *
   * @param headings the table headings
   * @param rowList list of row data
   * @param tableStyle optional table style
   * @return an html table element
   */
  private def mkTable(headings: List[String], rowList: List[List[String]],
                      tableStyle: scalacss.StyleA = Styles.emptyStyle) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Returns a table cell markup, checking if the text is already in html format (after markdown processing)
    def mkTableCell(text: String) = {
      if (text.startsWith("<p>"))
        td(raw(text))
      else
        td(p(text))
    }

    if (rowList.isEmpty) div()
    else {
      val (newHead, newRows) = SharedUtils.compact(headings, rowList)
      if (newHead.isEmpty) div()
      else {
        table(tableStyle, "data-toggle".attr := "table",
          thead(
            tr(newHead.map(th(_)))),
          tbody(
            for (row ← newRows) yield {
              tr(row.map(mkTableCell))
            }))
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
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components._

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
                               targetSubsystem: SubsystemWithVersion): Future[List[ComponentInfo]] = {
    Ajax.get(Routes.icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem)).map { r ⇒
      val list = read[List[ComponentInfo]](r.responseText)
      if (targetSubsystem.subsystemOpt.isDefined) list.map(ComponentInfo.applyIcdFilter) else list
    }
  }

  // Gets top level subsystem info from the server
  private def getSubsystemInfo(subsystem: String, versionOpt: Option[String]): Future[SubsystemInfo] = {
    val path = Routes.subsystemInfo(subsystem, versionOpt)
    Ajax.get(path).map { r ⇒
      read[SubsystemInfo](r.responseText)
    }
  }

  /**
   * Adds (appends) a list of components to the display, in the order that they are given in the list.
   *
   * @param compNames       the names of the components
   * @param sv              the selected subsystem and version
   * @param targetSubsystem the target subsystem (might not be set)
   */
  def addComponents(compNames: List[String], sv: SubsystemWithVersion, targetSubsystem: SubsystemWithVersion,
                    icdOpt: Option[IcdVersion]): Future[Unit] = {
    sv.subsystemOpt match {
      case None ⇒ Future.successful()
      case Some(subsystem) ⇒
        val f = for {
          subsystemInfo ← getSubsystemInfo(subsystem, sv.versionOpt)
          infoList ← getComponentInfo(subsystem, sv.versionOpt, compNames, targetSubsystem)
        } yield {
          val titleInfo = TitleInfo(subsystemInfo, targetSubsystem, icdOpt)
          mainContent.clearContent()
          mainContent.setTitle(titleInfo.title, titleInfo.subtitleOpt, titleInfo.descriptionOpt)
          infoList.foreach(displayComponentInfo)
        }
        f.onFailure { case ex ⇒ mainContent.displayInternalError(ex) }
        f
    }
  }

  /**
   * Adds (appends) a component to the display
   *
   * @param compName        the name of the component
   * @param sv              the selected subsystem
   * @param targetSubsystem the target subsystem (might not be set)
   */
  def addComponent(compName: String, sv: SubsystemWithVersion,
                   targetSubsystem: SubsystemWithVersion): Unit = {
    sv.subsystemOpt.foreach { subsystem ⇒
      getComponentInfo(subsystem, sv.versionOpt, List(compName), targetSubsystem).map { list ⇒
        list.foreach(displayComponentInfo)
      }.recover {
        case ex ⇒ mainContent.displayInternalError(ex)
      }
    }
  }

  /**
   * Displays only the given component's information, ignoring any filter
   *
   * @param sv       the subsystem and version to use for the component
   * @param compName the name of the component
   */
  def setComponent(sv: SubsystemWithVersion, compName: String): Unit = {
    if (sv.subsystemOpt.isDefined) {
      val path = Routes.componentInfo(sv.subsystemOpt.get, sv.versionOpt, List(compName))
      Ajax.get(path).map { r ⇒
        val infoList = read[List[ComponentInfo]](r.responseText)
        mainContent.clearContent()
        mainContent.scrollToTop()
        mainContent.setTitle(s"Component: $compName")
        displayComponentInfo(infoList.head)
      }.recover {
        case ex ⇒
          mainContent.displayInternalError(ex)
      }
    }
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
  private def displayComponentInfo(info: ComponentInfo): Unit = {
    if (info.publishes.isDefined && info.publishes.get.nonEmpty
      || info.subscribes.isDefined && info.subscribes.get.subscribeInfo.nonEmpty
      || info.commands.isDefined && (info.commands.get.commandsReceived.nonEmpty || info.commands.get.commandsSent.nonEmpty)) {
      val markup = markupForComponent(info).render
      val oldElement = $id(getComponentInfoId(info.compName))
      if (oldElement == null) {
        mainContent.appendElement(markup)
      } else {
        // Use existing div, so the component's position stays the same
        mainContent.replaceElement(oldElement, markup)
      }
    }
  }

  /**
   * Returns a table of attributes
   *
   * @param titleStr       title to display above the table
   * @param attributesList list of attributes to display
   * @return
   */
  private def attributeListMarkup(titleStr: String, attributesList: List[AttributeInfo]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a ← attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue)
      div(strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable))
    }
  }

  /**
   * Returns a table of parameters
   *
   * @param titleStr       title to display above the table
   * @param attributesList list of attributes to display
   * @param requiredArgs   a list of required arguments
   * @return
   */
  private def parameterListMarkup(titleStr: String, attributesList: List[AttributeInfo], requiredArgs: List[String]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a ← attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue,
        if (requiredArgs.contains(a.name)) "yes" else "no")
      div(strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable))
    }
  }

  /**
   * Returns a hidden, expandable table row containing the given div item
   *
   * @param item    the contents of the table row
   * @param colSpan the number of columns to span
   * @return a pair of (button, tr) elements, where the button toggles the visibility of the row
   */
  private def hiddenRowMarkup(item: TypedTag[HTMLDivElement], colSpan: Int): (TypedTag[HTMLButtonElement], TypedTag[HTMLTableRowElement]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    // button to toggle visibility
    val idStr = UUID.randomUUID().toString
    val btn = button(Styles.attributeBtn,
      "data-toggle".attr := "collapse",
      "data-target".attr := s"#$idStr",
      title := "Show/hide details")(
        span(cls := "glyphicon glyphicon-collapse-down"))
    val row = tr(id := idStr, cls := "collapse")(td(colspan := colSpan)(item))
    (btn, row)
  }

  private def formatRate(rate: Double): String = if (rate == 0) "" else s"$rate Hz"

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, publishesOpt: Option[Publishes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event) = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(info.subsystem, info.compName))
    }

    // Makes the link for a subscriber component in the table
    def makeLinkForSubscriber(info: SubscribeInfo) = {
      a(title := s"Show API for ${info.compName}",
        s"${info.compName} ",
        href := "#",
        onclick := clickedOnSubscriber(info) _)
    }

    // Returns a table row displaying more details for the given telemetry
    def makeTelemetryDetailsRow(t: TelemetryInfo) = {
      val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate")
      val rowList = List(List(
        formatRate(t.minRate),
        formatRate(t.maxRate),
        if (t.archive) "Yes" else "No",
        formatRate(t.archiveRate)))

      div(
        if (t.requirements.isEmpty) div() else p(strong("Requirements: "), t.requirements.mkString(", ")),
        mkTable(headings, rowList),
        attributeListMarkup("Attributes", t.attributesList))
    }

    // Returns the markup for the published telemetry
    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]) = {
      if (telemetryList.isEmpty) div()
      else div(
        h4(s"$pubType Published by $compName"),
        table("data-toggle".attr := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Subscribers"))),
          tbody(
            for (t ← telemetryList) yield {
              val (btn, row) = hiddenRowMarkup(makeTelemetryDetailsRow(t), 3)
              List(tr(
                td(Styles.attributeCell, p(btn, t.name)),
                td(raw(t.description)),
                td(p(t.subscribers.map(makeLinkForSubscriber)))),
                row)
            })))
    }

    // Returns a table row displaying more details for the given alarm
    def makeAlarmDetailsRow(t: AlarmInfo) = {
      val headings = List("Severity", "Archive")
      val rowList = List(List(t.severity, if (t.archive) "Yes" else "No"))

      div(
        if (t.requirements.isEmpty) div() else p(strong("Requirements: "), t.requirements.mkString(", ")),
        mkTable(headings, rowList))
    }

    // Returns the markup for the published alarms
    def publishAlarmListMarkup(alarmList: List[AlarmInfo]) = {
      if (alarmList.isEmpty) div()
      else div(
        h4(s"Alarms Published by $compName"),
        table("data-toggle".attr := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Subscribers"))),
          tbody(
            for (t ← alarmList) yield {
              val (btn, row) = hiddenRowMarkup(makeAlarmDetailsRow(t), 3)
              List(tr(
                td(Styles.attributeCell, p(btn, t.name)),
                td(raw(t.description)),
                td(p(t.subscribers.map(makeLinkForSubscriber)))),
                row)
            })))
    }

    publishesOpt match {
      case None ⇒ div()
      case Some(publishes) ⇒
        if (publishes.nonEmpty) {
          div(Styles.componentSection,
            h3(s"Items published by $compName"),
            raw(publishes.description),
            publishTelemetryListMarkup("Telemetry", publishes.telemetryList),
            publishTelemetryListMarkup("Events", publishes.eventList),
            publishTelemetryListMarkup("Event Streams", publishes.eventStreamList),
            publishAlarmListMarkup(publishes.alarmList))
        } else div()
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnPublisher(info: SubscribeInfo)(e: dom.Event) = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(info.subsystem, info.compName))
    }

    // Makes the link for a publisher component in the table
    def makeLinkForPublisher(info: SubscribeInfo) = {
      a(title := s"Show API for ${info.compName}",
        s"${info.compName} ",
        href := "#",
        onclick := clickedOnPublisher(info) _)
    }

    // Returns a table row displaying more details for the given subscription
    def makeDetailsRow(si: SubscribeInfo) = {
      val headings = List("Subsystem", "Component", "Prefix.Name", "Required Rate", "Max Rate")
      val rowList = List(List(
        si.subsystem,
        si.compName,
        si.path,
        formatRate(si.requiredRate),
        formatRate(si.maxRate)))

      div(mkTable(headings, rowList))
    }

    def subscribeListMarkup(pubType: String, subscribeList: List[SubscribeInfo]) = {
      if (subscribeList.isEmpty) div()
      else div(
        h4(s"$pubType Subscribed to by $compName"),
        div(Styles.componentSection,
          table(Styles.componentTable, "data-toggle".attr := "table",
            thead(
              tr(
                th("Name"),
                th("Description"),
                th("Publisher"))),
            tbody(
              for (s ← subscribeList) yield {
                val (btn, row) = hiddenRowMarkup(makeDetailsRow(s), 3)
                val usage = if (s.usage.isEmpty) div() else div(strong("Usage:"), raw(s.usage))
                List(tr(
                  td(Styles.attributeCell, p(btn, s.name)),
                  td(raw(s.description), usage),
                  td(p(makeLinkForPublisher(s)))),
                  row)
              }))))
    }

    subscribesOpt match {
      case None ⇒ div()
      case Some(subscribes) ⇒
        if (subscribes.subscribeInfo.nonEmpty) {
          div(Styles.componentSection,
            h3(s"Items subscribed to by $compName"),
            raw(subscribes.description),
            subscribeListMarkup("Telemetry", subscribes.subscribeInfo.filter(_.itemType == "Telemetry")),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == "Events")),
            subscribeListMarkup("Event Streams", subscribes.subscribeInfo.filter(_.itemType == "EventStreams")),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == "Alarms")))
        } else div()
    }
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a sender link
    def clickedOnSender(sender: OtherComponent)(e: dom.Event) = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(sender.subsystem, sender.compName))
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: OtherComponent) = {
      a(s"${sender.compName} ", href := "#", onclick := clickedOnSender(sender) _)
    }

    // Returns a table row displaying more details for the given command
    def makeDetailsRow(r: ReceivedCommandInfo) = {
      div(
        if (r.requirements.isEmpty) div() else p(strong("Requirements: "), r.requirements.mkString(", ")),
        parameterListMarkup("Arguments", r.args, r.requiredArgs))
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(Styles.componentSection,
      h4(s"Command Configurations Received by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Senders"))),
        tbody(
          for (r ← info) yield {
            val (btn, row) = hiddenRowMarkup(makeDetailsRow(r), 3)
            List(
              tr(
                td(Styles.attributeCell, p(btn, r.name)), // XXX TODO: Make link to command description page with details
                td(raw(r.description)),
                td(p(r.senders.map(makeLinkForSender)))),
              row)
          })))
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[SentCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a receiver link
    def clickedOnReceiver(receiver: OtherComponent)(e: dom.Event) = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(receiver.subsystem, receiver.compName))
    }

    // Makes the link for a receiver component in the table
    def makeLinkForReceiver(receiver: OtherComponent) = {
      a(s"${receiver.compName} ", href := "#", onclick := clickedOnReceiver(receiver) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(Styles.componentSection,
      h4(s"Command Configurations Sent by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Receiver"))),
        tbody(
          for (s ← info) yield {
            tr(
              td(p(s.name)), // XXX TODO: Make link to command description page with details
              td(raw(s.description)),
              td(p(s.receivers.map(makeLinkForReceiver))))
          })))
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands]) = {
    import scalatags.JsDom.all._
    commandsOpt match {
      case None ⇒ div()
      case Some(commands) ⇒
        if (commands.commandsReceived.isEmpty && commands.commandsSent.isEmpty) div()
        else div(
          h3(s"Commands for $compName"),
          raw(commands.description),
          receivedCommandsMarkup(compName, commands.commandsReceived),
          sentCommandsMarkup(compName, commands.commandsSent))
    }
  }

  // Generates a one line table with basic component information
  private def componentInfoTableMarkup(info: ComponentInfo) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Subsystem"),
            th("Name"),
            th("Prefix"),
            th("Type"),
            th("WBS ID"))),
        tbody(
          tr(
            td(info.subsystem),
            td(info.compName),
            td(info.prefix),
            td(info.componentType),
            td(info.wbsId)))))
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val idStr = getComponentInfoId(info.compName)

    div(Styles.component, id := idStr)(
      h2(info.compName),
      componentInfoTableMarkup(info),
      raw(info.description),
      publishMarkup(info.compName, info.publishes),
      subscribeMarkup(info.compName, info.subscribes),
      commandsMarkup(info.compName, info.commands))
  }

}
