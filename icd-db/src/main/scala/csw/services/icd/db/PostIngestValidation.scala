package csw.services.icd.db

import csw.services.icd.Problem
import csw.services.icd.fits.IcdFits
import icd.web.shared.IcdModels.{DiagnosticMode, EventModel, ImageModel, ReceiveCommandModel}
import icd.web.shared.{IcdModels, SubsystemWithVersion}
import play.api.libs.json.{JsArray, JsNumber, JsValue, Json}

import java.io.File
import PostIngestValidation.*

/**
 * Helper class used for additional validation checks made after ingesting icd model files into the database
 * @param db IcdDb instance
 */
class PostIngestValidation(db: IcdDb) {
  private val versionManager = db.versionManager

  // Check that default parameter value is valid for declared type
  // and return Some(errorMessage) if there is an error.
  private def checkDefaultParamValue(p: IcdModels.ParameterModel): Option[String] = {
    lazy val msg = s"In parameter ${p.name}, defaultValue ${p.defaultValue} is invalid"

    // Check that the default value for the param is in the declared range
    def checkRange(defaultValue: String): Option[String] = {
      val value = strToDouble(defaultValue)
      val min   = p.minimum.map(strToDouble).getOrElse(Double.MinValue)
      val max   = p.maximum.map(strToDouble).getOrElse(Double.MaxValue)
      val minOk = if (p.exclusiveMinimum) value > min else value >= min
      val maxOk = if (p.exclusiveMaximum) value < max else value <= max
      if (minOk && maxOk)
        None
      else
        Some(s"$msg: Value $defaultValue is out of declared range for type ${p.typeStr}")
    }

    def checkPrimitiveType(`type`: String, defaultValue: String): Option[String] = {
      `type` match {
        case "boolean" =>
          if (defaultValue.toBooleanOption.isEmpty) {
            Some(s"$msg (Should be one of: true, false)")
          }
          else {
            None
          }
        case "integer" =>
          if (defaultValue.toIntOption.isEmpty) {
            Some(s"$msg (Should be an integer value)")
          }
          else {
            checkRange(defaultValue)
          }
        case "byte" =>
          if (defaultValue.toByteOption.isEmpty) {
            Some(s"$msg (Should be a byte value)")
          }
          else {
            checkRange(defaultValue)
          }
        case "short" =>
          if (defaultValue.toShortOption.isEmpty) {
            Some(s"$msg (Should be a short value)")
          }
          else {
            checkRange(defaultValue)
          }
        case "long" =>
          if (defaultValue.toLongOption.isEmpty) {
            Some(s"$msg (Should be a long value)")
          }
          else {
            checkRange(defaultValue)
          }
        case "float" =>
          if (defaultValue.toFloatOption.isEmpty) {
            Some(s"$msg (Should be a float value)")
          }
          else {
            checkRange(defaultValue)
          }
        case "double" =>
          if (defaultValue.toDoubleOption.isEmpty) {
            Some(s"$msg (Should be a double value)")
          }
          else {
            checkRange(defaultValue)
          }
        case _ => None
      }
    }

    // Check default value element types for array and return Some error message if there is a problem
    def checkArrayType(itemType: String): Option[String] = {
      def checkJsValue(jsValue: JsValue): Option[String] =
        jsValue match {
          case JsNumber(value) =>
            checkPrimitiveType(itemType, value.toString())
          case JsArray(values) =>
            values.flatMap(checkJsValue).headOption
          case _ =>
            None
        }
      if (p.defaultValue.startsWith("[")) {
        val jsValues = Json.parse(p.defaultValue).as[List[JsValue]]
        if (p.maybeDimensions.isDefined && p.maybeDimensions.get.head != jsValues.length) {
          Some(s"$msg: Wrong top level array dimensions: ${jsValues.length}, expected ${p.maybeDimensions.get.head}")
        }
        else {
          val list = jsValues.map(checkJsValue)
          list.flatten.headOption
        }
      }
      else {
        Some(s"$msg: Default array value should start with '['")
      }
    }

    if (p.defaultValue.isEmpty) {
      None
    }
    else {
      // Check that defaultValue is one of the allowed enum values
      if (p.maybeEnum.isDefined) {
        if (p.maybeEnum.get.contains(p.defaultValue))
          None
        else {
          val choices = p.maybeEnum.get.mkString(", ")
          Some(s"$msg (Should be one of: $choices)")
        }
      }
      else if (p.maybeType.isDefined) {
        // Check that defaultValue has declared type
        p.maybeType.get match {
          case "string" =>
            val s      = p.defaultValue
            val minLen = p.minLength.getOrElse(0)
            val maxLen = p.maxLength.getOrElse(Int.MaxValue)
            if (s.length < minLen)
              Some(s"$msg (min length is $minLen)")
            else if (s.length > maxLen)
              Some(s"$msg (max length is $maxLen)")
            else
              None
          case "array" =>
            p.maybeArrayType.flatMap(checkArrayType)
          case x =>
            checkPrimitiveType(x, p.defaultValue)
        }
      }
      else {
        None
      }
    }
  }

