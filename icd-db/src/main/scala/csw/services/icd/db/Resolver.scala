package csw.services.icd.db

import icd.web.shared.IcdModels
import icd.web.shared.IcdModels.{AttributeModel, CommandModel, EventModel, PublishModel, ReceiveCommandModel}

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger
import Resolver._

object Resolver {
  private lazy val log: Logger = Logger("csw.services.icd.db.Resolver")

  private class ResolverException(msg: String) extends RuntimeException(msg)

  private def resolverException(msg: String): ResolverException = {
    val ex = new ResolverException(msg)
    log.error(msg)
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
     * Attributes for events, as above, but with attr name:
     * $componentName/events/$eventName/attributes/$attrName
     * or abbreviated as above.
     *
     * Attributes for commands: as above, but with additional info:
     * $componentName/receive/$commandName/args/$attrName
     * or $componentName/receive/$commandName/resultType/$attrName
     * or abbreviated as above.
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
   * Holds the parts of an attribute reference
   */
  private case class AttrRef(ref: Ref, attrSection: AttrRef.AttrSection, name: String)

  private object AttrRef {
    // Tells where to look for an attribute
    sealed trait AttrSection
    object attributes extends AttrSection
    object args       extends AttrSection
    object resultType extends AttrSection

    object AttrSection {
      def apply(ref: String, s: String): AttrSection = {
        s match {
          case "attributes" => attributes
          case "args"       => args
          case "resultType" => resultType
          case x =>
            throw resolverException(
              s"Invalid ref attribute section $x in $ref, expected one of (attributes, args, resultType)"
            )
        }
      }
    }

    /**
     * Returns an AttrRef object for an event or command attribute ref string in the form:
     *
     * For events:
     * $componentName/events/$eventName/attributes/$attrName
     * or abbreviated if in same scope (see parseRef).
     *
     * For commands:
     * $componentName/receive/$commandName/args/$attrName
     * or $componentName/receive/$commandName/resultType/$attrName
     * or abbreviated (See parseRef).
     *
     * @param ref the ref string
     * @param defaultComponent if component is not specified in the ref string, use this
     * @param defaultSection if section is not given in the ref, use this
     * @param defaultName if the name of the event or command is not in the ref, use this
     * @param defaultAttrSection if attribute section is not given, use this
     */
    def apply(
        ref: String,
        defaultComponent: String,
        defaultSection: Ref.Section,
        defaultName: String,
        defaultAttrSection: AttrSection
    ): AttrRef = {
      val parts = ref.split('/')
      parts.length match {
        case 1 =>
          AttrRef(Ref(ref, defaultComponent, defaultSection, defaultName), defaultAttrSection, parts.head)
        case 2 =>
          AttrRef(Ref(ref, defaultComponent, defaultSection, defaultName), AttrSection(ref, parts(0)), parts(1))
        case 3 =>
          AttrRef(Ref(ref, defaultComponent, defaultSection, parts(0)), AttrSection(ref, parts(1)), parts(2))
        case 4 =>
          AttrRef(Ref(ref, defaultComponent, Ref.Section(ref, parts(0)), parts(1)), AttrSection(ref, parts(2)), parts(3))
        case 5 =>
          AttrRef(Ref(ref, parts(0), Ref.Section(ref, parts(1)), parts(2)), AttrSection(ref, parts(3)), parts(4))
        case _ =>
          throw resolverException(
            s"Invalid attribute ref '$ref': Expected syntax like: componentName/events/eventName/attributes/attrName (or abbreviated if in same scope)"
          )
      }
    }
  }
}

/**
 * A utility class used to resolve "ref" entries in events, commands or attributes.
 * @param allModels list of all models for a subsystem (based on the input model files)
 */
case class Resolver(allModels: List[IcdModels]) {

  /**
   * Resolve any event, command or attribute "ref" items and return the resolved definitions.
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
          args =
            if (receiveCommandModel.args.nonEmpty)
              receiveCommandModel.args.map(attributeModel =>
                resolveCommandAttribute(commandModel, receiveCommandModel, AttrRef.args, attributeModel)
              )
            else
              refReceiveCommandModel.args.map(attributeModel =>
                resolveCommandAttribute(commandModel, receiveCommandModel, AttrRef.args, attributeModel)
              ),
          completionType =
            if (receiveCommandModel.completionType.nonEmpty) receiveCommandModel.completionType
            else refReceiveCommandModel.completionType,
          resultType =
            if (receiveCommandModel.resultType.nonEmpty)
              receiveCommandModel.resultType.map(attributeModel =>
                resolveCommandAttribute(commandModel, receiveCommandModel, AttrRef.resultType, attributeModel)
              )
            else
              refReceiveCommandModel.resultType.map(attributeModel =>
                resolveCommandAttribute(commandModel, receiveCommandModel, AttrRef.resultType, attributeModel)
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
      resolveAttributes: Boolean = true
  ): ReceiveCommandModel = {
    val refCommandModel          = resolveCommandModel(ref.ref, ref.component, commandModel)
    val maybeReceiveCommandModel = refCommandModel.receive.find(_.name == ref.name)
    if (maybeReceiveCommandModel.isEmpty)
      throw resolverException(s"Invalid ref '${ref.ref}': Command ${ref.name} not found in ${ref.component}")
    val receiveCommandModel = maybeReceiveCommandModel.get
    val args =
      if (resolveAttributes)
        receiveCommandModel.args.map(resolveCommandAttribute(refCommandModel, receiveCommandModel, AttrRef.args, _))
      else
        receiveCommandModel.args
    val resultType =
      if (resolveAttributes)
        receiveCommandModel.resultType.map(resolveCommandAttribute(refCommandModel, receiveCommandModel, AttrRef.resultType, _))
      else
        receiveCommandModel.resultType
    receiveCommandModel.copy(ref = "", args = args, resultType = resultType)
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
          attributesList =
            if (eventModel.attributesList.nonEmpty)
              eventModel.attributesList.map(attributeModel =>
                resolveEventAttribute(publishModel, eventModel, section, attributeModel)
              )
            else
              refEventModel.attributesList.map(attributeModel =>
                resolveEventAttribute(publishModel, eventModel, section, attributeModel)
              )
        )
      case Failure(ex) =>
        eventModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
    }
  }

  private def resolveRefAttribute(attrRef: AttrRef): AttributeModel = {
    val maybeModel = allModels.find(_.componentModel.exists(_.component == attrRef.ref.component))
    if (maybeModel.isEmpty)
      throw resolverException(s"Invalid ref ${attrRef.ref.ref}: Can't find component ${attrRef.ref.component}")
    val model = maybeModel.get
    val attrList = attrRef.ref.section match {
      case Ref.events | Ref.observeEvents | Ref.currentStates =>
        if (model.publishModel.isEmpty)
          throw resolverException(s"Invalid ref ${attrRef.ref.ref}: No events found for ${attrRef.ref.component}")
        val publishModel = model.publishModel.get
        val eventModel   = resolveRefEvent(publishModel, attrRef.ref, resolveAttributes = false)
        eventModel.attributesList
      case Ref.receive =>
        if (model.commandModel.isEmpty)
          throw resolverException(s"Invalid ref ${attrRef.ref.ref}: No commands found for ${attrRef.ref.component}")
        val commandModel        = model.commandModel.get
        val receiveCommandModel = resolveRefReceiveCommandModel(commandModel, attrRef.ref, resolveAttributes = false)
        attrRef.attrSection match {
          case AttrRef.attributes | AttrRef.args => receiveCommandModel.args
          case AttrRef.resultType                => receiveCommandModel.resultType
        }
    }
    val maybeAttr = attrList.find(_.name == attrRef.name)
    if (maybeAttr.isEmpty)
      throw resolverException(s"Invalid attribute ref: ${attrRef.ref.ref}: Attribute ${attrRef.name} not found")
    maybeAttr.get
  }

  private def resolveEventAttribute(
      publishModel: PublishModel,
      eventModel: EventModel,
      section: Ref.Section,
      attributeModel: AttributeModel
  ): AttributeModel = {
    if (attributeModel.ref.isEmpty)
      attributeModel
    else {
      Try(
        resolveRefAttribute(AttrRef(attributeModel.ref, publishModel.component, section, eventModel.name, AttrRef.attributes))
      ) match {
        case Success(refAttribute) =>
          resolveAttributes(attributeModel, refAttribute)
        case Failure(ex) =>
          attributeModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
      }
    }
  }

  private def resolveCommandAttribute(
      commandModel: CommandModel,
      receiveCommandModel: ReceiveCommandModel,
      section: AttrRef.AttrSection,
      attributeModel: AttributeModel
  ): AttributeModel = {
    if (attributeModel.ref.isEmpty)
      attributeModel
    else {
      Try(
        resolveRefAttribute(
          AttrRef(attributeModel.ref, commandModel.component, Ref.receive, receiveCommandModel.name, section)
        )
      ) match {
        case Success(refAttribute) =>
          resolveAttributes(attributeModel, refAttribute)
        case Failure(ex) =>
          attributeModel.copy(ref = "", refError = s"Error: ${ex.getMessage}")
      }
    }
  }

  // If a field is defined in attributeModel, use it, otherwise use the one from refAttribute
  private def resolveAttributes(
      attributeModel: AttributeModel,
      refAttribute: AttributeModel
  ): AttributeModel = {
    AttributeModel(
      name = attributeModel.name,
      ref = "",
      refError = "",
      description = if (attributeModel.description.nonEmpty) attributeModel.description else refAttribute.description,
      maybeType = if (attributeModel.maybeType.nonEmpty) attributeModel.maybeType else refAttribute.maybeType,
      maybeEnum = if (attributeModel.maybeEnum.nonEmpty) attributeModel.maybeEnum else refAttribute.maybeEnum,
      maybeArrayType = if (attributeModel.maybeArrayType.nonEmpty) attributeModel.maybeArrayType else refAttribute.maybeArrayType,
      maybeDimensions =
        if (attributeModel.maybeDimensions.nonEmpty) attributeModel.maybeDimensions else refAttribute.maybeDimensions,
      units = if (attributeModel.units.nonEmpty) attributeModel.units else refAttribute.units,
      maxItems = if (attributeModel.maxItems.nonEmpty) attributeModel.maxItems else refAttribute.maxItems,
      minItems = if (attributeModel.minItems.nonEmpty) attributeModel.minItems else refAttribute.minItems,
      minimum = if (attributeModel.minimum.nonEmpty) attributeModel.minimum else refAttribute.minimum,
      maximum = if (attributeModel.maximum.nonEmpty) attributeModel.maximum else refAttribute.maximum,
      exclusiveMinimum = if (attributeModel.exclusiveMinimum) attributeModel.exclusiveMinimum else refAttribute.exclusiveMinimum,
      exclusiveMaximum = if (attributeModel.exclusiveMaximum) attributeModel.exclusiveMaximum else refAttribute.exclusiveMaximum,
      allowNaN = if (attributeModel.allowNaN) attributeModel.allowNaN else refAttribute.allowNaN,
      defaultValue = if (attributeModel.defaultValue.nonEmpty) attributeModel.defaultValue else refAttribute.defaultValue,
      typeStr = if (attributeModel.typeStr.nonEmpty) attributeModel.typeStr else refAttribute.typeStr,
      attributesList = if (attributeModel.attributesList.nonEmpty) attributeModel.attributesList else refAttribute.attributesList
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

  // Resolve a reference to another event in the form
  private def resolveRefEvent(publishModel: PublishModel, ref: Ref, resolveAttributes: Boolean = true): EventModel = {
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
    val attributesList =
      if (resolveAttributes)
        eventModel.attributesList.map(resolveEventAttribute(refPublishModel, eventModel, ref.section, _))
      else
        eventModel.attributesList
    eventModel.copy(ref = "", attributesList = attributesList)
  }

}
