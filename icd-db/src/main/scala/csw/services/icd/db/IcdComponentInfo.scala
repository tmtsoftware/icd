package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.{ Published, PublishInfo, PublishedItem, Subscribed, PublishType, Health, Alarms, EventStreams, Events, Telemetry }
import csw.services.icd.model.{ ReceiveCommandModel, ComponentModel, IcdModels }
import shared.{ CommandInfo, OtherComponent }

/**
 * Gathers information related to a component in a given version of an ICD.
 * Only information related to the component and the target subsystem version is included.
 */
object IcdComponentInfo {
  /**
   * Query the database for information about the given component
   * @param db used to access the database
   * @param subsystem the subsystem containing the component
   * @param versionOpt the version of the subsystem to use (determines the version of the component):
   *                   None for unpublished working version
   * @param compName the component name
   * @param target the target subsystem of the ICD
   * @param targetVersionOpt the version of the target subsystem to use
   * @return an object containing information about the component
   */
  def apply(db: IcdDb, subsystem: String, versionOpt: Option[String], compName: String,
            target: String, targetVersionOpt: Option[String]): shared.ComponentInfo = {
    // get the models for this component
    val modelsList = db.versionManager.getModels(subsystem, versionOpt, Some(compName))
    val targetModelsList = db.versionManager.getModels(target, targetVersionOpt, None)
    val description = getComponentField(modelsList, _.description)
    val prefix = getComponentField(modelsList, _.prefix)
    val wbsId = getComponentField(modelsList, _.wbsId)
    val h = modelsList.headOption
    val publishInfo = h.map(getPublishInfo(subsystem, _, targetModelsList))
    val subscribeInfo = h.map(getSubscribeInfo(_, targetModelsList))
    val commandsReceived = h.map(getCommandsReceived(_, targetModelsList))
    val commandsSent = h.map(getCommandsSent(_, targetModelsList))

    shared.ComponentInfo(subsystem, compName, description, prefix, wbsId,
      publishInfo.toList.flatten,
      subscribeInfo.toList.flatten,
      commandsReceived.getOrElse(Nil),
      commandsSent.getOrElse(Nil))
  }