  private def strToDouble(s: String): Double = {
    s match {
      case "inf" | "Inf"   => Double.MaxValue
      case "-inf" | "-Inf" => Double.MinValue
      case _               => s.toDoubleOption.getOrElse(0.0)
    }
  }

  // Check for min > max and defaultValue in parameter fields and return list of error messages for the parameter list
  private def getParamMinMaxProblems(parameterList: List[IcdModels.ParameterModel]): List[String] = {
    val list = parameterList.flatMap { p =>
      val a =
        if (
          p.maximum.isDefined && p.minimum.isDefined
          && strToDouble(p.maximum.get) < strToDouble(p.minimum.get)
        )
          Some(s"In parameter ${p.name}, maximum (${p.maximum.get}) < minimum (${p.minimum.get})")
        else None
      val b =
        if (p.maxLength.isDefined && p.minLength.isDefined && p.maxLength.get < p.minLength.get)
          Some(s"In parameter ${p.name}, maxLength (${p.maxLength.get}) < minLength (${p.minLength.get})")
        else None
      val c =
        if (p.maxItems.isDefined && p.minItems.isDefined && p.maxItems.get < p.minItems.get)
          Some(s"In parameter ${p.name}, maxItems (${p.maxItems.get}) < minItems (${p.minItems.get})")
        else None
      val d = checkDefaultParamValue(p)
      List(a, b, c, d)
    }
    list.flatten
  }

  // Check for min > max in event parameter fields
  private def getMinMaxEventProblems(prefix: String, events: List[EventModel], name: String): List[Problem] = {
    events.flatMap(event =>
      getParamMinMaxProblems(event.parameterList)
        .map(s => Problem("error", s"'$s' in $name '$prefix.${event.name}''"))
    )
  }

  // Check for min > max in command parameter fields
  private def getMinMaxCommandProblems(prefix: String, commands: List[ReceiveCommandModel]): List[Problem] = {
    commands.flatMap(command =>
      getParamMinMaxProblems(command.parameters)
        .map(s => Problem("error", s"'$s' in command '$prefix.${command.name}''"))
    )
  }

  private def getEventProblems(prefix: String, events: List[EventModel], name: String): List[Problem] = {
    val p1 = getDuplicates(events.map(_.name)).map(s => Problem("error", s"Duplicate $name name: '$s''"))
    val p2 = events.flatMap(event =>
      getDuplicates(event.parameterList.map(_.name))
        .map(s => Problem("error", s"Duplicate parameter name '$s' in $name '$prefix.${event.name}''"))
    )
    p1 ::: p2
  }

  private def getEventDiagnosticModeProblems(
      prefix: String,
      events: List[EventModel],
      diagnosticModes: List[DiagnosticMode]
  ): List[Problem] = {
    events
      .filter(_.diagnosticModes.nonEmpty)
      .flatMap(_.diagnosticModes)
      .flatMap { eventDiagMode =>
        if (!diagnosticModes.map(_.hint).contains(eventDiagMode))
          List(Problem("error", s"In $prefix, diagnostic mode $eventDiagMode is not defined"))
        else
          Nil
      }
  }

