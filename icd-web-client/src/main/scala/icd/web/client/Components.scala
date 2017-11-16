package icd.web.client

import java.util.UUID

import icd.web.shared.ComponentInfo.{apply => _, unapply => _, _}
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

object Components {
  import icd.web.shared.JsonSupport._

  // Id of component info for given component name
//  def getComponentInfoId(compName: String): String = s"$compName-info"
  def getComponentInfoId(compName: String): String = compName

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
    * @param headings   the table headings
    * @param rowList    list of row data
    * @param tableStyle optional table style
    * @return an html table element
    */
  def mkTable(headings: List[String], rowList: List[List[String]],
              tableStyle: scalacss.StyleA = Styles.emptyStyle): TypedTag[HTMLElement] = {
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
        table(tableStyle, attr("data-toggle") := "table",
          thead(
            tr(newHead.map(th(_)))
          ),
          tbody(
            for (row <- newRows) yield {
              tr(row.map(mkTableCell))
            }
          ))
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
  import icd.web.shared.JsonSupport._

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
    Ajax.get(Routes.icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem)).map { r =>
      val list = Json.fromJson[List[ComponentInfo]](Json.parse(r.responseText)).getOrElse(Nil)
      if (targetSubsystem.subsystemOpt.isDefined) list.map(ComponentInfo.applyIcdFilter) else list
    }
  }

  // Gets top level subsystem info from the server
  private def getSubsystemInfo(subsystem: String, versionOpt: Option[String]): Future[SubsystemInfo] = {
    val path = Routes.subsystemInfo(subsystem, versionOpt)
    Ajax.get(path).map { r =>
      Json.fromJson[SubsystemInfo](Json.parse(r.responseText)).get
    }
  }

  /**
    * Displays a summary of the events published and commands received by the subsystem.
    * Note: This is pretty much the same code as in IcdDbPrinter, which is used for pdfs,
    * but the scalaTags types are different here (and here we are in a JavaScript environment).
    *
    * @param subsystemInfo   subsystem to use
    * @param targetSubsystem optional target subsystem name (for ICD)
    * @param infoList        list of component info
    * @return the HTML
    */
  private def displaySummary(subsystemInfo: SubsystemInfo, targetSubsystem: Option[String], infoList: List[ComponentInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    import SummaryInfo._

    val subsystem = subsystemInfo.subsystem
    val subsystemVersion = subsystemInfo.versionOpt.getOrElse(unpublished)
    val isIcd = targetSubsystem.isDefined

    def firstParagraph(s: String): String = {
      val i = s.indexOf("</p>")
      if (i == -1) s else s.substring(0, i + 4)
    }

    // Displays a summary for published items of a given event type or commands received.
    def publishedSummaryMarkup(itemType: String, list: List[PublishedItem], heading: String, prep: String) = {
      val (action, publisher) = heading.toLowerCase() match {
        case "published by" => ("publishes", "Publisher")
        case "received by" => ("receives", "Receiver")
      }
      val targetStr = if (targetSubsystem.isDefined) s" $prep ${targetSubsystem.get}" else ""
      if (list.isEmpty) div() else {
        div(
          h3(s"$itemType $heading $subsystem$targetStr"),
          table(
            thead(
              tr(
                th(publisher),
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
                  td(p(a(href := s"#${info.component.component}")(info.component.component))),
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
    def subscribedSummaryMarkup(itemType: String, list: List[SubscribedItem], heading: String, prep: String) = {
      if (list.isEmpty) div() else {
        val (subscribes, publishes, subscriber) = heading.toLowerCase() match {
          case "subscribed to by" => ("subscribes", "publishes", "Subscriber")
          case "sent by" => ("sends", "receives", "Sender")
        }
        val targetStr = if (targetSubsystem.isDefined) s" $prep ${targetSubsystem.get}" else ""
        div(
          h3(s"$itemType $heading $subsystem$targetStr"),
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
                  if (info.publisherSubsystem == info.subscriber.subsystem)
                    a(href := s"#${info.publisherComponent}")(prefixItem)
                  else span(prefixItem)

                val description = info.warningOpt match {
                  case Some(msg) => p(em("Warning: ", msg))
                  case None => raw(firstParagraph(info.item.description))
                }

                // ICDs contain both subsystems, so we can link to them
                // XXX TODO: Link targets should contain subsystem names!?
                if (isIcd) {
                  tr(
                    td(p(a(href := s"#${info.subscriber.component}")(info.subscriber.component))),
                    td(p(a(href := s"#${info.publisherComponent}")(prefixItem))),
                    td(p(a(href := s"#${idFor(info.publisherComponent, publishes, itemType, info.item.name)}")(info.item.name))),
                    td(description)
                  )
                } else {
                  tr(
                    td(p(a(href := s"#${info.subscriber.component}")(info.subscriber.component))),
                    td(p(publisherPrefix)),
                    td(p(a(href := s"#${idFor(info.subscriber.component, subscribes, itemType, info.item.name)}")(info.item.name))),
                    td(description)
                  )
                }
              }
            )
          )
        )
      }
    }

    def publishedSummary() = {
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

      div(
        h2(s"$subsystem Summary"),

        publishedSummaryMarkup("Events", publishedEvents, "Published by", "for"),
        publishedSummaryMarkup("Event Streams", publishedEventStreams, "Published by", "for"),
        publishedSummaryMarkup("Telemetry", publishedTelemetry, "Published by", "for"),
        publishedSummaryMarkup("Alarms", publishedAlarms, "Published by", "for"),
        publishedSummaryMarkup("Commands", receivedCommands, "Received by", "from")
      )
    }

    def subscribedSummary() = {
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
        subscribedSummaryMarkup("Events", subscribedEvents, "Subscribed to by", "from"),
        subscribedSummaryMarkup("Event Streams", subscribedEventStreams, "Subscribed to by", "from"),
        subscribedSummaryMarkup("Telemetry", subscribedTelemetry, "Subscribed to by", "from"),
        subscribedSummaryMarkup("Alarms", subscribedAlarms, "Subscribed to by", "from"),
        subscribedSummaryMarkup("Commands", sentCommands, "Sent by", "to")
      )
    }

    div(Styles.component, id := "Summary")(
      publishedSummary(),
      subscribedSummary()
    )
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
      case None => Future.successful()
      case Some(subsystem) =>
        val f = for {
          subsystemInfo <- getSubsystemInfo(subsystem, sv.versionOpt)
          infoList <- getComponentInfo(subsystem, sv.versionOpt, compNames, targetSubsystem)
        } yield {
          val titleInfo = TitleInfo(subsystemInfo, targetSubsystem, icdOpt)
          mainContent.clearContent()
          mainContent.setTitle(titleInfo.title, titleInfo.subtitleOpt, titleInfo.descriptionOpt)
          mainContent.appendElement(displaySummary(subsystemInfo, targetSubsystem.subsystemOpt, infoList).render)
          infoList.foreach(displayComponentInfo)
        }
        f.onComplete {
          case Failure(ex) => mainContent.displayInternalError(ex)
          case _ =>
        }
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
    sv.subsystemOpt.foreach { subsystem =>
      getComponentInfo(subsystem, sv.versionOpt, List(compName), targetSubsystem).map { list =>
        list.foreach(displayComponentInfo)
      }.recover {
        case ex => mainContent.displayInternalError(ex)
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
      Ajax.get(path).map { r =>
        val infoList = Json.fromJson[List[ComponentInfo]](Json.parse(r.responseText)).getOrElse(Nil)
        mainContent.clearContent()
        mainContent.scrollToTop()
        mainContent.setTitle(s"Component: $compName")
        displayComponentInfo(infoList.head)
      }.recover {
        case ex =>
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
      val oldElement = $id(getComponentInfoId(info.componentModel.component))
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
  private def attributeListMarkup(titleStr: String, attributesList: List[AttributeModel]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue)
      div(
        strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable)
      )
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
  private def parameterListMarkup(titleStr: String, attributesList: List[AttributeModel], requiredArgs: List[String]): TypedTag[HTMLDivElement] = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) div()
    else {
      val headings = List("Name", "Description", "Type", "Units", "Default")
      val rowList = for (a <- attributesList) yield List(a.name, a.description, a.typeStr, a.units, a.defaultValue,
        if (requiredArgs.contains(a.name)) "yes" else "no")
      div(
        strong(titleStr),
        mkTable(headings, rowList, tableStyle = Styles.attributeTable)
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
  private def hiddenRowMarkup(item: TypedTag[HTMLDivElement], colSpan: Int): (TypedTag[HTMLButtonElement], TypedTag[HTMLTableRowElement]) = {
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
  private def publishMarkup(compName: String, publishesOpt: Option[Publishes]) = {
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

    // Returns a table row displaying more details for the given telemetry
    def makeTelemetryDetailsRow(t: TelemetryInfo) = {
      val headings = List("Min Rate", "Max Rate", "Archive", "Archive Rate")
      val rowList = List(List(
        formatRate(t.telemetryModel.minRate),
        formatRate(t.telemetryModel.maxRate),
        if (t.telemetryModel.archive) "Yes" else "No",
        formatRate(t.telemetryModel.archiveRate)
      ))

      div(
        if (t.telemetryModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.telemetryModel.requirements.mkString(", ")),
        mkTable(headings, rowList),
        attributeListMarkup("Attributes", t.telemetryModel.attributesList)
      )
    }

    // Returns the markup for the published telemetry
    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]) = {
      if (telemetryList.isEmpty) div()
      else div(
        h4(s"$pubType Published by $compName"),
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
            for (t <- telemetryList) yield {
              val (btn, row) = hiddenRowMarkup(makeTelemetryDetailsRow(t), 3)
              List(
                tr(
                  td(Styles.attributeCell, p(btn,
                    a(name := idFor(compName, "publishes", pubType, t.telemetryModel.name))(t.telemetryModel.name))),
                  td(raw(t.telemetryModel.description)),
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
      val headings = List("Severity", "Archive")
      val rowList = List(List(t.alarmModel.severity, if (t.alarmModel.archive) "Yes" else "No"))

      div(
        if (t.alarmModel.requirements.isEmpty) div() else p(strong("Requirements: "), t.alarmModel.requirements.mkString(", ")),
        mkTable(headings, rowList)
      )
    }

    // Returns the markup for the published alarms
    def publishAlarmListMarkup(alarmList: List[AlarmInfo]) = {
      if (alarmList.isEmpty) div()
      else div(
        h4(s"Alarms Published by $compName"),
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
              val (btn, row) = hiddenRowMarkup(makeAlarmDetailsRow(t), 3)
              List(
                tr(
                  td(Styles.attributeCell, p(btn,
                    a(name := idFor(compName, "publishes", "Alarms", t.alarmModel.name))(t.alarmModel.name))),
                  td(raw(t.alarmModel.description)),
                  td(p(t.subscribers.map(makeLinkForSubscriber)))
                ),
                row
              )
            }
          )
        )
      )
    }

    publishesOpt match {
      case None => div()
      case Some(publishes) =>
        if (publishes.nonEmpty) {
          div(
            Styles.componentSection,
            h3(s"Items published by $compName"),
            raw(publishes.description),
            publishTelemetryListMarkup("Telemetry", publishes.telemetryList),
            publishTelemetryListMarkup("Events", publishes.eventList),
            publishTelemetryListMarkup("Event Streams", publishes.eventStreamList),
            publishAlarmListMarkup(publishes.alarmList)
          )
        } else div()
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnPublisher(info: DetailedSubscribeInfo)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(
        info.subscribeModelInfo.subsystem,
        info.subscribeModelInfo.component
      ))
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
      val headings = List("Subsystem", "Component", "Prefix.Name", "Required Rate", "Max Rate", "Publisher's Min Rate", "Publisher's Max Rate")
      val rowList = List(List(
        sInfo.subsystem,
        sInfo.component,
        si.path,
        formatRate(sInfo.requiredRate),
        formatRate(sInfo.maxRate),
        formatRate(si.telemetryModel.map(_.minRate).getOrElse(0.0)),
        formatRate(si.telemetryModel.map(_.maxRate).getOrElse(0.0))
      ))

      val attrTable = si.telemetryModel.map(t => attributeListMarkup("Attributes", t.attributesList)).getOrElse(div())
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
      else div(
        h4(s"$pubType Subscribed to by $compName"),
        div(
          Styles.componentSection,
          table(Styles.componentTable, attr("data-toggle") := "table",
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
                val usage = if (s.subscribeModelInfo.usage.isEmpty) div()
                else div(
                  strong("Usage:"),
                  raw(s.subscribeModelInfo.usage)
                )
                List(
                  tr(
                    td(Styles.attributeCell, p(btn,
                      a(name := idFor(compName, "subscribes", pubType, s.subscribeModelInfo.name))(s.subscribeModelInfo.name))),
                    td(raw(s.description), getWarning(s), usage),
                    td(p(makeLinkForPublisher(s)))
                  ),
                  row
                )
              }
            ))
        )
      )
    }

    subscribesOpt match {
      case None => div()
      case Some(subscribes) =>
        if (subscribes.subscribeInfo.nonEmpty) {
          div(
            Styles.componentSection,
            h3(s"Items subscribed to by $compName"),
            raw(subscribes.description),
            subscribeListMarkup("Telemetry", subscribes.subscribeInfo.filter(_.itemType == Telemetry)),
            subscribeListMarkup("Events", subscribes.subscribeInfo.filter(_.itemType == Events)),
            subscribeListMarkup("Event Streams", subscribes.subscribeInfo.filter(_.itemType == EventStreams)),
            subscribeListMarkup("Alarms", subscribes.subscribeInfo.filter(_.itemType == Alarms))
          )
        } else div()
    }
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a sender link
    def clickedOnSender(sender: OtherComponent)(e: dom.Event): Unit = {
      e.preventDefault()
      listener.componentSelected(ComponentLink(sender.subsystem, sender.compName))
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: OtherComponent) = {
      a(s"${sender.subsystem}.${sender.compName} ", href := "#", onclick := clickedOnSender(sender) _)
    }

    // Returns a table row displaying more details for the given command
    def makeDetailsRow(r: ReceivedCommandInfo) = {
      val m = r.receiveCommandModel
      div(
        if (m.requirements.isEmpty) div() else p(strong("Requirements: "), m.requirements.mkString(", ")),
        parameterListMarkup("Arguments", m.args, m.requiredArgs)
      )
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(
      Styles.componentSection,
      h4(s"Command Configurations Received by $compName"),
      table(Styles.componentTable, attr("data-toggle") := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Senders")
          )
        ),
        tbody(
          for (r <- info) yield {
            val rc = r.receiveCommandModel
            val (btn, row) = hiddenRowMarkup(makeDetailsRow(r), 3)
            List(
              tr(
                td(Styles.attributeCell, p(btn,
                  a(name := idFor(compName, "receives", "Commands", rc.name))(rc.name))),
                // XXX TODO: Make link to command description page with details
                td(raw(rc.description)),
                td(p(r.senders.map(makeLinkForSender)))
              ),
              row
            )
          }
        ))
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

    // Returns a table row displaying more details for the given command
    def makeDetailsRow(r: ReceiveCommandModel) = {
      div(
        if (r.requirements.isEmpty) div() else p(strong("Requirements: "), r.requirements.mkString(", ")),
        parameterListMarkup("Arguments", r.args, r.requiredArgs)
      )
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
          val (btn, row) = hiddenRowMarkup(makeDetailsRow(r), 3)
          List(
            tr(
              td(Styles.attributeCell, p(btn,
                a(name := idFor(compName, "sends", "Commands", r.name))(r.name))),
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
    else div(
      Styles.componentSection,
      h4(s"Command Configurations Sent by $compName"),
      table(Styles.componentTable, attr("data-toggle") := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Receiver")
          )
        ),
        tbody(
          for (s <- info) yield makeItem(s)
        ))
    )
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands]) = {
    import scalatags.JsDom.all._
    commandsOpt match {
      case None => div()
      case Some(commands) =>
        if (commands.commandsReceived.isEmpty && commands.commandsSent.isEmpty) div()
        else div(
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
      table(Styles.componentTable, attr("data-toggle") := "table",
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
        ))
    )
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo): TypedTag[Div] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    val idStr = getComponentInfoId(info.componentModel.component)

    div(Styles.component, id := idStr)(
      h2(info.componentModel.component),
      componentInfoTableMarkup(info),
      raw(info.componentModel.description),
      publishMarkup(info.componentModel.component, info.publishes),
      subscribeMarkup(info.componentModel.component, info.subscribes),
      commandsMarkup(info.componentModel.component, info.commands)
    )
  }

}
