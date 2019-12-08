package icd.web.shared

import java.util.Locale

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

  // --- Size estimates for archiving event attributes --

  val doubleSize = 8

  // Used in case no array dimensions were specified
  val defaultArrayDims = List(4)

  // Size in bytes used when no type was found
  val defaultTypeSize = 4

  // For csw coord types, max length of tag name (TODO: Count bytes or 2-byte chars?)
  val tagSize = 7

  // Guess average catalog name length
  val catalogNameSize = 16

  // Size of CSW Angle class
  val angleSize = 16

  // Size of CSW EqFrame instance
  val eqFrameSize = 4

  // Size of CSW ProperMotion instance
  val properMotionSize = 16

  // Max length of CSW SolarSystemObject name
  val solarSystemObjectSize = 7

  // Cycles Per Year for 1 Hz
  val hzToCpy = 31536000

  /**
   * Convert a quantity in bytes to a human-readable string such as "4.0 MB".
   */
  private def bytesToString(size: Long): String = {
    val TB = 1L << 40
    val GB = 1L << 30
    val MB = 1L << 20
    val KB = 1L << 10

    val (value, unit) = {
      if (size >= TB) {
        (size.asInstanceOf[Double] / TB, "TB")
      } else if (size >= GB) {
        (size.asInstanceOf[Double] / GB, "GB")
      } else if (size >= MB) {
        (size.asInstanceOf[Double] / MB, "MB")
      } else if (size >= KB) {
        (size.asInstanceOf[Double] / KB, "KB")
      } else {
        (size.asInstanceOf[Double], "B")
      }
    }
    "%.1f %s".format(value, unit)
  }

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
      maybeArrayType: Option[String],
      maybeDimensions: Option[List[Int]],
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
  ) extends NameDesc {

    // Estimate size required to archive the value(s) for this attribute
    private def getTypeSize(typeName: String): Int = {
      typeName match {
        case "array" =>
          // Use the given array dimensions, or guess if none given (may not be known ahead of time?)
          maybeDimensions.getOrElse(defaultArrayDims).product * maybeArrayType.map(getTypeSize).getOrElse(defaultTypeSize)
        case "struct" => attributesList.map(_.totalSizeInBytes).sum
        // Assume boolean encoded as 1/0 byte?
        case "boolean" => 1
        case "integer" => 4
        case "number"  => 8
        // Can't know string size. Make a guess at the average size...
        case "string"  => 16
        case "byte"    => 1
        case "short"   => 2
        case "long"    => 4
        case "float"   => 4
        case "double"  => 8
        case "taiDate" => 16
        case "utcDate" => 16
        case "raDec"   => 16
        //  EqCoord(tag: Tag, ra: Angle, dec: Angle, frame: EqFrame, catalogName: String, pm: ProperMotion)
        case "eqCoord" => tagSize + 2 * angleSize + eqFrameSize + catalogNameSize + properMotionSize
        // SolarSystemCoord(tag: Tag, body: SolarSystemObject)
        case "coord" | "solarSystemCoord" => tagSize + solarSystemObjectSize
        //  case class MinorPlanetCoord(
        //      tag: Tag,
        //      epoch: Double,            // TT as a Modified Julian Date
        //      inclination: Angle,       // degrees
        //      longAscendingNode: Angle, // degrees
        //      argOfPerihelion: Angle,   // degrees
        //      meanDistance: Double,     // AU
        //      eccentricity: Double,
        //      meanAnomaly: Angle // degrees
        //  ) extends Coord
        case "minorPlanetCoord" => tagSize + 3 * doubleSize + 4 * angleSize
        //  case class CometCoord(
        //      tag: Tag,
        //      epochOfPerihelion: Double,  // TT as a Modified Julian Date
        //      inclination: Angle,         // degrees
        //      longAscendingNode: Angle,   // degrees
        //      argOfPerihelion: Angle,     // degrees
        //      perihelionDistance: Double, // AU
        //      eccentricity: Double
        //  ) extends Coord
        case "cometCoord" => tagSize + 3 * doubleSize + 3 * angleSize
        //  case class AltAzCoord(tag: Tag, alt: Angle, az: Angle) extends Coord {
        case "altAzCoord" => tagSize + 2 * angleSize
        case _            => defaultTypeSize
      }
    }

    /**
     * Calculated (or estimated, worst case) total size in bytes for this attribute (for archiving)
     */
    lazy val totalSizeInBytes: Int = {
      maybeType match {
        case Some(typeName) =>
          getTypeSize(typeName)
        case None =>
          maybeEnum match {
            // Take length of longest enum as worst case
            case Some(enums) => enums.map(_.length).max
            case _           => 0
          }
      }
    }
  }

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
      maxRate: Option[Double]
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

  object EventModel {
    // Use 1hz if maxRate is not defined and display the result in italics
    val defaultMaxRate = 1.0
  }

  /**
   * Models the event published by a component
   */
  case class EventModel(
      name: String,
      description: String,
      requirements: List[String],
      maybeMaxRate: Option[Double],
      archive: Boolean,
      archiveDuration: String,
      attributesList: List[AttributeModel]
  ) extends NameDesc {
    import EventModel._

    // Estimated size in bytes of this event
    lazy val totalSizeInBytes: Int = name.length + attributesList.map(_.totalSizeInBytes).sum

    // Estimated number of bytes to archive this event at the maxRate for a year
    lazy val totalArchiveBytesPerYear: Long = math.round(totalSizeInBytes * maybeMaxRate.getOrElse(defaultMaxRate) * hzToCpy)

    // String describing estimated space required per year to archive this event (if archive is true)
    lazy val totalArchiveSpacePerYear: String = if (archive) bytesToString(totalArchiveBytesPerYear) else ""
  }

}
