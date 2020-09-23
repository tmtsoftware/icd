package csw.services.icd.db

import icd.web.shared.IcdModels
import icd.web.shared.IcdModels.{ParameterModel, CommandModel, EventModel, PublishModel, ReceiveCommandModel}

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger
import Resolver._

object Resolver {
  private lazy val log: Logger = Logger("csw.services.icd.db.Resolver")
  var loggingEnabled           = true

  private class ResolverException(msg: String) extends RuntimeException(msg)

  private def resolverException(msg: String): ResolverException = {
    val ex = new ResolverException(msg)
    if (loggingEnabled) log.error(msg)
    ex
  }

  /**
   * Holds the basic parts of a ref for an event or command
   * @param ref the original ref string
   * @param component the component name
   * @param section the section (type of event, or receive for commands)
   * @param name name of the event or command
   */
  private case class Ref(ref: String, component: String, section: Ref.Section, name: String)

  private object Ref {
    // Tells where to look for a ref
    sealed trait Section
    object events        extends Section
    object observeEvents extends Section
    object currentStates extends Section
    object receive       extends Section

    object Section {
      def apply(ref: String, s: String): Section = {
        s match {
          case "events"        => events
          case "observeEvents" => observeEvents
          case "currentStates" => currentStates
          case "receive"       => receive
          case x =>
            throw resolverException(
              s"Invalid ref section $x in $ref, expected one of (events, observeEvents, currentStates, receive)"
            )
        }
      }
    }

    /**
     * Returns a Ref object for an event or command ref string in the form:
     *
     * Events:
     *    $componentName/events/$eventName
     * or $componentName/observeEvents/$eventName
     * or $componentName/currentState/$eventName
     * or events/$eventName (in same component)
     * or just $eventName (in same component and section)
     *
     * Commands:
     * or $componentName/receive/$commandName
     * or just $commandName (in same component)
     *
     * Parameters for events, as above, but with param name:
     * $componentName/events/$eventName/parameters/$paramName
     * or abbreviated as above.
     *
     * Parameters for commands: as above, but with additional info:
     * $componentName/receive/$commandName/parameters/$paramName
     * or $componentName/receive/$commandName/resultType/$paramName
     * or abbreviated as above.
     *
     * For backward compatibility, "attributes" or "args" are accepted
     * in place of "parameters" for events, commands, resp.
     *
     * @param ref the ref string
     * @param defaultComponent if component is not specified in the ref string, use this
     * @param defaultSection if section is not given in the ref, use this
     */
    def apply(ref: String, defaultComponent: String, defaultSection: Section): Ref = {
      val parts = ref.split('/')
      parts.length match {
        case 1 =>
          Ref(ref, defaultComponent, defaultSection, parts.head)
        case 2 =>
          Ref(ref, defaultComponent, Section(ref, parts(0)), parts(1))
        case 3 =>
          Ref(ref, parts(0), Section(ref, parts(1)), parts(2))
        case _ =>
          throw resolverException(
            s"Invalid ref '$ref': Expected syntax like: componentName/events/eventName (or abbreviated if in same scope)"
          )
      }
    }
  }

  /**
   * Holds the parts of an parameter reference
   */
  private case class ParamRef(ref: Ref, paramSection: ParamRef.ParamSection, name: String)

  private object ParamRef {
    // Tells where to look for an parameter
    sealed trait ParamSection
    object eventParams   extends ParamSection
    object commandParams extends ParamSection
    object resultType    extends ParamSection

    object ParamSection {
      def apply(ref: Ref, s: String): ParamSection = {
        // For backward compatibility, allow "attributes" or "args" in place of "parameters"
        s match {
          case "parameters" => if (ref.section == Ref.receive) commandParams else eventParams
          case "attributes" => eventParams    // deprecated
          case "  args"       => commandParams  // deprecated
          case "resultType" => resultType
          case x =>
            throw resolverException(
              s"Invalid ref parameter section $x in ${ref.ref}, expected one of (parameters [attributes, args], or resultType)"
            )
        }
      }
    }

