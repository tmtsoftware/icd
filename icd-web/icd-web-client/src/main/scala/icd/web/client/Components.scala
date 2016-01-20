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

  //  // XXX Hack to toggle full text in description columns (see resize.css)
  //  private def descriptionTableCell(htmlDesc: String) = {
  //    import scalatags.JsDom.all._
  //
  //    // XXX This works, but the elipse is not displayed if html content is in the table cell
  //    //    import jquery.{ jQuery ⇒ $ }
  //    //    val tdId = UUID.randomUUID().toString
  //    //    val divId = UUID.randomUUID().toString
  //    //    def clicked()(e: dom.Event) = {
  //    //      $(s"#$tdId").toggleClass("fullDescriptionTableCell")
  //    //      $(s"#$divId").toggleClass("fullDescription")
  //    //    }
  //    //    td(id := tdId, cls := "shortDescriptionTableCell", onclick := clicked() _,
  //    //      div(id := divId, cls := "shortDescription", onclick := clicked() _,
  //    //        span(cls := "shortDescriptionSpan", onclick := clicked() _, raw(htmlDesc))))
  //
  //    td(raw(htmlDesc))
  //  }
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
    if (info.publishes.isDefined || info.subscribes.isDefined || info.commands.isDefined) {
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

  // Expandable table row for attributes
  private def attributeListMarkup(titleStr: String, attributesList: List[AttributeInfo], colSpan: Int): (TypedTag[HTMLButtonElement], TypedTag[HTMLTableRowElement]) = {
    import scalatags.JsDom.all._
    if (attributesList.isEmpty) (button(), tr())
    else {
      // button to toggle visibility
      val idStr = UUID.randomUUID().toString
      val btn = button(cls := s"btn$idStr attributeBtn btn btn-default btn-xs", "data-toggle".attr := "collapse", "data-target".attr := s"#$idStr")(
        span(cls := "glyphicon glyphicon-collapse-down"))

      val row = tr(id := idStr, cls := "collapse")(
        td(colspan := colSpan)(
          div(cls := "nopagebreak")(
            strong(titleStr),
            table(cls := "attributeTable", "data-toggle".attr := "table",
              thead(
                tr(
                  th("Name"),
                  th("Description"),
                  th("Type"),
                  th("Units"),
                  th("Default"))),
              tbody(
                for (a ← attributesList) yield {
                  tr(
                    td(a.name),
                    td(raw(a.description)),
                    td(a.typeStr),
                    td(a.units),
                    td(a.defaultValue))
                })))))
      (btn, row)
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, publishesOpt: Option[Publishes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event) = {
      listener.componentSelected(ComponentLink(info.subsystem, info.compName))
    }

    // Makes the link for a subscriber component in the table
    def makeLinkForSubscriber(info: SubscribeInfo) = {
      a(title := s"Show API for ${info.compName}",
        s"${info.compName} ",
        href := "#",
        onclick := clickedOnSubscriber(info) _)
    }

    def publishTelemetryListMarkup(pubType: String, telemetryList: List[TelemetryInfo]) = {
      if (telemetryList.isEmpty) div()
      else div(cls := "nopagebreak")(
        h4(s"$pubType Published by $compName"),
        table("data-toggle".attr := "table",
          thead(col(width := "5%"),
            col(width := "10%"),
            col(width := "75%"),
            col(width := "10%"),
            tr(
              th("Name"),
              th("Rate"),
              th("Description"),
              th("Subscribers"))),
          tbody(
            for (t ← telemetryList) yield {
              val (btn, attrRow) = attributeListMarkup("Attributes", t.attributesList, 4)
              List(tr(
                td(cls := "attributeCell", btn, t.name),
                td(s"${t.minRate} - ${t.maxRate} Hz", br, br, "Archive: ", br, if (t.archive) s" at ${t.archiveRate} Hz" else "No"),
                td(raw(t.description)),
                td(t.subscribers.map(makeLinkForSubscriber))),
                attrRow)
            })))
    }

    def publishEventListMarkup(eventList: List[EventInfo]) = {
      if (eventList.isEmpty) div()
      else div(cls := "nopagebreak")(
        h4(s"Events Published by $compName"),
        table("data-toggle".attr := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Type"),
              th("Units"),
              th("Default"),
              th("Subscribers"))),
          tbody(
            for (e ← eventList) yield {
              tr(
                td(e.attr.name),
                td(raw(e.attr.description)),
                td(e.attr.typeStr),
                td(e.attr.units),
                td(e.attr.defaultValue),
                td(e.subscribers.map(makeLinkForSubscriber)))
            })))
    }

    def publishAlarmListMarkup(alarmList: List[AlarmInfo]) = {
      if (alarmList.isEmpty) div()
      else div(cls := "nopagebreak")(
        h4(s"Alarms Published by $compName"),
        table("data-toggle".attr := "table",
          thead(
            tr(
              th("Name"),
              th("Description"),
              th("Severity"),
              th("Archive"),
              th("Subscribers"))),
          tbody(
            for (a ← alarmList) yield {
              tr(
                td(a.name),
                td(raw(a.description)),
                td(a.severity),
                td(if (a.archive) "Yes" else "No"),
                td(a.subscribers.map(makeLinkForSubscriber)))
            })))
    }

    publishesOpt match {
      case None ⇒ div()
      case Some(publishes) ⇒
        div(Styles.componentSection,
          h3(s"Items published by $compName"),
          raw(publishes.description),
          publishTelemetryListMarkup("Telemetry", publishes.telemetryList),
          publishEventListMarkup(publishes.eventList),
          publishTelemetryListMarkup("Event Streams", publishes.eventStreamList),
          publishAlarmListMarkup(publishes.alarmList))
    }
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subscribesOpt: Option[Subscribes]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnPublisher(info: SubscribeInfo)(e: dom.Event) = {
      listener.componentSelected(ComponentLink(info.subsystem, info.compName))
    }

    // Makes the link for a publisher component in the table
    def makeLinkForPublisher(info: SubscribeInfo) = {
      a(title := s"Show API for ${info.compName}",
        s"${info.compName} ",
        href := "#",
        onclick := clickedOnPublisher(info) _)
    }

    // Splits the name of the subscribed item into component prefix and simple name
    def getPrefixName(s: SubscribeInfo): (String, String) = {
      val path = s.name.split('.')
      val prefix = path.dropRight(1).mkString(".")
      val name = path.last
      (prefix, name)
    }

    subscribesOpt match {
      case None ⇒ div()
      case Some(subscribes) ⇒
        div(Styles.componentSection,
          h3(s"Items subscribed to by $compName"),
          raw(subscribes.description),
          if (subscribes.subscribeInfo.isEmpty) div()
          else table(Styles.componentTable, "data-toggle".attr := "table",
            thead(
              tr(
                th("Prefix.Name"),
                th("Type"),
                th("Description"),
                th("Usage"),
                th("Publisher"))),
            tbody(
              for (s ← subscribes.subscribeInfo) yield {
                val (prefix, name) = getPrefixName(s)
                tr(
                  td(prefix, br, s".$name"),
                  td(s.itemType),
                  td(raw(s.description)),
                  td(raw(s.usage)),
                  td(makeLinkForPublisher(s)))
              })))
    }
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[ReceivedCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a sender link
    def clickedOnSender(sender: OtherComponent)(e: dom.Event) = {
      listener.componentSelected(ComponentLink(sender.subsystem, sender.compName))
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: OtherComponent) = {
      a(s"${sender.compName} ", href := "#", onclick := clickedOnSender(sender) _)
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
            val (btn, attrRow) = attributeListMarkup("Arguments", r.args, 3)
            List(
              tr(
                td(cls := "attributeCell", btn, r.name), // XXX TODO: Make link to command description page with details
                td(raw(r.description)),
                td(r.senders.map(makeLinkForSender))),
              attrRow)
          })))
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[SentCommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a receiver link
    def clickedOnReceiver(receiver: OtherComponent)(e: dom.Event) = {
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
              td(s.name), // XXX TODO: Make link to command description page with details
              td(raw(s.description)),
              td(s.receivers.map(makeLinkForReceiver)))
          })))
  }

  // Generates the markup for the commands section (description plus received and sent)
  private def commandsMarkup(compName: String, commandsOpt: Option[Commands]) = {
    import scalatags.JsDom.all._
    commandsOpt match {
      case None ⇒ div()
      case Some(commands) ⇒
        div(cls := "nopagebreak")(
          h3(s"Commands for $compName"),
          raw(commands.description),
          receivedCommandsMarkup(compName, commands.commandsReceived),
          sentCommandsMarkup(compName, commands.commandsSent))
    }
  }

  // Generates a one line table with basic component informationdiv(
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

    div(Styles.component, id := getComponentInfoId(info.compName))(
      h2(info.compName),
      raw(info.description),
      componentInfoTableMarkup(info),
      publishMarkup(info.compName, info.publishes),
      subscribeMarkup(info.compName, info.subscribes),
      commandsMarkup(info.compName, info.commands))
  }

}
