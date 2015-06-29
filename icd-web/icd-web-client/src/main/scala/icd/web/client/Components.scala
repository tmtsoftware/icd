package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import shared._
import upickle._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }
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

  // Information displayed at top of components page
  case class TitleInfo(title: String, subtitleOpt: Option[String], descriptionOpt: Option[String])
}

/**
 * Manages the component (Assembly, HCD) display
 * @param mainContent used to display information about selected components
 * @param listener called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
case class Components(mainContent: MainContent, listener: ComponentListener) {

  import Components._
  import Subsystem._

  // Gets the title and optional subtitle to display based on the selected source and target subsystems
  private def getTitleInfo(subsystemInfo: SubsystemInfo,
                           targetSubsystem: SubsystemWithVersion,
                           icdOpt: Option[IcdVersion]): TitleInfo = {
    if (icdOpt.isDefined) {
      val icd = icdOpt.get
      val title = s"ICD from ${icd.subsystem} to ${icd.target} (version ${icd.icdVersion})"
      val subtitle = s"Based on ${icd.subsystem} ${icd.subsystemVersion} and ${icd.target} ${icd.targetVersion}"
      TitleInfo(title, Some(subtitle), None)
    } else {
      val version = subsystemInfo.versionOpt.getOrElse(unpublished)
      if (targetSubsystem.subsystemOpt.isDefined) {
        val target = targetSubsystem.subsystemOpt.get
        val targetVersion = targetSubsystem.versionOpt.getOrElse(unpublished)
        val title = s"ICD from ${subsystemInfo.subsystem} to $target $unpublished"
        val subtitle = s"Based on ${subsystemInfo.subsystem} $version and $target $targetVersion"
        TitleInfo(title, Some(subtitle), None)
      } else {
        TitleInfo(s"API for ${subsystemInfo.subsystem} $version",
          Some(subsystemInfo.title), Some(subsystemInfo.description))
      }
    }
  }

  /**
   * Gets information about the given components
   * @param subsystem the components' subsystem
   * @param versionOpt optional version (default: current version)
   * @param compNames list of component names
   * @param filter list of target component names used to filter list
   * @return future list of objects describing the components
   */
  private def getComponentInfo(subsystem: String, versionOpt: Option[String],
                               compNames: List[String],
                               filter: Option[List[String]]): Future[List[ComponentInfo]] = {
    Future.sequence {
      for (compName ← compNames) yield Ajax.get(Routes.componentInfo(subsystem, compName, versionOpt)).map { r ⇒
        applyFilter(filter, read[ComponentInfo](r.responseText))
      }
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
   * @param filter an optional list of target component names to use to filter the
   *               display (restrict to only those target components)
   * @param sv the selected subsystem and version
   * @param targetSubsystem the target subsystem (might not be set)
   */
  def addComponents(compNames: List[String], filter: Option[List[String]],
                    sv: SubsystemWithVersion, targetSubsystem: SubsystemWithVersion,
                    icdOpt: Option[IcdVersion]): Future[Unit] = {
    sv.subsystemOpt match {
      case None ⇒ Future.successful()
      case Some(subsystem) ⇒
        val f = for {
          subsystemInfo ← getSubsystemInfo(subsystem, sv.versionOpt)
          infoList ← getComponentInfo(subsystem, sv.versionOpt, compNames, filter)
        } yield {
          val titleInfo = getTitleInfo(subsystemInfo, targetSubsystem, icdOpt)
          mainContent.clearContent()
          mainContent.setTitle(titleInfo.title, titleInfo.subtitleOpt)
          mainContent.setDescription(titleInfo.descriptionOpt.getOrElse(""))
          infoList.foreach(displayComponentInfo)
        }
        f.onFailure { case ex ⇒ mainContent.displayInternalError(ex) }
        f
    }
  }

  /**
   * Adds (appends) a component to the display
   * @param compName the name of the component
   * @param filter an optional list of target component names to use to filter the
   *               display (restrict to only those target components)
   * @param sv the selected subsystem
   * @param targetSubsystem the target subsystem (might not be set)
   */
  def addComponent(compName: String, filter: Option[List[String]],
                   sv: SubsystemWithVersion,
                   targetSubsystem: SubsystemWithVersion,
                   icdOpt: Option[IcdVersion]): Unit = {
    sv.subsystemOpt.foreach { subsystem ⇒
      Ajax.get(Routes.componentInfo(subsystem, compName, sv.versionOpt)).map { r ⇒ // Future!
        val info = applyFilter(filter, read[ComponentInfo](r.responseText))
        displayComponentInfo(info)
      }.recover {
        case ex ⇒
          mainContent.displayInternalError(ex)
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
      val path = Routes.componentInfo(sv.subsystemOpt.get, compName, sv.versionOpt)
      Ajax.get(path).map { r ⇒
        val info = read[ComponentInfo](r.responseText)
        mainContent.clearContent()
        mainContent.scrollToTop()
        mainContent.setTitle(s"Component: $compName")
        displayComponentInfo(info)
      }.recover {
        case ex ⇒
          mainContent.displayInternalError(ex)
      }
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

        ComponentInfo(info.subsystem, info.compName, info.description, publishInfo, subscribeInfo,
          commandsReceived, commandsSent)
      case None ⇒ info
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
      listener.componentSelected(ComponentLink(sender.subsystem, sender.compName))
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
      listener.componentSelected(ComponentLink(receiver.subsystem, receiver.compName))
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

    div(cls := "container", id := getComponentInfoId(info.compName))(
      h2(info.compName),
      p(info.description),
      publishMarkup(info.compName, info.publishInfo),
      subscribeMarkup(info.compName, info.subscribeInfo),
      receivedCommandsMarkup(info.compName, info.commandsReceived),
      sentCommandsMarkup(info.compName, info.commandsSent))
  }

}