    /**
     * Returns an ParamRef object for an event or command parameter ref string in the form:
     *
     * For events:
     * $componentName/events/$eventName/parameters/$paramName
     * or abbreviated if in same scope (see parseRef).
     *
     * For commands:
     * $componentName/receive/$commandName/parameters/$paramName
     * or $componentName/receive/$commandName/resultType/$paramName
     * or abbreviated (See parseRef).
     *
     * @param ref the ref string
     * @param defaultComponent if component is not specified in the ref string, use this
     * @param defaultSection if section is not given in the ref, use this
     * @param defaultName if the name of the event or command is not in the ref, use this
     * @param defaultParamSection if parameter section is not given, use this
     */
    def apply(
        ref: String,
        defaultComponent: String,
        defaultSection: Ref.Section,
        defaultName: String,
        defaultParamSection: ParamSection
    ): ParamRef = {
      val parts = ref.split('/')
      parts.length match {
        case 1 =>
          val r = Ref(ref, defaultComponent, defaultSection, defaultName)
          ParamRef(r, defaultParamSection, parts.head)
        case 2 =>
          val r = Ref(ref, defaultComponent, defaultSection, defaultName)
          ParamRef(r, ParamSection(r, parts(0)), parts(1))
        case 3 =>
          val r = Ref(ref, defaultComponent, defaultSection, parts(0))
          ParamRef(r, ParamSection(r, parts(1)), parts(2))
        case 4 =>
          val r = Ref(ref, defaultComponent, Ref.Section(ref, parts(0)), parts(1))
          ParamRef(r, ParamSection(r, parts(2)), parts(3))
        case 5 =>
          val r = Ref(ref, parts(0), Ref.Section(ref, parts(1)), parts(2))
          ParamRef(r, ParamSection(r, parts(3)), parts(4))
        case _ =>
          throw resolverException(
            s"Invalid parameter ref '$ref': Expected syntax like: componentName/events/eventName/parameters/paramName (or abbreviated if in same scope)"
          )
      }
    }
  }
}

/**
 * A utility class used to resolve "ref" entries in events, commands or parameters.
 * @param allModels list of all models for a subsystem (based on the input model files)
 */
