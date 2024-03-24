package icd.web.shared

import icd.web.shared.ComponentInfo.PublishType
import icd.web.shared.IcdModels.*

object ComponentInfo {

  // Types of published items
  sealed trait PublishType

  case object Events extends PublishType

  case object ObserveEvents extends PublishType

  case object CurrentStates extends PublishType

  case object Images extends PublishType

  case object Alarms extends PublishType

  /**
   * Filters out any published commands with no subscribers, and any commands received, with no senders,
   * etc. As a result, only the interface between the two subsystems should remain.
   *
   * @param info all the information for a component
   * @return a new ComponentInfo with empty items removed
   */
  def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val (oldEventList, oldObserveEventList, oldCurrentStateList, oldImageList) =
      info.publishes match {
        case None => (Nil, Nil, Nil, Nil)
        case Some(p) =>
          (p.eventList, p.observeEventList, p.currentStateList, p.imageList)
      }
    val oldCommandsReceived = info.commands.toList.flatMap(_.commandsReceived)
    val oldCommandsSent     = info.commands.toList.flatMap(_.commandsSent)

    val oldServicesProvided = info.services.toList.flatMap(_.servicesProvided)
    val oldServicesRequired = info.services.toList.flatMap(_.servicesRequired)

    val newEventList        = oldEventList.filter(p => p.subscribers.nonEmpty)
    val newObserveEventList = oldObserveEventList.filter(p => p.subscribers.nonEmpty)
    val newCurrentStateList = oldCurrentStateList.filter(p => p.subscribers.nonEmpty)
    val newImageList        = oldImageList.filter(p => p.subscribers.nonEmpty)

    val newCommandsReceived = oldCommandsReceived.filter(p => p.senders.nonEmpty)
    val newCommandsSent = oldCommandsSent.filter(p => p.receiver.nonEmpty)

    val newServicesProvided = oldServicesProvided.filter(p => p.requiredBy.nonEmpty)
    val newServicesRequired = oldServicesRequired.filter(p => p.maybeServiceModelProvider.nonEmpty)

    val publishes =
      info.publishes.map(p =>
        p.copy(
          eventList = newEventList,
          observeEventList = newObserveEventList,
          currentStateList = newCurrentStateList,
          imageList = newImageList
        )
      )

    val commands = info.commands.map(c =>
      c.copy(
        commandsReceived = newCommandsReceived,
        commandsSent = newCommandsSent
      )
    )

    val services = info.services.map(s =>
      s.copy(
        servicesProvided = newServicesProvided,
        servicesRequired = newServicesRequired
      )
    )

    ComponentInfo(info.componentModel, publishes, info.subscribes, commands, services)
  }

  /**
   * Returns true if the argument contains any lists with publishers, subscribers, ect. that are non-empty
   */
  def nonEmpty(info: ComponentInfo): Boolean = {
    info.publishes.exists(_.nonEmpty) ||
    info.subscribes.exists(_.subscribeInfo.nonEmpty) ||
    info.commands.exists(_.nonEmpty) ||
    info.services.nonEmpty
  }
}

/**
 * ICD Component information passed to client
 *
 * @param componentModel the component model
 * @param publishes      describes items published by the component
 * @param subscribes     describes items the component subscribes to
 * @param commands       describes commands the component can send and receive
 * @param services       describes services the component provides or requires
 */
case class ComponentInfo(
    componentModel: ComponentModel,
    publishes: Option[Publishes],
    subscribes: Option[Subscribes],
    commands: Option[Commands],
    services: Option[Services]
)

/**
 * Describes a published event or image
 *
 * @param model  the publisher's event or image information
 * @param subscribers a list of the other components that subscribe to this item
 */
case class EventOrImageInfo(model: NameDesc, subscribers: List[SubscribeInfo])


/**
 * Describes a published event
 *
 * @param eventModel  the publisher's event information
 * @param subscribers a list of the other components that subscribe to this item
 */
case class EventInfo(eventModel: EventModel, subscribers: List[SubscribeInfo])

