package icd.web.shared

/**
 * Defines the basic model classes matching the icd schema files (in icd/resources).
 */
object IcdModels {

  // --- Size estimates for archiving event parameters --

  val doubleSize = 8

  // Used in case no array dimensions were specified
  val defaultArrayDims: List[Int] = List(4)

  // Can't know string size. Make a guess at the average size...
  val defaultStringSize = 80

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

  // Used to calculate archived data sizes
  val operatingHoursPerNight = 12

  // Cycles Per Year for 1 Hz
  val hzToCpy: Int = 365 * operatingHoursPerNight * 60 * 60

  /**
   * Convert a quantity in bytes to a human-readable string such as "4.0 MB".
   */
  def bytesToString(size: Long): String = {
    if (size == 0L) ""
    else {
      val TB = 1L << 40
      val GB = 1L << 30
      val MB = 1L << 20
      val KB = 1L << 10

      val (value, unit) = {
        if (size >= TB) {
          (size.asInstanceOf[Double] / TB, "TB")
        }
        else if (size >= GB) {
          (size.asInstanceOf[Double] / GB, "GB")
        }
        else if (size >= MB) {
          (size.asInstanceOf[Double] / MB, "MB")
        }
        else if (size >= KB) {
          (size.asInstanceOf[Double] / KB, "KB")
        }
        else {
          (size.asInstanceOf[Double], "B")
        }
      }
      "%.1f %s".format(value, unit)
    }
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
   * @param description      alarm description
   * @param requirements     list of requirements that flow to this alarm
   * @param severityLevels   Severity levels that the alarm can have (besides Disconnected, Indeterminate, Okay)
   * @param location         A text description of where the alarming condition is located
   * @param alarmType        The general category for the alarm one of (Absolute, BitPattern, Calculated, Deviation,
   *                         Discrepancy, Instrument, RateChange, RecipeDriven, Safety, Statistical, System)
   * @param probableCause    The probable cause for each level or for all levels
   * @param operatorResponse Instructions or information to help the operator respond to the alarm
   * @param autoAck          True if this alarm does not require an acknowledge by the operator
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
      autoAck: Boolean,
      latched: Boolean
  ) extends NameDesc

