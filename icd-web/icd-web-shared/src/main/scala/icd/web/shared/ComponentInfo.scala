package icd.web.shared

import icd.web.shared.IcdModels._

object ComponentInfo {

  // For ICDs, we are only interested in the interface between the two subsystems.
  // Filter out any published commands with no subscribers,
  // and any commands received, with no senders
  //
  // XXX TODO: filter subscribe with no publisher, commands received with no sender!
  //
  def applyIcdFilter(info: ComponentInfo): ComponentInfo = {
    val (oldTelemetryList, oldEventList, oldEventStreamList, oldAlarmList) = info.publishes match {
      case None    ⇒ (Nil, Nil, Nil, Nil)
      case Some(p) ⇒ (p.telemetryList, p.eventList, p.eventStreamList, p.alarmList)
    }
    val oldCommandsReceived = info.commands.toList.flatMap(_.commandsReceived)

    val newTelemetryList = oldTelemetryList.filter(p ⇒ p.subscribers.nonEmpty)
    val newEventList = oldEventList.filter(p ⇒ p.subscribers.nonEmpty)
    val newEventStreamList = oldEventStreamList.filter(p ⇒ p.subscribers.nonEmpty)
    val newAlarmList = oldAlarmList.filter(p ⇒ p.subscribers.nonEmpty)

    val newCommandsReceived = oldCommandsReceived.filter(p ⇒ p.senders.nonEmpty)

    val publishes = info.publishes.map(p ⇒ p.copy(
      telemetryList = newTelemetryList,
      eventList = newEventList,
      eventStreamList = newEventStreamList,
      alarmList = newAlarmList
    ))

    val commands = info.commands.map(c ⇒ c.copy(commandsReceived = newCommandsReceived))

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
  publishes:      Option[Publishes],
  subscribes:     Option[Subscribes],
  commands:       Option[Commands]
)

/**
 * Describes a published telemetry, event or event stream item
 *
 * @param telemetryModel the publisher's telemetry information
 * @param subscribers    a list of the other components that subscribe to this item
 */
case class TelemetryInfo(
  telemetryModel: TelemetryModel,
  subscribers:    List[SubscribeInfo]
)

/**
 * Describes an alarm
 *
 * @param alarmModel   the basic alarm model
 * @param subscribers  list of components who subscribe to the alarm
 */
case class AlarmInfo(
  alarmModel:  AlarmModel,
  subscribers: List[SubscribeInfo]
)

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
    description:     String,
    telemetryList:   List[TelemetryInfo],
    eventList:       List[TelemetryInfo],
    eventStreamList: List[TelemetryInfo],
    alarmList:       List[AlarmInfo]
) {
  /**
   * True if at the component publishes something
   */
  def nonEmpty: Boolean = telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty
}

/**
 * Describes an item that a component subscribes to
 *
 * @param subscribeModelInfo     data from the input subscribe model
 * @param path         the full path name (component-prefix.name) of the item
 * @param description  description of the item (from the publisher, in html format, after markdown processing)
 */
case class SubscribeInfo(
  itemType:           String,
  subscribeModelInfo: SubscribeModelInfo,
  path:               String,
  description:        String
)

/**
 * Describes what items a component subscribes to
 *
 * @param description   optional top level description of subscribed items (in html format, after markdown processing)
 * @param subscribeInfo a list of subscribed items
 */
case class Subscribes(
  description:   String,
  subscribeInfo: List[SubscribeInfo]
)

/**
 * Describes another component (receiver, for sent commands, sender for received commands)
 *
 * @param subsystem the subsystem of the other component
 * @param compName  the other component
 */
case class OtherComponent(
  subsystem: String,
  compName:  String
)

/**
 * Describes a configuration command sent to another component
 *
 * @param name        the name of the command
 * @param description description of the command (in html format, after markdown processing)
 * @param receivers   the receiving components
 */
case class SentCommandInfo(
  name:        String,
  description: String,
  receivers:   List[OtherComponent]
)

/**
 * Describes a command config received by this component
 *
 * @param name         the name of the command
 * @param description  description of the command (in html format, after markdown processing)
 * @param senders      list of components that send the command
 * @param requirements list of requirements for the command
 * @param requiredArgs list of names of required arguments
 * @param args         describes the command's arguments
 */
case class ReceivedCommandInfo(
  name:         String,
  description:  String,
  senders:      List[OtherComponent],
  requirements: List[String],
  requiredArgs: List[String],
  args:         List[AttributeModel]
)

/**
 * Describes commands the component sends and receives
 *
 * @param description      optional top level description of commands (in html format, after markdown processing)
 * @param commandsReceived a list of commands received be this component
 * @param commandsSent     a list of commands sent by this component
 */
case class Commands(
  description:      String,
  commandsReceived: List[ReceivedCommandInfo],
  commandsSent:     List[SentCommandInfo]
)
