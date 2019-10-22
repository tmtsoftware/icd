package icd.web.shared

/**
 * Holds the set of models associated with the set of standard ICD files
 * (the files found in each directory of an ICD definition. Each file is optional).
 * See icd/resources/ for the related schema files.
 */
trait IcdModels {

  import IcdModels._

  val subsystemModel: Option[SubsystemModel]
  val componentModel: Option[ComponentModel]
  val publishModel: Option[PublishModel]
  val subscribeModel: Option[SubscribeModel]
  val commandModel: Option[CommandModel]
}

/**
 * Defines the basic model classes matching the icd schema files (in icd/resources).
 */
object IcdModels {

  /**
   * Contains the common component and subsystem names that are in the top level model objects
   *
   * @param subsystem component's subsystem name
   * @param component component name
   */
  case class BaseModel(subsystem: String, component: String)

  /**
   * Common trait of items with a name and description.
   */
  trait NameDesc {
    val name: String
    val description: String
  }

  /**
   * Common trait of items with a subsystem, component and name.
   */
  trait SubsystemComponentName {
    val subsystem: String
    val component: String
    val name: String
  }

  /**
   * Description of an alarm
   *
   * @param name             alarm name
   * @param description      alarm descrption
   * @param requirements     list of requirements that flow to this alarm
   * @param severityLevels   Severity levels that the alarm can have (besides Disconnected, Indeterminate, Okay)
   * @param location         A text description of where the alarming condition is located
   * @param alarmType        The general category for the alarm one of (Absolute, BitPattern, Calculated, Deviation,
   *                         Discrepancy, Instrument, RateChange, RecipeDriven, Safety, Statistical, System)
   * @param probableCause    The probable cause for each level or for all levels
   * @param operatorResponse Instructions or information to help the operator respond to the alarm
   * @param acknowledge      Does this alarm require an acknowledge by the operator?
   * @param latched          Should this alarm be latched?
   */
  case class AlarmModel(
      name: String,
      description: String,
      requirements: List[String],
      severityLevels: List[String],
      location: String,
      alarmType: String,
      probableCause: String,
      operatorResponse: String,
      acknowledge: Boolean,
      latched: Boolean
  ) extends NameDesc

  /**
   * Defines the properties of an attribute
   *
   * @param name             name of the attribute
   * @param description      description of the attribute
   * @param maybeType        an optional string describing the type (either this or maybeEnum should be defined)
   * @param maybeEnum        an optional string describing the enum type (either this or maybeType should be defined)
   * @param units            description of the value's units
   * @param maxItems         optional max number of values in an array value (in string format)
   * @param minItems         optional min number of values in an array value (in string format)
   * @param minimum          optional max value (in string format)
   * @param maximum          optional min value (in string format)
   * @param exclusiveMinimum true if the min value in exclusive
   * @param exclusiveMaximum true if the max value in exclusive
   * @param defaultValue     default value (as a string, which may be empty)
   * @param typeStr          a generated text description of the type
   */
  case class AttributeModel(
      name: String,
      description: String,
      maybeType: Option[String],
      maybeEnum: Option[List[String]],
      units: String,
      maxItems: Option[String],
      minItems: Option[String],
      minimum: Option[String],
      maximum: Option[String],
      exclusiveMinimum: Boolean,
      exclusiveMaximum: Boolean,
      defaultValue: String,
      typeStr: String,
      attributesList: List[AttributeModel]
  ) extends NameDesc

  /**
   * Model for a commands configuration that a component receives
   *
   * @param name           command name
   * @param description    command desc
   * @param requirements   an array of requirement ids
   * @param preconditions  an array of preconditions
   * @param postconditions an array of postconditions
   * @param requiredArgs   list of names of required args
   * @param args           describes the command argumemnts (configuration fields)
   */
  case class ReceiveCommandModel(
      name: String,
      description: String,
      requirements: List[String],
      preconditions: List[String],
      postconditions: List[String],
      requiredArgs: List[String],
      args: List[AttributeModel],
      completionType: String,
      resultType: List[AttributeModel],
      completionConditions: List[String]
  ) extends NameDesc

  /**
   * Describes command configurations that the component sends
   *
   * @param name      command name
   * @param subsystem the target subsystem
   * @param component the target component
   */
  case class SendCommandModel(
      name: String,
      subsystem: String,
      component: String
  ) extends SubsystemComponentName

  /**
   * Model for commands received and sent by component: See resources/command-schema.conf
   */
  case class CommandModel(
      subsystem: String,
      component: String,
      description: String,
      receive: List[ReceiveCommandModel],
      send: List[SendCommandModel]
  )

  /**
   * The basic component model
   */
  case class ComponentModel(
      componentType: String,
      subsystem: String,
      component: String,
      prefix: String,
      title: String,
      description: String,
      modelVersion: String,
      wbsId: String
  )

  /**
   * The component's publish model
   */
  case class PublishModel(
      subsystem: String,
      component: String,
      description: String,
      eventList: List[EventModel],
      observeEventList: List[EventModel],
      currentStateList: List[EventModel],
      alarmList: List[AlarmModel]
  )

  /**
   * Describes the items a component subscribes to
   *
   * @param subsystem        the component's subsystem
   * @param component        the component
   * @param description      a top level description of the subscribed items
   * @param eventList        list of subscribed events
   * @param observeEventList list of subscribed observe events
   * @param alarmList        list of subscribed alarms
   */
  case class SubscribeModel(
      subsystem: String,
      component: String,
      description: String,
      eventList: List[SubscribeModelInfo],
      observeEventList: List[SubscribeModelInfo],
      currentStateList: List[SubscribeModelInfo],
      alarmList: List[SubscribeModelInfo]
  )

  /**
   * Describes an item the component subscribes to
   *
   * @param subsystem    the publisher's subsystem
   * @param component    the publisher's component
   * @param name         the name of the published item
   * @param usage        describes how the item is used by the subscriber
   * @param requiredRate the required rate
   * @param maxRate      the max rate
   */
  case class SubscribeModelInfo(
      subsystem: String,
      component: String,
      name: String,
      usage: String,
      requiredRate: Double,
      maxRate: Double
  ) extends SubsystemComponentName

  /**
   * Models the component's subsystem
   */
  case class SubsystemModel(
      subsystem: String,
      title: String,
      description: String,
      modelVersion: String
  )

  /**
   * Models the event published by a component
   */
  case class EventModel(
      name: String,
      description: String,
      requirements: List[String],
      minRate: Double,
      maxRate: Double,
      archive: Boolean,
      archiveDuration: String,
      archiveRate: Double,
      attributesList: List[AttributeModel]
  ) extends NameDesc

}