  private def getImageProblems(prefix: String, images: List[ImageModel]): List[Problem] = {
    val p1 = getDuplicates(images.map(_.name)).map(s => Problem("error", s"Duplicate image name: '$s''"))
    val p2 = images.flatMap(image =>
      getDuplicates(image.metadataList.map(_.name))
        .map(s => Problem("error", s"Duplicate metadata name '$s' in image: '$prefix.${image.name}''"))
    )
    p1 ::: p2
  }

  private def getCommandProblems(prefix: String, commands: List[ReceiveCommandModel]): List[Problem] = {
    // Check that requiredArgs for commands are defined
    def checkRequiredArgs(command: ReceiveCommandModel): List[Problem] = {
      command.requiredArgs.flatMap(arg =>
        if (command.parameters.map(_.name).contains(arg)) None
        else Some(Problem("warning", s"requiredArg '$arg' is not defined for command ${command.name} in $prefix"))
      )
    }

    val p1 = getDuplicates(commands.map(_.name)).map(s => Problem("error", s"Duplicate command name: '$s''"))
    val p2 = commands.flatMap(command =>
      getDuplicates(command.parameters.map(_.name))
        .map(s => Problem("error", s"Duplicate parameter name '$s' in command '$prefix.${command.name}''"))
    )
    val p3 = commands.flatMap(command => checkRequiredArgs(command))
    p1 ::: p2 ::: p3
  }

  private def checkDuplicateKeywordChannelPair(models: List[IcdModels]): List[Problem] = {
    val keywordChannelList = for {
      icdModels    <- models
      publishModel <- icdModels.publishModel.toList
      events       <- publishModel.eventList
      params       <- events.parameterList
      keyword      <- params.keywords
    } yield {
      (keyword.name, keyword.channel.getOrElse(""))
    }
    // Check for duplicates
    keywordChannelList
      .groupBy(identity)
      .collect { case (x, List(_, _, _*)) => x }
      .toList
      .map(p =>
        if (p._2.isEmpty)
          Problem("error", s"Duplicate FITS keyword: ${p._1}")
        else
          Problem("error", s"Duplicate FITS keyword, channel pair: keyword: ${p._1}, channel: ${p._2}")
      )
  }

  private def getFitsKeywordProblems(
      prefix: String,
      events: List[EventModel],
      allowedFitsKeyNames: Set[String],
      allowedChannels: Set[String]
  ): List[Problem] = {
    if (allowedFitsKeyNames.isEmpty) Nil
    else {
      val result = for {
        event <- events
        p     <- event.parameterList
        k     <- p.keywords
      } yield {
        val keyNameProblems =
          if (allowedFitsKeyNames.contains(k.name)) None
          else Some(Problem("error", s"$prefix.${event.name}: ${k.name} is not in the FITS dictionary"))
        val channelNameProblems =
          if (k.channel.isEmpty || allowedChannels.contains(k.channel.get)) None
          else {
            val msg1 =
              s"Event: $prefix.${event.name}, parameter: ${p.name}, FITS keyword: ${k.name}: Channel ${k.channel.get} is not defined.  "
            val msg2 = if (allowedChannels.nonEmpty) s"Should be one of: ${allowedChannels.mkString(", ")}" else ""
            Some(Problem("error", msg1 + msg2))
          }
        keyNameProblems.toList ::: channelNameProblems.toList
      }
      result.flatten
    }
  }