/**
 * Describes a published image
 *
 * @param imageModel  the publisher's image information
 * @param subscribers a list of the other components that subscribe to this image
 */
case class ImageInfo(imageModel: ImageModel, subscribers: List[SubscribeInfo])

/**
 * Describes what values a component publishes
 *
 * @param description      optional top level description of published items (in html format, after markdown processing)
 * @param eventList        list of published events
 * @param observeEventList list of published observe events
 * @param currentStateList list of published current states
 * @param imageList        list of published images
 * @param alarmList        list of published alarms
 */
case class Publishes(
    description: String,
    eventList: List[EventInfo],
    observeEventList: List[EventInfo],
    currentStateList: List[EventInfo],
    imageList: List[ImageInfo],
    alarmList: List[AlarmModel]
) {

  /**
   * True if the component publishes something
   */
  def nonEmpty: Boolean =
    eventList.nonEmpty || observeEventList.nonEmpty || currentStateList.nonEmpty || imageList.nonEmpty || alarmList.nonEmpty
}

/**
 * Holds information about a subscriber
 *
 * @param componentModel     the subscriber's component model
 * @param itemType           the publish type (Event, Alarm, ObserveEvent, etc.)
 * @param subscribeModelInfo data from the input subscribe model
 */
case class SubscribeInfo(componentModel: ComponentModel, itemType: PublishType, subscribeModelInfo: SubscribeModelInfo)

/**
 * Describes an item that a component subscribes to, including information from the publisher
 *
 * @param itemType           the publish type (Event, Alarm, ObserveEvent, etc.)
 * @param subscribeModelInfo data from the input subscribe model
 * @param eventModel         set only if itemType is not Alarms or Image
 * @param imageModel         set only if itemType is Image
 * @param publisher          the publisher's component model
 * @param warnings           if true, display a warning if no publisher event was specified
 */
case class DetailedSubscribeInfo(
    itemType: PublishType,
    subscribeModelInfo: SubscribeModelInfo,
    eventModel: Option[EventModel],
    imageModel: Option[ImageModel],
    publisher: Option[ComponentModel],
    warnings: Boolean = true
) {
  val description: String = eventModel.orElse(imageModel).map(_.description).getOrElse("")
  val warning: Option[String] =
    if (!warnings || eventModel.nonEmpty || imageModel.nonEmpty) None
    else if (publisher.isEmpty) {
      Some(
        s"Component ${subscribeModelInfo.subsystem}.${subscribeModelInfo.component} was not found"
      )
    }
    else {
      Some(
        s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component} does not publish $itemType: ${subscribeModelInfo.name}"
      )
    }

  /**
   * Full path to subscribed item: prefix.name
   */
  val path: String = s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component}.${subscribeModelInfo.name}"
}

/**
 * Describes what items a component subscribes to
 *
 * @param description   optional top level description of subscribed items (in html format, after markdown processing)
 * @param subscribeInfo a list of subscribed items
 */
case class Subscribes(description: String, subscribeInfo: List[DetailedSubscribeInfo])

/**
 * Describes another component (receiver, for sent commands, sender for received commands)
 *
 * @param subsystem the subsystem of the other component
 * @param compName  the other component
 */
case class OtherComponent(subsystem: String, compName: String)

/**
 * Describes a configuration command sent to another component
 *
 * @param name                the command name
 * @param subsystem           the subsystem that receives the command
 * @param component           the component that receives the command
 * @param receiveCommandModel the model for the receiving end of the command, if found
 * @param receiver            the receiving component, if found
 */
case class SentCommandInfo(
    name: String,
    subsystem: String,
    component: String,
    receiveCommandModel: Option[ReceiveCommandModel],
    receiver: Option[ComponentModel],
    warnings: Boolean = true
) {

  val warning: Option[String] =
    if (!warnings || receiveCommandModel.nonEmpty) None
    else if (receiver.isEmpty) {
      Some(s"Component $subsystem.$component was not found")
    }
    else {
      Some(s"$subsystem.$component does not define configuration: $name")
    }
}

