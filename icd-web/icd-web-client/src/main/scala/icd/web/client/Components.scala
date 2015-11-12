package icd.web.client

import icd.web.shared._
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import upickle.default._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import Components._

object Components {
  // Id of component info for given component name
  def getComponentInfoId(compName: String) = s"$compName-info"

  /**
   * Information about a link to a component
   * @param subsystem the component's subsystem
   * @param compName the component name
   */
  case class ComponentLink(subsystem: String, compName: String)

  trait ComponentListener {
    /**
     * Called when a link for the component is clicked
     * @param link conatins the component's subsystem and name
     */
    def componentSelected(link: ComponentLink): Unit
  }

  // Displayed version for unpublished APIs
  val unpublished = "(unpublished)"
}

/**
 * Manages the component (Assembly, HCD) display
 * @param mainContent used to display information about selected components
 * @param listener called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components._

  /**
   * Gets information about the given components
   * @param subsystem the components' subsystem
   * @param versionOpt optional version (default: current version)
   * @param compNames list of component names
   * @param targetSubsystem optional target subsystem and version
   * @return future list of objects describing the components
   */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String], compNames: List[String],
                               targetSubsystem: SubsystemWithVersion): Future[List[ComponentInfo]] = {
    Ajax.get(Routes.icdComponentInfo(subsystem, versionOpt, compNames, targetSubsystem)).map { r ⇒
      val list = read[List[ComponentInfo]](r.responseText)
      if (targetSubsystem.subsystemOpt.isDefined) list.map(applyIcdFilter) else list
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
   * @param compNames the names of the components
   * @param sv the selected subsystem and version
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
   * @param compName the name of the component
   * @param sv the selected subsystem
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
   * @param sv the subsystem and version to use for the component
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

  // For ICDs, we are only interested in the interface between the two subsystems.
  // Filter out any published commands with no subscribers,
  // and any commands received, with no senders
  private def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val publishInfo = info.publishInfo.filter(p ⇒ p.subscribers.nonEmpty)
    val commandsReceived = info.commandsReceived.filter(p ⇒ p.otherComponents.nonEmpty)
    ComponentInfo(info.subsystem, info.compName, info.title, info.description, info.prefix,
      info.componentType, info.wbsId, publishInfo, info.subscribeInfo, commandsReceived, info.commandsSent)
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
   * @param info contains the information to display
   */
  private def displayComponentInfo(info: ComponentInfo): Unit = {
    if (info.publishInfo.nonEmpty || info.subscribeInfo.nonEmpty || info.commandsReceived.nonEmpty || info.commandsSent.nonEmpty) {
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

  // Generates the HTML markup to display the component's publish information
  private def publishMarkup(compName: String, pubInfo: List[PublishInfo]) = {
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

    // Only display non-empty tables
    if (pubInfo.isEmpty) div()
    else div(Styles.componentSection,
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
      listener.componentSelected(ComponentLink(info.subsystem, info.compName))
    }

    // Makes the link for a publisher component in the table
    def makeLinkForPublisher(info: SubscribeInfo) = {
      a(title := s"Show API for ${info.compName}",
        s"${info.compName} ",
        href := "#",
        onclick := clickedOnPublisher(info) _)
    }

    if (subInfo.isEmpty) div()
    else div(Styles.componentSection,
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
            val path = s.name.split('.')
            val prefix = path.dropRight(1).mkString(".")
            val name = path.last
            tr(
              td(prefix, br, s".$name"),
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
      listener.componentSelected(ComponentLink(sender.subsystem, sender.compName))
    }

    // Makes the link for a sender component in the table
    def makeLinkForSender(sender: OtherComponent) = {
      a(s"${sender.compName} ", href := "#", onclick := clickedOnSender(sender) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(Styles.componentSection,
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
      listener.componentSelected(ComponentLink(receiver.subsystem, receiver.compName))
    }

    // Makes the link for a receiver component in the table
    def makeLinkForReceiver(receiver: OtherComponent) = {
      a(s"${receiver.compName} ", href := "#", onclick := clickedOnReceiver(receiver) _)
    }

    // Only display non-empty tables
    if (info.isEmpty) div()
    else div(Styles.componentSection,
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
      p(info.description),
      componentInfoTableMarkup(info),
      publishMarkup(info.compName, info.publishInfo),
      subscribeMarkup(info.compName, info.subscribeInfo),
      receivedCommandsMarkup(info.compName, info.commandsReceived),
      sentCommandsMarkup(info.compName, info.commandsSent))
  }

}
