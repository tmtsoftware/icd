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
   * Description of an alarm
   *
   * @param name         alarm name
   * @param description  alarm descrption
   * @param requirements list of requirements that flow to this alarm
   * @param severity     severity of the alarm
   * @param archive      true if publisher recommends archiving this alarm
   */
  case class AlarmModel(
    name:         String,
    description:  String,
    requirements: List[String],
    severity:     String,
    archive:      Boolean
  )

  /**
   * Defines the properties of an attribute
   *
   * @param name             name of the attribute
   * @param description      description of the attribute
   * @param typeOpt          an optional string describing the type (either this or enumOpt should be defined)
   * @param enumOpt          an optional string describing the enum type (either this or typeOpt should be defined)
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
    name:             String,
    description:      String,
    typeOpt:          Option[String],
    enumOpt:          Option[List[String]],
    units:            String,
    maxItems:         Option[String],
    minItems:         Option[String],
    minimum:          Option[String],
    maximum:          Option[String],
    exclusiveMinimum: Boolean,
    exclusiveMaximum: Boolean,
    defaultValue:     String,
    typeStr:          String
  )

  /**
   *
   * @param name
   * @param description
   * @param requirements
   * @param requiredArgs
   * @param args
   */
  case class ReceiveCommandModel(
    name:         String,
    description:  String,
    requirements: List[String],
    requiredArgs: List[String],
    args:         List[AttributeModel]
  )

  /**
   *
   * @param name
   * @param subsystem
   * @param component
   */
  case class SendCommandModel(
    name:      String,
    subsystem: String,
    component: String
  )

  /**
   * Model for commands received and sent by component: See resources/command-schema.conf
   */
  case class CommandModel(
    subsystem:   String,
    component:   String,
    description: String,
    receive:     List[ReceiveCommandModel],
    send:        List[SendCommandModel]
  )

  /**
   *
   * @param componentType
   * @param subsystem
   * @param component
   * @param prefix
   * @param title
   * @param description
   * @param modelVersion
   * @param wbsId
   */
  case class ComponentModel(
    componentType: String,
    subsystem:     String,
    component:     String,
    prefix:        String,
    title:         String,
    description:   String,
    modelVersion:  String,
    wbsId:         String
  )

  /**
   *
   * @param subsystem
   * @param component
   * @param description
   * @param telemetryList
   * @param eventList
   * @param eventStreamList
   * @param alarmList
   */
  case class PublishModel(
    subsystem:       String,
    component:       String,
    description:     String,
    telemetryList:   List[TelemetryModel],
    eventList:       List[TelemetryModel],
    eventStreamList: List[TelemetryModel],
    alarmList:       List[AlarmModel]
  )

  /**
   * Describes the items a component subscribes to
   *
   * @param subsystem       the component's subsystem
   * @param component       the component
   * @param description     a top level description of the subscribed items
   * @param telemetryList   list of subscribed telemetry
   * @param eventList       list of subscribed events
   * @param eventStreamList list of subscribed event streams
   * @param alarmList       list of subscribed alarms
   */
  case class SubscribeModel(
    subsystem:       String,
    component:       String,
    description:     String,
    telemetryList:   List[SubscribeModelInfo],
    eventList:       List[SubscribeModelInfo],
    eventStreamList: List[SubscribeModelInfo],
    alarmList:       List[SubscribeModelInfo]
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
    subsystem:    String,
    component:    String,
    name:         String,
    usage:        String,
    requiredRate: Double,
    maxRate:      Double
  )

  /**
   *
   * @param subsystem
   * @param title
   * @param description
   * @param modelVersion
   */
  case class SubsystemModel(
    subsystem:    String,
    title:        String,
    description:  String,
    modelVersion: String
  )

  /**
   *
   * @param name
   * @param description
   * @param requirements
   * @param minRate
   * @param maxRate
   * @param archive
   * @param archiveRate
   * @param attributesList
   */
  case class TelemetryModel(
    name:           String,
    description:    String,
    requirements:   List[String],
    minRate:        Double,
    maxRate:        Double,
    archive:        Boolean,
    archiveRate:    Double,
    attributesList: List[AttributeModel]
  )
}
