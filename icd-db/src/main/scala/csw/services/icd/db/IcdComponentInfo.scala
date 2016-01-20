package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.{ Published, PublishInfo, PublishedItem, Subscribed, PublishType, Alarms, EventStreams, Events, Telemetry }
import csw.services.icd.html.HtmlMarkup
import csw.services.icd.model.{ JsonSchemaModel, ReceiveCommandModel, ComponentModel, IcdModels }
import icd.web
import icd.web.shared._

/**
 * Gathers information related to a component in a given version of an ICD.
 * Only information related to the component and the target subsystem version is included.
 */
object IcdComponentInfo {
  /**
   * Query the database for information about the given component
   *
   * @param db               used to access the database
   * @param subsystem        the subsystem containing the component
   * @param versionOpt       the version of the subsystem to use (determines the version of the component):
   *                         None for unpublished working version
   * @param compNames        the component names
   * @param target           the target subsystem of the ICD
   * @param targetVersionOpt the version of the target subsystem to use
   * @return an object containing information about the component
   */
  def getComponentInfoList(db: IcdDb, subsystem: String, versionOpt: Option[String], compNames: List[String],
                           target: String, targetVersionOpt: Option[String]): List[ComponentInfo] = {
    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val query = new CachedIcdDbQuery(db.db)
    compNames.map(getComponentInfo(query, subsystem, versionOpt, _, target, targetVersionOpt))
  }

  /**
   * Query the database for information about the given component
   *
   * @param query            used to access the database
   * @param subsystem        the subsystem containing the component
   * @param versionOpt       the version of the subsystem to use (determines the version of the component):
   *                         None for unpublished working version
   * @param compName         the component name
   * @param target           the target subsystem of the ICD
   * @param targetVersionOpt the version of the target subsystem to use
   * @return an object containing information about the component
   */
  def getComponentInfo(query: IcdDbQuery, subsystem: String, versionOpt: Option[String], compName: String,
                       target: String, targetVersionOpt: Option[String]): ComponentInfo = {
    // get the models for this component
    val versionManager = new CachedIcdVersionManager(query)
    val modelsList = versionManager.getModels(subsystem, versionOpt, Some(compName))
    val targetModelsList = versionManager.getModels(target, targetVersionOpt, None)
    val title = getComponentField(modelsList, _.title)
    val description = getComponentField(modelsList, _.description)
    val prefix = getComponentField(modelsList, _.prefix)
    val componentType = getComponentField(modelsList, _.componentType)
    val wbsId = getComponentField(modelsList, _.wbsId)
    val h = modelsList.headOption

    val publishes = h.flatMap(getPublishes(subsystem, _, targetModelsList))
    val subscribes = h.flatMap(getSubscribes(_, targetModelsList))
    val commands = h.flatMap(getCommands(_, targetModelsList))

    ComponentInfo(subsystem, compName, title,
      HtmlMarkup.gfmToHtml(description),
      prefix, componentType, wbsId,
      publishes,
      subscribes,
      commands)
  }

  /**
   * Gets a string value from the component description, or an empty string if not found
   *
   * @param modelsList list of model sets for the component
   * @param f          function to get the value
   */
  private def getComponentField(modelsList: List[IcdModels], f: ComponentModel ⇒ String): String = {
    if (modelsList.isEmpty) ""
    else {
      modelsList.head.componentModel match {
        case Some(model) ⇒ f(model)
        case None        ⇒ ""
      }
    }
  }

  /**
   * Gets display information about an attribute from the given model object
   */
  private def getAttributeInfo(a: JsonSchemaModel): AttributeInfo = {
    AttributeInfo(a.name, HtmlMarkup.gfmToHtml(a.description), a.typeStr, a.units, a.defaultValue)
  }

