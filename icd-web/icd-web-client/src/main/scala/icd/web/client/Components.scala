package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import shared._
import upickle._

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages the component (Assembly, HCD) display
 * @param mainContent used to display information about selected components
 * @param listener called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
case class Components(mainContent: MainContent, listener: String ⇒ Unit) {

  /**
   * Adds (appends) a component to the display
   * @param compName the name of the component
   * @param filter an optional list of target component names to use to filter the
   *               display (restrict to only those target components)
   */
  def addComponent(compName: String, filter: Option[List[String]]): Unit = {
    Ajax.get(Routes.componentInfo(compName)).map { r ⇒
      val info = applyFilter(filter, read[ComponentInfo](r.responseText))
      removeComponent(compName)
      displayInfo(info, filtered = filter.isDefined)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
    }
  }

  /**
   * Displays only the given component's information, ignoring any filter
   * @param compName the name of the component
   */
  def setComponent(compName: String): Unit = {
    Ajax.get(Routes.componentInfo(compName)).map { r ⇒
      val info = read[ComponentInfo](r.responseText)
      mainContent.clearContent()
      mainContent.contentTitle.scrollTop = 0
      displayInfo(info, filtered = false)
    }.recover {
      case ex ⇒
        mainContent.displayInternalError(ex)
    }
  }

  // Filter out any components not in the filter, if the filter is defined
  private def applyFilter(filter: Option[List[String]], info: ComponentInfo): ComponentInfo = {
    filter match {
      case Some(names) ⇒
        val publishInfo = info.publishInfo.filter(p ⇒
          p.subscribers.exists(s ⇒
            names.contains(s.compName)))

        val subscribeInfo = info.subscribeInfo.filter(s ⇒
          names.contains(s.compName))

        val commandsReceived = info.commandsReceived.filter(p ⇒
          p.otherComponents.exists(s ⇒
            names.contains(s.compName)))

        val commandsSent = info.commandsSent.filter(p ⇒
          p.otherComponents.exists(s ⇒
            names.contains(s.compName)))

        ComponentInfo(info.name, info.description, publishInfo, subscribeInfo,
          commandsReceived, commandsSent)
      case None ⇒ info
    }
  }

  // Id of component info for given component name
  private def getComponentInfoId(compName: String) = s"$compName-info"

  // Removes the component display
  def removeComponent(compName: String): Unit = {
    val elem = $id(getComponentInfoId(compName))
    try {
      // XXX How to check if elem exists?
      mainContent.content.removeChild(elem)
    } catch {
      case t: Throwable ⇒
    }
  }

  /**
   * Displays the information for a component, appending to the other selected components, if any.
   * @param info contains the information to display
   * @param filtered true if the filter button is checked
   */
  private def displayInfo(info: ComponentInfo, filtered: Boolean): Unit = {
    if (info.publishInfo.nonEmpty || info.subscribeInfo.nonEmpty || info.commandsReceived.nonEmpty || info.commandsSent.nonEmpty) {
      val titleStr = "Components" + (if (filtered) " (filtered)" else "")
      val markup = markupForComponent(info)
      if (mainContent.contentTitle.textContent != titleStr) {
        mainContent.clearContent()
        mainContent.setContentTitle(titleStr)
      }
      val element = markup.render
      mainContent.content.appendChild(element)
    }
  }

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, pubInfo: List[PublishInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event) = {
      println(s"XXX clickedOnSubscriber: component name = ${info.compName}")
      listener(info.compName)
    }

    // Makes the link for a subscriber component in the table
    def makeLinkForSubscriber(info: SubscribeInfo) = {
      a(s"${info.compName} ", href := "#", onclick := clickedOnSubscriber(info) _)
    }

    // Only display non-empty tables
    if (pubInfo.isEmpty) div()
    else div(
      h3(s"Items published by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Type"),
            th("Description"),
            th("Subscribers"))),
        tbody(
          for (p ← pubInfo) yield {
            tr(
              td(p.name),
              td(p.itemType),
              td(p.description),
              td(p.subscribers.map(makeLinkForSubscriber)))
          })))
  }

  // Generates the HTML markup to display the component's subscribe information
  private def subscribeMarkup(compName: String, subInfo: List[SubscribeInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a subscriber link
    def clickedOnPublisher(info: SubscribeInfo)(e: dom.Event) = {
      listener(info.compName)
    }

    // Makes the link for a publisher component in the table
    def makeLinkForPublisher(info: SubscribeInfo) = {
      a(s"${info.compName} ", href := "#", onclick := clickedOnPublisher(info) _)
    }

    if (subInfo.isEmpty) div()
    else div(
      h3(s"Items subscribed to by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Prefix.Name"),
            th("Type"),
            th("Description"),
            th("Publisher"))),
        tbody(
          for (s ← subInfo) yield {
            tr(
              td(s.name),
              td(s.itemType),
              td(s.description),
              td(makeLinkForPublisher(s)))
          })))
  }

  // Generates the HTML markup to display the commands a component receives
  private def receivedCommandsMarkup(compName: String, info: List[CommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a sender link
    def clickedOnSender(sender: OtherComponent)(e: dom.Event) = {
      listener(sender.compName)
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: OtherComponent) = {
      a(s"${sender.compName} ", href := "#", onclick := clickedOnSender(sender) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(
      h3(s"Command Configurations Received by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Senders"))),
        tbody(
          for (p ← info) yield {
            tr(
              td(p.name), // XXX TODO: Make link to command description page with details
              td(p.description),
              td(p.otherComponents.map(makeLinkForSender)))
          })))
  }

  // Generates the HTML markup to display the commands a component sends
  private def sentCommandsMarkup(compName: String, info: List[CommandInfo]) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Action when user clicks on a receiver link
    def clickedOnReceiver(receiver: OtherComponent)(e: dom.Event) = {
      listener(receiver.compName)
    }

    // Makes the link for a receiver component in the table
    def makeLinkForReceiver(receiver: OtherComponent) = {
      a(s"${receiver.compName} ", href := "#", onclick := clickedOnReceiver(receiver) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(
      h3(s"Command Configurations Sent by $compName"),
      table(Styles.componentTable, "data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Description"),
            th("Receiver"))),
        tbody(
          for (p ← info) yield {
            tr(
              td(p.name), // XXX TODO: Make link to command description page with details
              td(p.description),
              td(p.otherComponents.map(makeLinkForReceiver)))
          })))
  }

  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo) = {
    import scalatags.JsDom.all._

    div(cls := "container", id := getComponentInfoId(info.name))(
      h2(info.name),
      p(info.description),
      publishMarkup(info.name, info.publishInfo),
      subscribeMarkup(info.name, info.subscribeInfo),
      receivedCommandsMarkup(info.name, info.commandsReceived),
      sentCommandsMarkup(info.name, info.commandsSent))
  }
}