/**
 * Describes a command config received by this component
 *
 * @param receiveCommandModel the basic model for the command
 * @param senders             list of components that send the command
 */
case class ReceivedCommandInfo(receiveCommandModel: ReceiveCommandModel, senders: List[ComponentModel])

/**
 * Describes commands the component sends and receives
 *
 * @param description      optional top level description of commands (in html format, after markdown processing)
 * @param commandsReceived a list of commands received be this component
 * @param commandsSent     a list of commands sent by this component
 */
case class Commands(description: String, commandsReceived: List[ReceivedCommandInfo], commandsSent: List[SentCommandInfo]) {

  /**
   * True if at the component sends or receives commands
   */
  def nonEmpty: Boolean = commandsReceived.nonEmpty || commandsSent.nonEmpty

}

/**
 * Describes a service required by this component
 *
 * @param serviceModelClient  describes the client's use of the service
 * @param maybeServiceModelProvider  describes the service provided, if known
 * @param provider  the component that should define the service, if known
 * @param warnings  true if service providergetServicesProvided not found
 */
case class ServicesRequiredInfo(
    serviceModelClient: ServiceModelClient,
    maybeServiceModelProvider: Option[ServiceModelProvider],
    provider: Option[ComponentModel],
    warnings: Boolean = true
) {

  val warning: Option[String] =
    if (!warnings || maybeServiceModelProvider.nonEmpty) None
    else if (provider.isEmpty) {
      Some(s"Component ${serviceModelClient.subsystem}.${serviceModelClient.component} was not found")
    }
    else {
      Some(
        s"${serviceModelClient.subsystem}.${serviceModelClient.component} does not define configuration: ${serviceModelClient.name}"
      )
    }
}

/**
 * Describes a service provided by this component
 *
 * @param serviceModelProvider the basic model for the service
 * @param requiredBy           list of components that use/require the service along with the paths used
 */
case class ServiceProvidedInfo(serviceModelProvider: ServiceModelProvider, requiredBy: List[ServiceModelClientComponent])

/**
 * Describes services the component uses and provides
 *
 * @param description      optional top level description of the services (in html format, after markdown processing)
 * @param servicesProvided a list of services provided by this component
 * @param servicesRequired a list of services required by this component
 */
case class Services(
    description: String,
    servicesProvided: List[ServiceProvidedInfo],
    servicesRequired: List[ServicesRequiredInfo]
) {

  /**
   * True if at the component provides or requires a service
   */
  def nonEmpty: Boolean = servicesProvided.nonEmpty || servicesRequired.nonEmpty

}

/**
 * Class used when creating summary tables
 */
object SummaryInfo {

  /**
   * Used where the item description from the other subsystem may not be available
   */
  case class OptionalNameDesc(name: String, opt: Option[NameDesc]) extends NameDesc {
    override val description: String = opt.map(_.description).getOrElse("")
  }

  /**
   * For summary of a published item or received command.
   *
   * @param publisher the publishing (or receiving, for commands) component
   * @param item      name and description of the item or command
   * @param subscribers list of subscribers or command senders
   */
  case class PublishedItem(publisher: ComponentModel, item: NameDesc, subscribers: List[ComponentModel])

  /**
   * For summary of a provided service, including paths used by client
   * @param provider service provider
   * @param service describes the service
   * @param consumers list of service consumers
   */
  case class ProvidedServiceItem(provider: ComponentModel, service: ServiceProvidedInfo, consumers: List[ComponentModel])

  /**
   * Summary of a subscribed item.
   *
   * @param publisherSubsystem the publisher's subsystem
   * @param publisherComponent the publisher's component
   * @param maybePublisher     the publisher's component model, if known
   * @param maybeWarning       a warning, in case the publisher's component model, is not known
   * @param subscriber         the subscriber's component model
   * @param item               name and description of the published item
   */
  case class SubscribedItem(
      publisherSubsystem: String,
      publisherComponent: String,
      maybePublisher: Option[ComponentModel],
      maybeWarning: Option[String],
      subscriber: ComponentModel,
      item: NameDesc
  )

}