  /**
   * Gets information about the items published by a component
   *
   * @param subsystem        the source subsystem
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getPublishes(subsystem: String, models: IcdModels, targetModelsList: List[IcdModels]): Option[Publishes] = {
    models.componentModel match {
      case None ⇒ None
      case Some(componentModel) ⇒
        val prefix = componentModel.prefix
        models.publishModel match {
          case None ⇒ None
          case Some(m) ⇒
            val desc = HtmlMarkup.gfmToHtml(m.description)
            val telemetryList = m.telemetryList.map { t ⇒
              TelemetryInfo(t.name, HtmlMarkup.gfmToHtml(t.description), t.minRate, t.maxRate, t.archive, t.archiveRate,
                t.attributesList.map(getAttributeInfo), getSubscribers(subsystem, prefix, t.name, t.description, Telemetry, targetModelsList))
            }
            val eventList = m.eventList.map { el ⇒
              EventInfo(getAttributeInfo(el),
                getSubscribers(subsystem, prefix, el.name, el.description, Events, targetModelsList))
            }
            val eventStreamList = m.eventStreamList.map { t ⇒
              TelemetryInfo(t.name, HtmlMarkup.gfmToHtml(t.description), t.minRate, t.maxRate, t.archive, t.archiveRate,
                t.attributesList.map(getAttributeInfo), getSubscribers(subsystem, prefix, t.name, t.description, EventStreams, targetModelsList))
            }
            val alarmList = m.alarmList.map { al ⇒
              AlarmInfo(al.name, HtmlMarkup.gfmToHtml(al.description), al.severity, al.archive,
                getSubscribers(subsystem, prefix, al.name, al.description, Alarms, targetModelsList))
            }
            if (desc.nonEmpty || telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(desc, telemetryList, eventList, eventStreamList, alarmList))
            else None
        }
    }
  }

  /**
   * Returns the object if the component subscribes to the given value.
   *
   * @param subscriberSubsystem the subscriber's subsystem
   * @param subscriberCompName  the subscriber's component name
   * @param path                full path name of value (prefix + name)
   * @param targetInfo          list of items the target subscribes to
   * @param subscribeType       telemetry, alarm, etc...
   * @return the Subscribed object, if the component is a subscriber to the given path
   */
  private def subscribes(subscriberSubsystem: String, subscriberCompName: String, publisherSubsystem: String,
                         path: String, targetInfo: List[csw.services.icd.model.SubscribeInfo],
                         subscribeType: PublishType): Option[Subscribed] = {
    targetInfo.find { subscribeInfo ⇒
      subscribeInfo.name == path && subscribeInfo.subsystem == publisherSubsystem
    }.map { subscribeInfo ⇒
      Subscribed(subscriberCompName, subscriberSubsystem, subscribeType, path, subscribeInfo.usage)
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   *
   * @param subsystem        the publisher's subsystem
   * @param prefix           component's prefix
   * @param name             simple name of the published item
   * @param desc             description of the item
   * @param subscribeType    telemetry, alarm, etc...
   * @param targetModelsList the target model objects
   */
  private def getSubscribers(subsystem: String, prefix: String, name: String, desc: String, subscribeType: PublishType,
                             targetModelsList: List[IcdModels]): List[SubscribeInfo] = {
    for {
      icdModel ← targetModelsList
      subscribeModel ← icdModel.subscribeModel
      s ← subscribes(subscribeModel.subsystem, subscribeModel.component,
        subsystem, s"$prefix.$name", subscribeModel.telemetryList, subscribeType)
    } yield {
      web.shared.SubscribeInfo(s.subscribeType.toString, s.name, HtmlMarkup.gfmToHtml(desc),
        HtmlMarkup.gfmToHtml(s.usage), s.subsystem, s.componentName)
    }
  }

  /**
   * Returns a list describing what each component publishes
   *
   * @param targetModelsList the target model objects
   */
  private def getPublishInfo(targetModelsList: List[IcdModels]): List[PublishInfo] = {
    for {
      icdModels ← targetModelsList
      publishModel ← icdModels.publishModel
      componentModel ← icdModels.componentModel
    } yield {
      val publishedList = List(publishModel.telemetryList.map(i ⇒ Published(Telemetry, i.name, i.description)),
        publishModel.eventList.map(i ⇒ Published(Events, i.name, i.description)),
        publishModel.eventStreamList.map(i ⇒ Published(EventStreams, i.name, i.description)),
        publishModel.alarmList.map(i ⇒ Published(Alarms, i.name, i.description))).flatten
      PublishInfo(publishModel.component, componentModel.prefix, publishedList)
    }
  }

  /**
   * Returns a list describing which component(s) publish the given value.
   *
   * @param path             full path name of value (prefix + name)
   * @param publishType      telemetry, alarm, etc...
   * @param targetModelsList the target model objects
   */
  private def publishes(path: String, publishType: PublishType, targetModelsList: List[IcdModels]): List[PublishedItem] = {
    for {
      i ← getPublishInfo(targetModelsList)
      p ← i.publishes.filter(p ⇒ s"${i.prefix}.${p.name}" == path && publishType == p.publishType)
    } yield PublishedItem(i.componentName, i.prefix, p)
  }

  /**
   * Gets a information about the items the component subscribes to, along with the publisher of each item
   *
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getSubscribes(models: IcdModels, targetModelsList: List[IcdModels]): Option[Subscribes] = {
    // Gets a list of items of a given type that the component subscribes to, with publisher info
    def getInfo(publishType: PublishType, si: csw.services.icd.model.SubscribeInfo): List[SubscribeInfo] = {
      publishes(si.name, publishType, targetModelsList).map { pi ⇒
        web.shared.SubscribeInfo(publishType.toString, si.name, HtmlMarkup.gfmToHtml(pi.item.description),
          HtmlMarkup.gfmToHtml(si.usage), si.subsystem, pi.componentName)
      }
    }

    models.subscribeModel match {
      case None ⇒ None
      case Some(m) ⇒
        val subscribeInfo = m.telemetryList.map(getInfo(Telemetry, _)) ++
          m.eventList.map(getInfo(Events, _)) ++
          m.eventStreamList.map(getInfo(EventStreams, _)) ++
          m.alarmList.map(getInfo(Alarms, _))
        val desc = m.description
        if (desc.nonEmpty || subscribeInfo.nonEmpty)
          Some(Subscribes(HtmlMarkup.gfmToHtml(desc), subscribeInfo.flatten))
        else None
    }
  }

  /**
   * Returns a list of the names of target components that send the given command to the given component/subsystem
   *
   * @param subsystem        the subsystem that contains the component that defines the command
   * @param component        the component that defines the command
   * @param commandName      the command name
   * @param targetModelsList the target model objects
   * @return
   */
  private def getCommandSenders(subsystem: String, component: String, commandName: String,
                                targetModelsList: List[IcdModels]): List[ComponentModel] = {
    for {
      icdModels ← targetModelsList
      componentModel ← icdModels.componentModel
      commandModel ← icdModels.commandModel
      sendCommandModel ← commandModel.send.find(s ⇒
        s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield componentModel
  }

  /**
   * Gets a list of commands received by the component, including information about which components
   * send each command.
   *
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getCommandsReceived(models: IcdModels, targetModelsList: List[IcdModels]): List[ReceivedCommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = getCommandSenders(cmd.subsystem, cmd.component, received.name, targetModelsList).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      val desc = HtmlMarkup.gfmToHtml(received.description)
      val args = received.args.map(getAttributeInfo)
      ReceivedCommandInfo(received.name, desc, senders, received.requirements, received.requiredArgs, args)
    }
  }

  /**
   * Returns an object describing a command, defined to be received by the given component in the given subsystem,
   * if found.
   *
   * @param subsystem        the subsystem that contains the component that defines the command
   * @param component        the component that defines the command
   * @param commandName      the command name
   * @param targetModelsList the target model objects
   */
  private def getCommand(subsystem: String, component: String, commandName: String,
                         targetModelsList: List[IcdModels]): Option[ReceiveCommandModel] = {
    val result = for {
      icdModels ← targetModelsList
      commandModel ← icdModels.commandModel
      if commandModel.subsystem == subsystem && commandModel.component == component
      receiveCommandModel ← commandModel.receive.find(_.name == commandName)
    } yield receiveCommandModel

    result.headOption
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   *
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getCommandsSent(models: IcdModels, targetModelsList: List[IcdModels]): List[SentCommandInfo] = {
    val result = for {
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      getCommand(sent.subsystem, sent.component, sent.name, targetModelsList).map { r ⇒
        SentCommandInfo(sent.name, HtmlMarkup.gfmToHtml(r.description),
          List(OtherComponent(sent.subsystem, sent.component)))
      }
    }
    result.flatten
  }

  /**
   * Gets a list of commands sent or received by the component.
   *
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getCommands(models: IcdModels, targetModelsList: List[IcdModels]): Option[Commands] = {
    val received = getCommandsReceived(models, targetModelsList)
    val sent = getCommandsSent(models, targetModelsList)
    models.commandModel match {
      case None ⇒ None
      case Some(m) ⇒
        val desc = m.description
        if (desc.nonEmpty || sent.nonEmpty || received.nonEmpty)
          Some(Commands(HtmlMarkup.gfmToHtml(desc), received, sent))
        else None
    }
  }
}

