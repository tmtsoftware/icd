package csw.services.icd.db

import icd.web.shared.IcdModels
import icd.web.shared.IcdModels.{AttributeModel, EventModel, PublishModel}
import Resolver._

object Resolver {
  class ResolverException(msg: String) extends RuntimeException(msg)
}

/**
 * A utility class used to resolve "ref" entries in events, commands or attributes.
 * @param models list of models for a subsystem (based on the input model files)
 */
case class Resolver(models: List[IcdModels]) {

  /**
   * Resolve any event, command or attribute "ref" items and return the resolved definitions.
   * @return the fully resolved list of models
   */
  def resolve(): List[IcdModels] = {
    models.map(resolveIcdModels)
  }

  private def resolveIcdModels(models: IcdModels): IcdModels = {
    val publishModel = models.publishModel.map(resolvePublishModel)
    IcdModels(
      subsystemModel = models.subsystemModel,
      componentModel = models.componentModel,
      publishModel = publishModel,
      subscribeModel = models.subscribeModel,
      // XXX TODO: resolve received command
      commandModel = models.commandModel
    )
  }

  private def resolvePublishModel(publishModel: PublishModel): PublishModel = {
    val events        = publishModel.eventList.map(eventModel => resolveEvent(publishModel, "events", eventModel))
    val observeEvents = publishModel.eventList.map(eventModel => resolveEvent(publishModel, "observeEvents", eventModel))
    val currentStates = publishModel.eventList.map(eventModel => resolveEvent(publishModel, "currentStates", eventModel))
    PublishModel(
      subsystem = publishModel.subsystem,
      component = publishModel.component,
      description = publishModel.description,
      eventList = events,
      observeEventList = observeEvents,
      currentStateList = currentStates,
      alarmList = publishModel.alarmList
    )
  }

  private def resolveEvent(publishModel: PublishModel, section: String, eventModel: EventModel): EventModel = {
    val refEventModel =
      if (eventModel.ref.isEmpty)
        eventModel
      else
        resolveRefEvent(publishModel, section, eventModel.ref)
    EventModel(
      name = eventModel.name,
      ref = "",
      description = if (eventModel.description.nonEmpty) eventModel.description else refEventModel.description,
      requirements = if (eventModel.requirements.nonEmpty) eventModel.requirements else refEventModel.requirements,
      maybeMaxRate = if (eventModel.maybeMaxRate.nonEmpty) eventModel.maybeMaxRate else refEventModel.maybeMaxRate,
      archive = if (eventModel.archive) eventModel.archive else refEventModel.archive,
      archiveDuration = if (eventModel.archiveDuration.nonEmpty) eventModel.archiveDuration else refEventModel.archiveDuration,
      attributesList =
        if (eventModel.attributesList.nonEmpty)
          eventModel.attributesList.map(attributeModel => resolveAttribute(publishModel, section, attributeModel))
        else
          refEventModel.attributesList.map(attributeModel => resolveAttribute(publishModel, section, attributeModel))
    )

  }

  private def resolveRefAttribute(publishModel: PublishModel, eventType: String, ref: String): AttributeModel = {
    val parts          = ref.split('/')
    val refEventModel  = resolveRefEvent(publishModel, eventType, parts.dropRight(1).mkString("/"))
    val name           = parts.reverse.head
    val maybeAttribute = refEventModel.attributesList.find(_.name == name)
    if (maybeAttribute.isEmpty) {
      throw new ResolverException(s"Invalid ref '$ref': attribute $name not found")
    }
    maybeAttribute.get
  }

  private def resolveAttribute(
      publishModel: PublishModel,
      section: String,
      attributeModel: AttributeModel
  ): AttributeModel = {
    if (attributeModel.ref.isEmpty)
      attributeModel
    else {
      val refAttribute = resolveRefAttribute(publishModel, section, attributeModel.ref)
      AttributeModel(
        name = attributeModel.name,
        ref = "",
        description = if (attributeModel.description.nonEmpty) attributeModel.description else refAttribute.description,
        maybeType = if (attributeModel.maybeType.nonEmpty) attributeModel.maybeType else refAttribute.maybeType,
        maybeEnum = if (attributeModel.maybeEnum.nonEmpty) attributeModel.maybeEnum else refAttribute.maybeEnum,
        maybeArrayType =
          if (attributeModel.maybeArrayType.nonEmpty) attributeModel.maybeArrayType else refAttribute.maybeArrayType,
        maybeDimensions =
          if (attributeModel.maybeDimensions.nonEmpty) attributeModel.maybeDimensions else refAttribute.maybeDimensions,
        units = if (attributeModel.units.nonEmpty) attributeModel.units else refAttribute.units,
        maxItems = if (attributeModel.maxItems.nonEmpty) attributeModel.maxItems else refAttribute.maxItems,
        minItems = if (attributeModel.minItems.nonEmpty) attributeModel.minItems else refAttribute.minItems,
        minimum = if (attributeModel.minimum.nonEmpty) attributeModel.minimum else refAttribute.minimum,
        maximum = if (attributeModel.maximum.nonEmpty) attributeModel.maximum else refAttribute.maximum,
        exclusiveMinimum =
          if (attributeModel.exclusiveMinimum) attributeModel.exclusiveMinimum else refAttribute.exclusiveMinimum,
        exclusiveMaximum =
          if (attributeModel.exclusiveMaximum) attributeModel.exclusiveMaximum else refAttribute.exclusiveMaximum,
        allowNaN = if (attributeModel.allowNaN) attributeModel.allowNaN else refAttribute.allowNaN,
        defaultValue = if (attributeModel.defaultValue.nonEmpty) attributeModel.defaultValue else refAttribute.defaultValue,
        typeStr = if (attributeModel.typeStr.nonEmpty) attributeModel.typeStr else refAttribute.typeStr,
        attributesList =
          if (attributeModel.attributesList.nonEmpty) attributeModel.attributesList else refAttribute.attributesList
      )
    }
  }

  private def resolvePublishModel(ref: String, component: String, publishModel: PublishModel): PublishModel = {
    val maybeRefPublishModel =
      if (component == publishModel.component)
        Some(publishModel)
      else
        models.find(_.componentModel.map(_.component).contains(component)).flatMap(_.publishModel)
    if (maybeRefPublishModel.isEmpty)
      throw new ResolverException(s"Invalid ref '$ref': component $component not found in subsystem ${publishModel.subsystem}")
    maybeRefPublishModel.get
  }

  // Resolve a reference to another event in the form
  // $componentName/events/$eventName
  // or $componentName/observeEvents/$eventName
  // or $componentName/currentState/$eventName
  // or $componentName/receive/$commandName
  // or events/$eventName (in same component)
  //or just $eventName (in same section)
  private def resolveRefEvent(publishModel: PublishModel, eventType: String, ref: String): EventModel = {
    val parts = ref.split('/')
    val (component, section, name) = parts.length match {
      case 1 =>
        (publishModel.component, eventType, parts.head)
      case 2 =>
        (publishModel.component, parts(0), parts(1))
      case 3 =>
        (parts(0), parts(1), parts(2))
      case _ =>
        throw new ResolverException(s"Invalid ref '$ref': Expected syntax like: componentName/events/eventName")
    }
    val refPublishModel = resolvePublishModel(ref, component, publishModel)
    val eventModelList = section match {
      case "events"        => refPublishModel.eventList
      case "observeEvents" => refPublishModel.observeEventList
      case "currentStates" => refPublishModel.currentStateList
      case _ =>
        throw new ResolverException(s"Invalid ref '$ref': invalid event type: $section")
    }
    val maybeEventModel = eventModelList.find(_.name == name)
    if (maybeEventModel.isEmpty)
      throw new ResolverException(s"Invalid ref '$ref': Event $name not found in $component $section")
    val eventModel = maybeEventModel.get
    EventModel(
      name = eventModel.name,
      ref = "",
      description = eventModel.description,
      requirements = eventModel.requirements,
      maybeMaxRate = eventModel.maybeMaxRate,
      archive = eventModel.archive,
      archiveDuration = eventModel.archiveDuration,
      attributesList = eventModel.attributesList.map(attributeModel => resolveAttribute(refPublishModel, section, attributeModel))
    )
  }

}
