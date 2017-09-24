package icd.web.shared

import icd.web.shared.ComponentInfo.{PublishType, Telemetry}
import icd.web.shared.IcdModels._

object ComponentInfo {

  // Types of published items
  sealed trait PublishType

  case object Telemetry extends PublishType

  case object Events extends PublishType

  case object EventStreams extends PublishType

  case object Alarms extends PublishType

  // For ICDs, we are only interested in the interface between the two subsystems.
  // Filter out any published commands with no subscribers,
  // and any commands received, with no senders
  def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val (oldTelemetryList, oldEventList, oldEventStreamList, oldAlarmList) = info.publishes match {
      case None => (Nil, Nil, Nil, Nil)
      case Some(p) => (p.telemetryList, p.eventList, p.eventStreamList, p.alarmList)
    }
    val oldCommandsReceived = info.commands.toList.flatMap(_.commandsReceived)
    val oldCommandsSent = info.commands.toList.flatMap(_.commandsSent)

    val newTelemetryList = oldTelemetryList.filter(p => p.subscribers.nonEmpty)
    val newEventList = oldEventList.filter(p => p.subscribers.nonEmpty)
    val newEventStreamList = oldEventStreamList.filter(p => p.subscribers.nonEmpty)
    val newAlarmList = oldAlarmList.filter(p => p.subscribers.nonEmpty)

    val newCommandsReceived = oldCommandsReceived.filter(p => p.senders.nonEmpty)
    val newCommandsSent = oldCommandsSent.filter(p => p.receiver.nonEmpty)

    val publishes = info.publishes.map(p => p.copy(
      telemetryList = newTelemetryList,
      eventList = newEventList,
      eventStreamList = newEventStreamList,
      alarmList = newAlarmList))

    val commands = info.commands.map(c => c.copy(
      commandsReceived = newCommandsReceived, commandsSent = newCommandsSent
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
case class ComponentInfo(
  componentModel: ComponentModel,
  publishes: Option[Publishes],
  subscribes: Option[Subscribes],
  commands: Option[Commands])

/**
 * Describes a published telemetry, event or event stream item
 *
 * @param telemetryModel the publisher's telemetry information
 * @param subscribers    a list of the other components that subscribe to this item
 */
case class TelemetryInfo(
  telemetryModel: TelemetryModel,
  subscribers: List[SubscribeInfo])

/**
 * Describes an alarm
 *
 * @param alarmModel  the basic alarm model
 * @param subscribers list of components who subscribe to the alarm
 */
case class AlarmInfo(
  alarmModel: AlarmModel,
  subscribers: List[SubscribeInfo])

/**
 * Describes what values a component publishes
 *
 * @param description     optional top level description of published items (in html format, after markdown processing)
 * @param telemetryList   list of published telemetry
 * @param eventList       list of published events
 * @param eventStreamList list of published event streams
 * @param alarmList       list of published alarms
 */
case class Publishes(
  description: String,
  telemetryList: List[TelemetryInfo],
  eventList: List[TelemetryInfo],
  eventStreamList: List[TelemetryInfo],
  alarmList: List[AlarmInfo]) {
  /**
   * True if at the component publishes something
   */
  def nonEmpty: Boolean = telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty
}

/**
 * Holds information about a subscriber
 *
 * @param componentModel     the subscriber's component model
 * @param itemType           the publish type (Telemetry, Alarm, EventStream, etc.)
 * @param subscribeModelInfo data from the input subscribe model
 */
case class SubscribeInfo(componentModel: ComponentModel, itemType: PublishType, subscribeModelInfo: SubscribeModelInfo)

/**
 * Describes an item that a component subscribes to, including information from the publisher
 *
 * @param itemType           the publish type (Telemetry, Alarm, EventStream, etc.)
 * @param subscribeModelInfo data from the input subscribe model
 * @param telemetryModel     set only if itemType is not Alarms
 * @param alarmModel         set only if itemType is Alarms
 * @param publisher          the publisher's component model
 */
case class DetailedSubscribeInfo(
  itemType: PublishType,
  subscribeModelInfo: SubscribeModelInfo,
  telemetryModel: Option[TelemetryModel],
  alarmModel: Option[AlarmModel],
  publisher: Option[ComponentModel]) {
  val description = (if (itemType == Telemetry) {
    telemetryModel.map(_.description)
  } else {
    alarmModel.map(_.description)
  }).getOrElse("")

  val warning = if (telemetryModel.nonEmpty || alarmModel.nonEmpty) None
  else {
    Some(s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component} does not publish $itemType: ${subscribeModelInfo.name}")
  }

  /**
   * Full path to subscribed item: prefix.name (if publisher was found, otherwise subsystem.component.name)
   */
  val path = publisher match {
    case Some(p) => s"${p.prefix}.${subscribeModelInfo.name}"
    case None => s"${subscribeModelInfo.subsystem}.${subscribeModelInfo.component}.${subscribeModelInfo.name}"
  }
}

/**
 * Describes what items a component subscribes to
 *
 * @param description   optional top level description of subscribed items (in html format, after markdown processing)
 * @param subscribeInfo a list of subscribed items
 */
case class Subscribes(
  description: String,
  subscribeInfo: List[DetailedSubscribeInfo])

/**
 * Describes another component (receiver, for sent commands, sender for received commands)
 *
 * @param subsystem the subsystem of the other component
 * @param compName  the other component
 */
case class OtherComponent(
  subsystem: String,
  compName: String)


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
  receiver: Option[OtherComponent]) {

  val warning = if (receiveCommandModel.nonEmpty) None
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
case class ReceivedCommandInfo(
  receiveCommandModel: ReceiveCommandModel,
  senders: List[OtherComponent])

/**
 * Describes commands the component sends and receives
 *
 * @param description      optional top level description of commands (in html format, after markdown processing)
 * @param commandsReceived a list of commands received be this component
 * @param commandsSent     a list of commands sent by this component
 */
case class Commands(
  description: String,
  commandsReceived: List[ReceivedCommandInfo],
  commandsSent: List[SentCommandInfo]) {

  /**
    * True if at the component sends or receives commands
    */
  def nonEmpty: Boolean = commandsReceived.nonEmpty || commandsSent.nonEmpty

}