  /**
   * Check for obvious errors, such as duplicate event or parameter names after ingesting model files for subsystem
   * @param subsystem the subsystem that was ingested
   * @return list of problems
   */
  def checkPostIngest(subsystem: String): List[Problem] = {

    val icdFits                         = IcdFits(db)
    val allowedFitsKeyNames             = icdFits.getFitsKeyInfo(None).map(_.name).toSet
    val availableFitsKeyChannels        = icdFits.getFitsChannels.map(c => c.subsystem -> c.channels).toMap
    val sv                              = SubsystemWithVersion(subsystem, None, None)
    val models                          = versionManager.getResolvedModels(sv, None, Map.empty)
    val duplicateKeywordChannelProblems = checkDuplicateKeywordChannelPair(models)
    val problems = models.flatMap { icdModels =>
      val publishProblems = icdModels.publishModel.toList.flatMap { publishModel =>
        val prefix          = s"${publishModel.subsystem}.${publishModel.component}"
        val allowedChannels = availableFitsKeyChannels.get(publishModel.subsystem).toList.flatten.toSet
        getMinMaxEventProblems(prefix, publishModel.eventList, "event") :::
        getMinMaxEventProblems(prefix, publishModel.currentStateList, "current state") :::
        getEventProblems(prefix, publishModel.eventList, "event") :::
        getEventDiagnosticModeProblems(prefix, publishModel.eventList, icdModels.commandModel.toList.flatMap(_.diagnosticModes)) :::
        getFitsKeywordProblems(prefix, publishModel.eventList, allowedFitsKeyNames, allowedChannels) :::
        getEventProblems(prefix, publishModel.currentStateList, "current state") :::
        getImageProblems(prefix, publishModel.imageList)
      }
      val commandProblems = icdModels.commandModel.toList.flatMap { commandModel =>
        val prefix = s"${commandModel.subsystem}.${commandModel.component}"
        getMinMaxCommandProblems(prefix, commandModel.receive) :::
        getCommandProblems(prefix, commandModel.receive)
      }
      publishProblems ::: commandProblems
    }
    val fitsDictProblems =
      if (subsystem == "DMS")
        icdFits.postIngestValidateFitsDictionary()
      else Nil
    duplicateKeywordChannelProblems ::: problems ::: fitsDictProblems
  }
}

object PostIngestValidation {
  // Returns list of duplicates in given list
  private def getDuplicates(list: List[String]) = list.groupBy(identity).collect { case (x, List(_, _, _*)) => x }.toList

  // Check for duplicate component names
  private def checkForDuplicateComponentNames(list: List[StdConfig]): List[String] = {
    val components = list.flatMap {
      case x if x.stdName.isComponentModel =>
        val subsystem = x.config.getString("subsystem")
        val component = x.config.getString("component")
        Some(s"$subsystem.$component")
      case _ => None
    }
    getDuplicates(components)
  }

  // Check for misspelled subsystem or component names
  private def checkForWrongComponentNames(list: List[StdConfig]): List[String] = {
    // get pairs of (dir -> subsystem.component) and check if there are directories that contain
    // multiple different values (should all be the same)
    val pairs = list.flatMap {
      case x if x.stdName.hasComponent =>
        val dir       = new File(x.fileName).getParent
        val subsystem = x.config.getString("subsystem")
        val component = x.config.getString("component")
        Some(dir -> s"$subsystem.$component")
      case _ => None
    }
    val map = pairs.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
    map.values.toList.map(_.distinct).filter(_.size != 1).map(_.mkString(" != "))
  }

  /**
   * Check for duplicate subsystem-model.conf file (found one in TCS) and component names
   * @param configs list of objects, one for each model file
   * @param subsystemList list of subsystems involved
   * @return list of problems
   */
  def checkComponents(configs: List[StdConfig], subsystemList: List[String]): List[Problem] = {
    val duplicateSubsystems = subsystemList.diff(subsystemList.toSet.toList)
    val duplicateComponents = checkForDuplicateComponentNames(configs)
    val wrongComponents     = checkForWrongComponentNames(configs)
    val duplicateSubsystemProblems =
      if (duplicateSubsystems.nonEmpty)
        List(Problem("error", s"Duplicate subsystem-model.conf found: ${duplicateSubsystems.mkString(", ")}"))
      else Nil

    val duplicateComponentProblems =
      if (duplicateComponents.nonEmpty)
        List(Problem("error", s"Duplicate component names found: ${duplicateComponents.mkString(", ")}"))
      else Nil

    val wrongComponentProblems =
      if (wrongComponents.nonEmpty)
        List(Problem("error", s"Conflicting component names found: ${wrongComponents.mkString(", ")}"))
      else Nil

    duplicateSubsystemProblems ::: duplicateComponentProblems ::: wrongComponentProblems
  }
}