case class Resolver(allModels: List[IcdModels]) {

  /**
   * Resolve any event, command or parameter "ref" items and return the resolved definitions.
   * @return the fully resolved list of models
   */
  def resolve(models: List[IcdModels]): List[IcdModels] = {
    models.map(resolveIcdModels)
  }

  private def resolveIcdModels(models: IcdModels): IcdModels = {
    IcdModels(
      subsystemModel = models.subsystemModel,
      componentModel = models.componentModel,
      publishModel = models.publishModel.map(m => resolvePublishModel(m)),
      subscribeModel = models.subscribeModel,
      commandModel = models.commandModel.map(m => resolveCommandModel(m))
    )
  }

  private def resolveCommandModel(commandModel: CommandModel): CommandModel = {
    CommandModel(
      subsystem = commandModel.subsystem,
      component = commandModel.component,
      description = commandModel.description,
      receive = commandModel.receive.map(m => resolveReceiveCommandModel(commandModel, m)),
      send = commandModel.send
    )
  }

  private def resolveCommandModel(ref: String, component: String, commandModel: CommandModel): CommandModel = {
    val maybeRefCommandModel =
      if (component == commandModel.component)
        Some(commandModel)
      else
        allModels.find(_.componentModel.map(_.component).contains(component)).flatMap(_.commandModel)
    if (maybeRefCommandModel.isEmpty)
      throw resolverException(
        s"Invalid ref '$ref': commands for component $component not found in subsystem ${commandModel.subsystem}"
      )
    maybeRefCommandModel.get
  }

  private def resolveReceiveCommandModel(
      commandModel: CommandModel,
      receiveCommandModel: ReceiveCommandModel
  ): ReceiveCommandModel = {
    val tryRefReceiveCommandModel =
      if (receiveCommandModel.ref.isEmpty) {
        Success(receiveCommandModel)
      }
      else {
        Try(resolveRefReceiveCommandModel(commandModel, Ref(receiveCommandModel.ref, commandModel.component, Ref.receive)))
      }
    tryRefReceiveCommandModel match {
      case Success(refReceiveCommandModel) =>
        ReceiveCommandModel(
          name = receiveCommandModel.name,
          ref = "",
          refError = "",
          description =
            if (receiveCommandModel.description.nonEmpty) receiveCommandModel.description else refReceiveCommandModel.description,
          requirements =
            if (receiveCommandModel.requirements.nonEmpty) receiveCommandModel.requirements
            else refReceiveCommandModel.requirements,
          preconditions =
            if (receiveCommandModel.preconditions.nonEmpty) receiveCommandModel.preconditions
            else refReceiveCommandModel.preconditions,
          postconditions =
            if (receiveCommandModel.postconditions.nonEmpty) receiveCommandModel.postconditions
            else refReceiveCommandModel.postconditions,
          requiredArgs =
            if (receiveCommandModel.requiredArgs.nonEmpty) receiveCommandModel.requiredArgs
            else refReceiveCommandModel.requiredArgs,
          parameters =
            if (receiveCommandModel.parameters.nonEmpty)
              receiveCommandModel.parameters.map(parameterModel =>
                resolveCommandParameter(commandModel, receiveCommandModel, ParamRef.commandParams, parameterModel)
              )
            else
              refReceiveCommandModel.parameters.map(parameterModel =>
                resolveCommandParameter(commandModel, receiveCommandModel, ParamRef.commandParams, parameterModel)
              ),
          completionType =
            if (receiveCommandModel.completionType != "immediate") receiveCommandModel.completionType
            else refReceiveCommandModel.completionType,
          resultType =
            if (receiveCommandModel.resultType.nonEmpty)
              receiveCommandModel.resultType.map(parameterModel =>
                resolveCommandParameter(commandModel, receiveCommandModel, ParamRef.resultType, parameterModel)
              )
            else
              refReceiveCommandModel.resultType.map(parameterModel =>
                resolveCommandParameter(commandModel, receiveCommandModel, ParamRef.resultType, parameterModel)
              ),
          completionConditions =
            if (receiveCommandModel.completionConditions.nonEmpty) receiveCommandModel.completionConditions
            else refReceiveCommandModel.completionConditions,
          role = if (receiveCommandModel.role.nonEmpty) receiveCommandModel.role else refReceiveCommandModel.role
        )
      case Failure(ex) =>
        receiveCommandModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
    }
  }

  private def resolveRefReceiveCommandModel(
      commandModel: CommandModel,
      ref: Ref,
      resolveParameters: Boolean = true
  ): ReceiveCommandModel = {
    val refCommandModel          = resolveCommandModel(ref.ref, ref.component, commandModel)
    val maybeReceiveCommandModel = refCommandModel.receive.find(_.name == ref.name)
    if (maybeReceiveCommandModel.isEmpty)
      throw resolverException(s"Invalid ref '${ref.ref}': Command ${ref.name} not found in ${ref.component}")
    val receiveCommandModel = maybeReceiveCommandModel.get
    val args =
      if (resolveParameters)
        receiveCommandModel.parameters.map(
          resolveCommandParameter(refCommandModel, receiveCommandModel, ParamRef.commandParams, _)
        )
      else
        receiveCommandModel.parameters
    val resultType =
      if (resolveParameters)
        receiveCommandModel.resultType.map(resolveCommandParameter(refCommandModel, receiveCommandModel, ParamRef.resultType, _))
      else
        receiveCommandModel.resultType
    receiveCommandModel.copy(ref = "", parameters = args, resultType = resultType)
  }

  private def resolvePublishModel(publishModel: PublishModel): PublishModel = {
    publishModel.copy(
      eventList = publishModel.eventList.map(resolveEvent(publishModel, Ref.events, _)),
      observeEventList = publishModel.observeEventList.map(resolveEvent(publishModel, Ref.observeEvents, _)),
      currentStateList = publishModel.currentStateList.map(resolveEvent(publishModel, Ref.currentStates, _))
    )
  }

  private def resolveEvent(publishModel: PublishModel, section: Ref.Section, eventModel: EventModel): EventModel = {
    val tryRefEventModel =
      if (eventModel.ref.isEmpty) {
        Success(eventModel)
      }
      else {
        Try(resolveRefEvent(publishModel, Ref(eventModel.ref, publishModel.component, section)))
      }
    tryRefEventModel match {
      case Success(refEventModel) =>
        EventModel(
          name = eventModel.name,
          ref = "",
          refError = "",
          description = if (eventModel.description.nonEmpty) eventModel.description else refEventModel.description,
          requirements = if (eventModel.requirements.nonEmpty) eventModel.requirements else refEventModel.requirements,
          maybeMaxRate = if (eventModel.maybeMaxRate.nonEmpty) eventModel.maybeMaxRate else refEventModel.maybeMaxRate,
          archive = if (eventModel.archive) eventModel.archive else refEventModel.archive,
          archiveDuration =
            if (eventModel.archiveDuration.nonEmpty) eventModel.archiveDuration else refEventModel.archiveDuration,
          parameterList =
            if (eventModel.parameterList.nonEmpty)
              eventModel.parameterList.map(parameterModel =>
                resolveEventParameter(publishModel, eventModel, section, parameterModel)
              )
            else
              refEventModel.parameterList.map(parameterModel =>
                resolveEventParameter(publishModel, eventModel, section, parameterModel)
              )
        )
      case Failure(ex) =>
        eventModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
    }
  }

  private def resolveRefParameter(paramRef: ParamRef): ParameterModel = {
    val maybeModel = allModels.find(_.componentModel.exists(_.component == paramRef.ref.component))
    if (maybeModel.isEmpty)
      throw resolverException(s"Invalid ref ${paramRef.ref.ref}: Can't find component ${paramRef.ref.component}")
    val model = maybeModel.get
    val paramList = paramRef.ref.section match {
      case Ref.events | Ref.observeEvents | Ref.currentStates =>
        if (model.publishModel.isEmpty)
          throw resolverException(s"Invalid ref ${paramRef.ref.ref}: No events found for ${paramRef.ref.component}")
        val publishModel = model.publishModel.get
        val eventModel   = resolveRefEvent(publishModel, paramRef.ref, resolveParameters = false)
        eventModel.parameterList
      case Ref.receive =>
        if (model.commandModel.isEmpty)
          throw resolverException(s"Invalid ref ${paramRef.ref.ref}: No commands found for ${paramRef.ref.component}")
        val commandModel        = model.commandModel.get
        val receiveCommandModel = resolveRefReceiveCommandModel(commandModel, paramRef.ref, resolveParameters = false)
        paramRef.paramSection match {
          case ParamRef.`eventParams` | ParamRef.`commandParams` => receiveCommandModel.parameters
          case ParamRef.resultType                               => receiveCommandModel.resultType
        }
    }
    val maybeParam = paramList.find(_.name == paramRef.name)
    if (maybeParam.isEmpty)
      throw resolverException(s"Invalid parameter ref: ${paramRef.ref.ref}: Parameter ${paramRef.name} not found")
    maybeParam.get
  }

  private def resolveEventParameter(
      publishModel: PublishModel,
      eventModel: EventModel,
      section: Ref.Section,
      parameterModel: ParameterModel
  ): ParameterModel = {
    if (parameterModel.ref.isEmpty) {
      if (parameterModel.parameterList.nonEmpty) {
        // handle struct type
        parameterModel.copy(parameterList =
          parameterModel.parameterList.map(a => resolveEventParameter(publishModel, eventModel, section, a))
        )
      }
      else parameterModel
    }
    else {
      Try(
        resolveRefParameter(ParamRef(parameterModel.ref, publishModel.component, section, eventModel.name, ParamRef.eventParams))
      ) match {
        case Success(refParameter) =>
          resolveParameters(parameterModel, refParameter)
        case Failure(ex) =>
          parameterModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
      }
    }
  }

  private def resolveCommandParameter(
      commandModel: CommandModel,
      receiveCommandModel: ReceiveCommandModel,
      section: ParamRef.ParamSection,
      parameterModel: ParameterModel
  ): ParameterModel = {
    if (parameterModel.ref.isEmpty) {
      if (parameterModel.parameterList.nonEmpty) {
        // handle struct type
        parameterModel.copy(parameterList =
          parameterModel.parameterList.map(a => resolveCommandParameter(commandModel, receiveCommandModel, section, a))
        )
      }
      else parameterModel
    }
    else {
      Try(
        resolveRefParameter(
          ParamRef(parameterModel.ref, commandModel.component, Ref.receive, receiveCommandModel.name, section)
        )
      ) match {
        case Success(refParameter) =>
          resolveParameters(parameterModel, refParameter)
        case Failure(ex) =>
          parameterModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
      }
    }
  }

  // If a field is defined in parameterModel, use it, otherwise use the one from refParameter
  private def resolveParameters(
      parameterModel: ParameterModel,
      refParameter: ParameterModel
  ): ParameterModel = {
    ParameterModel(
      name = parameterModel.name,
      ref = "",
      refError = "",
      description = if (parameterModel.description.nonEmpty) parameterModel.description else refParameter.description,
      maybeType = if (parameterModel.maybeType.nonEmpty) parameterModel.maybeType else refParameter.maybeType,
      maybeEnum = if (parameterModel.maybeEnum.nonEmpty) parameterModel.maybeEnum else refParameter.maybeEnum,
      maybeArrayType = if (parameterModel.maybeArrayType.nonEmpty) parameterModel.maybeArrayType else refParameter.maybeArrayType,
      maybeDimensions =
        if (parameterModel.maybeDimensions.nonEmpty) parameterModel.maybeDimensions else refParameter.maybeDimensions,
      units = if (parameterModel.units.nonEmpty) parameterModel.units else refParameter.units,
      maxItems = if (parameterModel.maxItems.nonEmpty) parameterModel.maxItems else refParameter.maxItems,
      minItems = if (parameterModel.minItems.nonEmpty) parameterModel.minItems else refParameter.minItems,
      minimum = if (parameterModel.minimum.nonEmpty) parameterModel.minimum else refParameter.minimum,
      maximum = if (parameterModel.maximum.nonEmpty) parameterModel.maximum else refParameter.maximum,
      exclusiveMinimum = if (parameterModel.exclusiveMinimum) parameterModel.exclusiveMinimum else refParameter.exclusiveMinimum,
      exclusiveMaximum = if (parameterModel.exclusiveMaximum) parameterModel.exclusiveMaximum else refParameter.exclusiveMaximum,
      allowNaN = if (parameterModel.allowNaN) parameterModel.allowNaN else refParameter.allowNaN,
      defaultValue = if (parameterModel.defaultValue.nonEmpty) parameterModel.defaultValue else refParameter.defaultValue,
      typeStr = if (parameterModel.typeStr.nonEmpty) parameterModel.typeStr else refParameter.typeStr,
      parameterList = if (parameterModel.parameterList.nonEmpty) parameterModel.parameterList else refParameter.parameterList
    )
  }

  private def resolvePublishModel(ref: String, component: String, publishModel: PublishModel): PublishModel = {
    val maybeRefPublishModel =
      if (component == publishModel.component)
        Some(publishModel)
      else
        allModels.find(_.componentModel.map(_.component).contains(component)).flatMap(_.publishModel)
    if (maybeRefPublishModel.isEmpty)
      throw resolverException(
        s"Invalid ref '$ref': published events for component $component not found in subsystem ${publishModel.subsystem}"
      )
    maybeRefPublishModel.get
  }

  // Resolve a reference to another event
  private def resolveRefEvent(publishModel: PublishModel, ref: Ref, resolveParameters: Boolean = true): EventModel = {
    val refPublishModel = resolvePublishModel(ref.ref, ref.component, publishModel)
    val eventModelList = ref.section match {
      case Ref.events        => refPublishModel.eventList
      case Ref.observeEvents => refPublishModel.observeEventList
      case Ref.currentStates => refPublishModel.currentStateList
      case _ =>
        throw resolverException(s"Invalid ref '${ref.ref}': invalid event type: ${ref.section}")
    }
    val maybeEventModel = eventModelList.find(_.name == ref.name)
    if (maybeEventModel.isEmpty) {
      throw resolverException(
        s"Invalid ref '${ref.ref}': Event ${ref.name} not found in ${ref.component}"
      )
    }
    val eventModel = maybeEventModel.get
    val parameterList =
      if (resolveParameters)
        eventModel.parameterList.map(resolveEventParameter(refPublishModel, eventModel, ref.section, _))
      else
        eventModel.parameterList
    eventModel.copy(ref = "", parameterList = parameterList)
  }

}