  /**
   * Gets a string value from the component description, or an empty string if not found
   * @param modelsList list of model sets for the component
   * @param f function to get the value
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
   * Gets information about the items published by a component
   * @param subsystem: the source subsystem
   * @param models the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getPublishInfo(subsystem: String, models: IcdModels, targetModelsList: List[IcdModels]): List[shared.PublishInfo] = {
    models.componentModel match {
      case None ⇒ Nil
      case Some(componentModel) ⇒
        val prefix = componentModel.prefix
        val result = models.publishModel.map { m ⇒
          m.telemetryList.map { t ⇒
            shared.PublishInfo("Telemetry", t.name, t.description,
              getSubscribers(subsystem, prefix, t.name, t.description, Telemetry, targetModelsList))
          } ++
            m.eventList.map { el ⇒
              shared.PublishInfo("Event", el.name, el.description,
                getSubscribers(subsystem, prefix, el.name, el.description, Events, targetModelsList))
            } ++
            m.eventStreamList.map { esl ⇒
              shared.PublishInfo("EventStream", esl.name, esl.description,
                getSubscribers(subsystem, prefix, esl.name, esl.description, EventStreams, targetModelsList))
            } ++
            m.alarmList.map { al ⇒
              shared.PublishInfo("Alarm", al.name, al.description,
                getSubscribers(subsystem, prefix, al.name, al.description, Alarms, targetModelsList))
            } ++
            m.healthList.map { hl ⇒
              shared.PublishInfo("Health", hl.name, hl.description,
                getSubscribers(subsystem, prefix, hl.name, hl.description, Health, targetModelsList))
            }
        }
        result.toList.flatten
    }
  }

  /**
   * Returns the object if the component subscribes to the given value.
   * @param subscriberSubsystem the subscriber's subsystem
   * @param subscriberCompName the subscriber's component name
   * @param path full path name of value (prefix + name)
   * @param targetInfo list of items the target subscribes to
   * @param subscribeType telemetry, alarm, etc...
   * @return the Subscribed object, if the component is a subscriber to the given path
   */
  private def subscribes(subscriberSubsystem: String, subscriberCompName: String, publisherSubsystem: String,
                         path: String, targetInfo: List[csw.services.icd.model.SubscribeInfo],
                         subscribeType: PublishType): Option[Subscribed] = {
    targetInfo.find { subscribeInfo ⇒
      subscribeInfo.name == path && subscribeInfo.subsystem == publisherSubsystem
    }.map { _ ⇒
      Subscribed(subscriberCompName, subscriberSubsystem, subscribeType, path, publisherSubsystem)
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   * @param subsystem the publisher's subsystem
   * @param prefix component's prefix
   * @param name simple name of the published item
   * @param desc description of the item
   * @param subscribeType telemetry, alarm, etc...
   * @param targetModelsList the target model objects
   */
  private def getSubscribers(subsystem: String, prefix: String, name: String, desc: String, subscribeType: PublishType,
                             targetModelsList: List[IcdModels]): List[shared.SubscribeInfo] = {
    for {
      icdModel ← targetModelsList
      subscribeModel ← icdModel.subscribeModel
      s ← subscribes(subscribeModel.subsystem, subscribeModel.component,
        subsystem, s"$prefix.$name", subscribeModel.telemetryList, subscribeType)
    } yield {
      shared.SubscribeInfo(s.subscribeType.toString, s.name, desc, s.subsystem, s.componentName)
    }
  }

  /**
   * Returns a list describing what each component publishes
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
        publishModel.alarmList.map(i ⇒ Published(Alarms, i.name, i.description)),
        publishModel.healthList.map(i ⇒ Published(Health, i.name, i.description))).flatten
      PublishInfo(publishModel.component, componentModel.prefix, publishedList)
    }
  }

  /**
   * Returns a list describing which components publish the given value.
   * @param path full path name of value (prefix + name)
   * @param publishType telemetry, alarm, etc...
   * @param targetModelsList the target model objects
   */
  private def publishes(path: String, publishType: PublishType, targetModelsList: List[IcdModels]): List[PublishedItem] = {
    for {
      i ← getPublishInfo(targetModelsList)
      p ← i.publishes.filter(p ⇒ s"${i.prefix}.${p.name}" == path && publishType == p.publishType)
    } yield PublishedItem(i.componentName, i.prefix, p)
  }

  /**
   * Gets a list of items the component subscribes to, along with the publisher of each item
   * @param models the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getSubscribeInfo(models: IcdModels, targetModelsList: List[IcdModels]): List[shared.SubscribeInfo] = {
    def getInfo(publishType: PublishType, si: csw.services.icd.model.SubscribeInfo): List[shared.SubscribeInfo] = {
      val info = publishes(si.name, publishType, targetModelsList).map { pi ⇒
        shared.SubscribeInfo(publishType.toString, si.name, pi.item.description, si.subsystem, pi.componentName)
      }
      if (info.nonEmpty) info
      else {
        List(shared.SubscribeInfo(publishType.toString, si.name, "", si.subsystem, ""))
      }
    }

    val result = models.subscribeModel.map { m ⇒
      m.telemetryList.map(getInfo(Telemetry, _)) ++
        m.eventList.map(getInfo(Events, _)) ++
        m.eventStreamList.map(getInfo(EventStreams, _)) ++
        m.alarmList.map(getInfo(Alarms, _)) ++
        m.healthList.map(getInfo(Health, _))
    }
    result.toList.flatten.flatten
  }

  /**
   * Returns a list of the names of target components that send the given command to the given component/subsystem
   * @param subsystem the subsystem that contains the component that defines the command
   * @param component the component that defines the command
   * @param commandName the command name
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
   * @param models the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getCommandsReceived(models: IcdModels, targetModelsList: List[IcdModels]): List[CommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = getCommandSenders(cmd.subsystem, cmd.component, received.name, targetModelsList).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      CommandInfo(received.name, received.description, senders)
    }
  }

  /**
   * Returns an object describing a command, defined to be received by the given component in the given subsystem,
   * if found.
   * @param subsystem the subsystem that contains the component that defines the command
   * @param component the component that defines the command
   * @param commandName the command name
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
   * @param models the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getCommandsSent(models: IcdModels, targetModelsList: List[IcdModels]): List[CommandInfo] = {
    val result = for {
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      getCommand(sent.subsystem, sent.component, sent.name, targetModelsList).map { r ⇒
        CommandInfo(sent.name, r.description, List(OtherComponent(sent.subsystem, sent.component)))
      }
    }
    result.flatten
  }
}