  /**
   * Defines the properties of a parameter
   *
   * @param name             name of the parameter
   * @param ref              if not empty, a reference to another parameter to copy missing values from
   *                         in the form component/section/name/paramSection/paramName (may be abbreviated, if in same scope)
   * @param refError contains an error message if ref is invalid (not stored in the db)
   * @param description      description of the parameter
   * @param maybeType        an optional string describing the type (either this or maybeEnum should be defined)
   * @param maybeEnum        an optional string describing the enum type (either this or maybeType should be defined)
   * @param maybeArrayType   if type is array, this should be the type of the array elements
   * @param maybeDimensions  if type is array, this should be the sizes of each dimension (optional)
   * @param units            description of the value's units
   * @param maxItems         optional max number of values in an array value (in string format)
   * @param minItems         optional min number of values in an array value (in string format)
   * @param minimum          optional max value (in string format)
   * @param maximum          optional min value (in string format)
   * @param exclusiveMinimum true if the min value in exclusive
   * @param exclusiveMaximum true if the max value in exclusive
   * @param defaultValue     default value (as a string, which may be empty)
   * @param typeStr          a generated text description of the type
   * @param keywords         a list of FITS keyword info from the publish-model.conf file for FITS keywords where this
   *                         parameter is the source
   *                         (This will normally be just the keyword name, but could in some cases be more complicated,
   *                         containing the channel or index into the parameter value, in the case of arrays.)
   * @param fitsKeys         a list of FITS keywords (from the FITS-Dictionary.json file) for which this parameter is the source
   *                         (only used if  keywords is empty)
   */
  case class ParameterModel(
      name: String,
      ref: String,
      refError: String,
      description: String,
      maybeType: Option[String],
      maybeEnum: Option[List[String]],
      maybeArrayType: Option[String],
      maybeDimensions: Option[List[Int]],
      units: String,
      maxItems: Option[Int],
      minItems: Option[Int],
      maxLength: Option[Int],
      minLength: Option[Int],
      minimum: Option[String],
      maximum: Option[String],
      exclusiveMinimum: Boolean,
      exclusiveMaximum: Boolean,
      allowNaN: Boolean,
      defaultValue: String,
      typeStr: String,
      keywords: List[EventParameterFitsKeyInfo],
      fitsKeys: List[String]
  ) extends NameDesc {

    /**
     * If keywords is defined (comes from publish-model.conf), return the key names,
     * otherwise the return fitsKeys (comes from the FITS Dictionary)
     */
    def getFitsKeys: List[String] = {
      if (keywords.nonEmpty) keywords.map(_.name) else fitsKeys
    }

    // Estimate size required to archive the value(s) for this parameter
    private def getTypeSize(typeName: String): Int = {
      typeName match {
        case "array" =>
          // Use the given array dimensions, or guess if none given (may not be known ahead of time?)
          val d    = maxItems.map(List(_)).getOrElse(defaultArrayDims)
          val n    = maybeDimensions.getOrElse(d).product
          val size = maybeArrayType.map(getTypeSize).getOrElse(defaultTypeSize)
          n * size + n - 1 // OSWDMS-32: Add 1 byte per array item
        // Assume boolean encoded as 1/0 byte?
        case "boolean" => 1
        case "integer" => 4
        case "number"  => 8
        case "string"  =>
          // Use maxLength if given, or minLength, if greater than the default, otherwise the default length
          maxLength.getOrElse(minLength.map(math.max(defaultStringSize, _)).getOrElse(defaultStringSize))
        case "byte"                => 1
        case "short"               => 2
        case "long"                => 4
        case "float"               => 4
        case "double"              => 8
        case "taiDate" | "taiTime" => 12
        case "utcDate" | "utcTime" => 12
        case "raDec"               => 16
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
     * Calculated (or estimated, worst case) total size in bytes for this parameter (for archiving)
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
   * Defines the properties of a metadata
   *
   * @param name             name of the metadata
   * @param description      description of the metadata
   * @param dataType         data type of FITS keyword values (float, double, integer, short, long, byte)
   * @param keyword          FITS keyword (example: ITIME, SCALE)
   */
  case class MetadataModel(
      name: String,
      description: String,
      dataType: String,
      keyword: String
  ) extends NameDesc

  /**
   * Model for the result of a received command
   *
   * @param description   description of the result
   * @param parameters    the fields in the result
   */
  case class CommandResultModel(
      description: String,
      parameters: List[ParameterModel]
  )

  /**
   * Model for a commands configuration that a component receives
   *
   * @param name           command name
   * @param ref            if not empty, a reference to another command in the
   *                       form component/receive/name
   * @param refError       contains an error message if ref is invalid (not stored in db)
   * @param description    command desc
   * @param requirements   an array of requirement ids
   * @param preconditions  an array of preconditions
   * @param postconditions an array of postconditions
   * @param requiredArgs   list of names of required args
   * @param completionType Indicates the completion type of a command: See CSW CommandService API for details
   * @param resultType     Defines an array of parameters in the result (For commands that return a result)
   * @param completionConditions  For oneway commands, describes the conditions for determining command completion (if applicable)
   * @param role   The required user role/authorization for the command ("eng", "admin" or "user" (default))
   */
  case class ReceiveCommandModel(
      name: String,
      ref: String,
      refError: String,
      description: String,
      requirements: List[String],
      preconditions: List[String],
      postconditions: List[String],
      requiredArgs: List[String],
      parameters: List[ParameterModel],
      completionType: String,
      maybeResult: Option[CommandResultModel],
      completionConditions: List[String],
      role: Option[String]
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
   * Model for a single diagnostic mode
   * @param hint name of the diag mode (hint param in the csw API)
   * @param description description of what the component does when it receives the command to enter this diag mode
   */
  case class DiagnosticMode(hint: String, description: String) extends NameDesc {
    override val name: String = hint
  }

  /**
   * Model for commands received and sent by component: See resources/command-schema.conf
   */
  case class CommandModel(
      subsystem: String,
      component: String,
      description: String,
      receive: List[ReceiveCommandModel],
      send: List[SendCommandModel],
      diagnosticModes: List[DiagnosticMode]
  )

  /**
   * The basic component model
   */
  case class ComponentModel(
      componentType: String,
      subsystem: String,
      component: String,
      title: String,
      description: String,
      modelVersion: String,
      wbsId: String,
      maybeSubsystemVersion: Option[String] = None
  ) {
    // Changed to enforce prefix = subsystem.component as in CSW
    val prefix = s"$subsystem.$component"
  }

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
      imageList: List[ImageModel],
      // For backward compatibility: Now the alarms are in a separate model file
      alarmList: List[AlarmModel]
  )

  /**
   * The component's alarm models
   */
  case class AlarmsModel(
      subsystem: String,
      component: String,
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
   */
  case class SubscribeModel(
      subsystem: String,
      component: String,
      description: String,
      eventList: List[SubscribeModelInfo],
      observeEventList: List[SubscribeModelInfo],
      currentStateList: List[SubscribeModelInfo],
      imageList: List[SubscribeModelInfo]
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

  /**
   * Describes a path or route in an HTTP service
   * @param method the HTTP method (POST, GET, etc.)
   * @param path the path / route
   * @param description short description of what the path / route does
   */
  case class ServicePath(method: String, path: String, description: String)

  /**
   * Describes an HTTP service provided by this subsystem
   * @param name name of the service
   * @param description short description of the service
   * @param openApi originally holds the name of file containing OpenApi description of the service
   *                (When read from the icd database this field then holds the contents of the OpenApi JSON file.)
   * @param paths list of routes/paths provided by the service
   */
  case class ServiceModelProvider(name: String, description: String, openApi: String, paths: List[ServicePath])

  /**
   * A reference to an HTTP service required by this subsystem
   * @param subsystem the subsystem for the component providing the HTTP service
   * @param component the component providing the HTTP service
   * @param name the name of the service provided
   * @param paths list of routes/paths used (empty means; all paths used)
   */
  case class ServiceModelClient(subsystem: String, component: String, name: String, paths: List[ServicePath])
      extends SubsystemComponentName

  /**
   * Contains a client component model and the list of service paths used
   * @param component the component using the service
   * @param paths the paths of the service used by the component (if empty, assume all paths)
   */
  case class ServiceModelClientComponent(component: ComponentModel, paths: List[ServicePath])

  /**
   * Lists the HTTP services provided or required by the subsystem component
   *
   * @param subsystem this subsystem
   * @param component this component
   * @param description an optional description for the services used or provided
   * @param provides HTTP services provided
   * @param requires HTTP services required
   */
  case class ServiceModel(
      subsystem: String,
      component: String,
      description: String,
      provides: List[ServiceModelProvider],
      requires: List[ServiceModelClient]
  )

  /**
   * Model for file that contains a description of the ICD between the subsystem and targetSubsystem.
   */
  case class IcdModel(
      subsystem: String,
      targetSubsystem: String,
      title: String,
      description: String
  ) {
    val titleStr: String =
      if (title.nonEmpty) title
      else s"About the ICD between $subsystem and $targetSubsystem"
  }

  object EventModel {
    // Use 1hz if maxRate is not defined and display the result in italics
    val defaultMaxRate       = 1.0
    val eventCategories      = List("DEMAND", "CONTROL", "EVENT", "STATUS")
    val defaultEventCategory = "STATUS"

    // Returns a pair of (maxRate, defaultUsed), where defaultUsed is true if maxRate is None or 0
    def getMaxRate(rate: Option[Double]): (Double, Boolean) = {
      val v = rate.getOrElse(defaultMaxRate)
      if (rate.isEmpty || v == 0) (defaultMaxRate, true) else (v, false)
    }

    // Returns a string describing the total archive space for a year for all of the given event models
    def getTotalArchiveSpace(models: List[EventModel]): String = {
      bytesToString(models.filter(_.archive).map(_.totalArchiveBytesPerYear).sum)
    }

    // Returns a string describing the total archive space for an hour for all of the given event models
    def getTotalArchiveSpaceHourly(models: List[EventModel]): String = {
      bytesToString(models.filter(_.archive).map(_.totalArchiveBytesPerYear / (365 * operatingHoursPerNight)).sum)
    }
  }

  /**
   * Models the event published by a component
   *
   * @param name event name
   * @param category event category (one of DEMAND, CONTROL, EVENT, STATUS, default STATUS)
   * @param ref if not empty, a reference to another event model in the
   *            form component/events/name, component/observeEvents/name, etc (may be abbreviated if in same component/section)
   * @param refError contains an error message if ref is invalid (not stored in the db)
   * @param description event description
   * @param requirements list of requirements that flow to this item
   * @param maybeMaxRate optional maximum rate of publishing in Hz
   * @param archive true if publisher recommends archiving this event
   * @param archiveDuration lifetime of the archiving (example: '2 years', '6 months'): Required if archive is true.
   * @param parameterList parameters for the event
   * @param diagnosticModes the event is only fired if the component is in one of the given diagnostic modes
   */
  case class EventModel(
      name: String,
      category: String,
      ref: String,
      refError: String,
      description: String,
      requirements: List[String],
      maybeMaxRate: Option[Double],
      archive: Boolean,
      archiveDuration: String,
      parameterList: List[ParameterModel],
      diagnosticModes: List[String]
  ) extends NameDesc {
    import EventModel.*

    def getCategory: String = if (category.isEmpty) defaultEventCategory else category

    // Estimate of overhead size for any csw event (without the paramset)
    def eventOverhead: Int = 167 + name.length

    // Estimated size in bytes of this event
    lazy val totalSizeInBytes: Int = eventOverhead + parameterList.map(p => 48 + p.name.length + p.totalSizeInBytes).sum

    // Estimated number of bytes to archive this event at the maxRate for a year
    lazy val totalArchiveBytesPerYear: Long = {
      if (diagnosticModes.nonEmpty) 0L else {
        val (maxRate, _) = getMaxRate(maybeMaxRate)
        math.round(totalSizeInBytes * maxRate * hzToCpy)
      }
    }

    // String describing estimated space required per hour to archive this event (if archive is true)
    lazy val totalArchiveSpacePerHour: String =
      if (archive) bytesToString(totalArchiveBytesPerYear / (365 * operatingHoursPerNight)) else ""

    // String describing estimated space required per year to archive this event (if archive is true)
    lazy val totalArchiveSpacePerYear: String = if (archive) bytesToString(totalArchiveBytesPerYear) else ""
  }

  /**
   * Models the event published by a component
   *
   * @param name event name
   * @param description event description
   * @param channel the image channel name
   * @param format format (For example: "FITS")
   * @param size image dimensions
   * @param pixelSize bytes per pixel
   * @param maybeMaxRate maximum rate the image is published
   * @param metadataList list of image metadata (FITS keywords)
   */
  case class ImageModel(
      name: String,
      description: String,
      channel: String,
      format: String,
      size: (Int, Int),
      pixelSize: Int,
      maybeMaxRate: Option[Double],
      metadataList: List[MetadataModel]
  ) extends NameDesc
}

import IcdModels.*

/**
 * Holds the set of models associated with the set of standard ICD files
 * (the files found in each directory of an ICD definition. Each file is optional).
 * See icd/resources/ for the related schema files.
 */
case class IcdModels(
    subsystemModel: Option[SubsystemModel],
    componentModel: Option[ComponentModel],
    publishModel: Option[PublishModel],
    subscribeModel: Option[SubscribeModel],
    commandModel: Option[CommandModel],
    alarmsModel: Option[AlarmsModel],
    serviceModel: Option[ServiceModel],
    icdModels: List[IcdModel]
)
