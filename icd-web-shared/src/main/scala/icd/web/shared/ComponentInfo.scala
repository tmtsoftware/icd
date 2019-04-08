package icd.web.shared

import icd.web.shared.ComponentInfo.{Alarms, PublishType}
import icd.web.shared.IcdModels._

object ComponentInfo {

  // Types of published items
  sealed trait PublishType

  case object Events extends PublishType

  case object ObserveEvents extends PublishType

  case object CurrentStates extends PublishType

  case object Alarms extends PublishType

  /**
    * Filters out any published commands with no subscribers, and any commands received, with no senders
    *
    * @param info all the information for a component
    * @return a new ComponentInfo with empty items removed
    */
  def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val (oldEventList, oldObserveEventList, oldAlarmList) =
      info.publishes match {
        case None => (Nil, Nil, Nil)
        case Some(p) =>
          (p.eventList, p.observeEventList, p.alarmList)
      }
    val oldCommandsReceived = info.commands.toList.flatMap(_.commandsReceived)
    val oldCommandsSent = info.commands.toList.flatMap(_.commandsSent)

    val newEventList = oldEventList.filter(p => p.subscribers.nonEmpty)
    val newObserveEventList =
      oldObserveEventList.filter(p => p.subscribers.nonEmpty)
    val newAlarmList = oldAlarmList.filter(p => p.subscribers.nonEmpty)

    val newCommandsReceived =
      oldCommandsReceived.filter(p => p.senders.nonEmpty)
    val newCommandsSent = oldCommandsSent.filter(p => p.receiver.nonEmpty)

    val publishes = info.publishes.map(
      p =>
        p.copy(eventList = newEventList,
          observeEventList = newObserveEventList,
          alarmList = newAlarmList))

    val commands = info.commands.map(
      c =>
        c.copy(
          commandsReceived = newCommandsReceived,
          commandsSent = newCommandsSent
        ))

    ComponentInfo(info.componentModel, publishes, info.subscribes, commands)
  }
}

/**
  * ICD Component information passed to client
  *
  * @param componentModel the component model
  * @param publishes      describes items published by the component
  * @param subscribes     describes items the component subscribes to
  * @param commands       describes commands the component can send and receive
  */
case class ComponentInfo(componentModel: ComponentModel,
                         publishes: Option[Publishes],
                         subscribes: Option[Subscribes],
                         commands: Option[Commands])

/**
  * Describes a published event
  *
  * @param eventModel  the publisher's event information
  * @param subscribers a list of the other components that subscribe to this item
  */
case class EventInfo(eventModel: EventModel, subscribers: List[SubscribeInfo])

/**
  * Describes an alarm
  *
  * @param alarmModel  the basic alarm model
  * @param subscribers list of components who subscribe to the alarm
  */
case class AlarmInfo(alarmModel: AlarmModel, subscribers: List[SubscribeInfo])

/**
  * Describes what values a component publishes
  *
  * @param description      optional top level description of published items (in html format, after markdown processing)
  * @param eventList        list of published events
  * @param observeEventList list of published observe events
  * @param currentStateList list of published current states
  * @param alarmList        list of published alarms
  */
case class Publishes(description: String,
                     eventList: List[EventInfo],
                     observeEventList: List[EventInfo],
                     currentStateList: List[EventInfo],
                     alarmList: List[AlarmInfo]) {

  /**
    * True if at the component publishes something
    */
  def nonEmpty: Boolean =
    eventList.nonEmpty || observeEventList.nonEmpty || alarmList.nonEmpty
}

/**
  * Holds information about a subscriber
  *
  * @param componentModel     the subscriber's component model
  * @param itemType           the publish type (Event, Alarm, ObserveEvent, etc.)
  * @param subscribeModelInfo data from the input subscribe model
  */
case class SubscribeInfo(componentModel: ComponentModel,
                         itemType: PublishType,
                         subscribeModelInfo: SubscribeModelInfo)

/**
  * Describes an item that a component subscribes to, including information from the publisher
  *
  * @param itemType           the publish type (Event, Alarm, ObserveEvent, etc.)
  * @param subscribeModelInfo data from the input subscribe model
  * @param eventModel         set only if itemType is not Alarms
  * @param alarmModel         set only if itemType is Alarms
  * @param publisher          the publisher's component model
  */
case class DetailedSubscribeInfo(itemType: PublishType,
                                 subscribeModelInfo: SubscribeModelInfo,
                                 eventModel: Option[EventModel],
                                 alarmModel: Option[AlarmModel],
                                 publisher: Option[ComponentModel]) {
  val description: String = (if (itemType == Alarms) {
    alarmModel.map(_.description)
  } else {
    eventModel.map(_.description)
  }).getOrElse("")

  val warning: Option[String] =
    if (eventModel.nonEmpty || alarmModel.nonEmpty) None
    else {
      Some(
        s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component} does not publish $itemType: ${subscribeModelInfo.name}")
    }

  /**
    * Full path to subscribed item: prefix.name (if publisher was found, otherwise subsystem.component.name)
    */
  val path: String = publisher match {
    case Some(p) => s"${p.prefix}.${subscribeModelInfo.name}"
    case None =>
      s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component}.${subscribeModelInfo.name}"
  }
}

/**
  * Describes what items a component subscribes to
  *
  * @param description   optional top level description of subscribed items (in html format, after markdown processing)
  * @param subscribeInfo a list of subscribed items
  */
case class Subscribes(description: String,
                      subscribeInfo: List[DetailedSubscribeInfo])

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
case class SentCommandInfo(name: String,
                           subsystem: String,
                           component: String,
                           receiveCommandModel: Option[ReceiveCommandModel],
                           receiver: Option[ComponentModel]) {

  val warning: Option[String] =
    if (receiveCommandModel.nonEmpty) None
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
case class ReceivedCommandInfo(receiveCommandModel: ReceiveCommandModel,
                               senders: List[ComponentModel])

/**
  * Describes commands the component sends and receives
  *
  * @param description      optional top level description of commands (in html format, after markdown processing)
  * @param commandsReceived a list of commands received be this component
  * @param commandsSent     a list of commands sent by this component
  */
case class Commands(description: String,
                    commandsReceived: List[ReceivedCommandInfo],
                    commandsSent: List[SentCommandInfo]) {

  /**
    * True if at the component sends or receives commands
    */
  def nonEmpty: Boolean = commandsReceived.nonEmpty || commandsSent.nonEmpty

}

/**
  * Class used when creating summary tables
  */
object SummaryInfo {

  /**
    * Used where the item description from the other subsystem may not be available
    */
  case class OptionalNameDesc(name: String, opt: Option[NameDesc])
    extends NameDesc {
    override val description: String = opt.map(_.description).getOrElse("")
  }

  /**
    * Summary of a published item or received command.
    *
    * @param publisher the publishing (or receiving, for commands) component
    * @param item      name and description of the item or command
    */
  case class PublishedItem(publisher: ComponentModel,
                           item: NameDesc,
                           subscribers: List[ComponentModel])

  /**
    * Summary of a subscribed item.
    *
    * @param publisherSubsystem the publisher's subsystem
    * @param publisherComponent the publisher's component
    * @param publisherOpt       the publisher's component model, if known
    * @param warningOpt         a warning, in case the publisher's component model, is not known
    * @param subscriber         the subscriber's component model
    * @param item               name and description of the published item
    */
  case class SubscribedItem(publisherSubsystem: String,
                            publisherComponent: String,
                            publisherOpt: Option[ComponentModel],
                            warningOpt: Option[String],
                            subscriber: ComponentModel,
                            item: NameDesc)

}
