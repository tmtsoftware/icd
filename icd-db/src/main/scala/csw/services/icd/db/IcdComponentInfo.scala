package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.Subscribed
import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
  * Gathers information related to a component in a given version of an ICD.
  * Only information related to the component and the target subsystem version is included.
  */
object IcdComponentInfo {
  /**
    * Query the database for information about the given components in an ICD
    *
    * @param db                used to access the database
    * @param subsystem         the subsystem containing the component
    * @param versionOpt        the version of the subsystem to use (determines the version of the component):
    *                          None for unpublished working version
    * @param compNames         the component names
    * @param target            the target subsystem of the ICD
    * @param targetVersionOpt  the version of the target subsystem to use
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @return an object containing information about the component
    */
  def getComponentInfoList(db: IcdDb, subsystem: String, versionOpt: Option[String], compNames: List[String],
                           target: String, targetVersionOpt: Option[String],
                           targetCompNameOpt: Option[String] = None): List[ComponentInfo] = {
    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val query = new CachedIcdDbQuery(db.db)
    compNames.flatMap(getComponentInfo(query, subsystem, versionOpt, _, target, targetVersionOpt, targetCompNameOpt))
      .map(ComponentInfo.applyIcdFilter)
  }

  /**
    * Query the database for information about the given component
    *
    * @param query             used to access the database
    * @param subsystem         the subsystem containing the component
    * @param versionOpt        the version of the subsystem to use (determines the version of the component):
    *                          None for unpublished working version
    * @param compName          the component name
    * @param target            the target subsystem of the ICD
    * @param targetVersionOpt  the version of the target subsystem to use
    * @param targetCompNameOpt optional name of target component (default is to use all target components)
    * @return an object containing information about the component
    */
  def getComponentInfo(query: IcdDbQuery, subsystem: String, versionOpt: Option[String], compName: String,
                       target: String, targetVersionOpt: Option[String],
                       targetCompNameOpt: Option[String]): Option[ComponentInfo] = {
    // get the models for this component
    val versionManager = new CachedIcdVersionManager(query)
    val modelsList = versionManager.getModels(subsystem, versionOpt, Some(compName))
    val targetModelsList = versionManager.getModels(target, targetVersionOpt, targetCompNameOpt)

   modelsList.headOption.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes = getPublishes(subsystem, icdModels, targetModelsList)
      val subscribes = getSubscribes(icdModels, targetModelsList)
      val commands = getCommands(query, icdModels, targetModelsList)

      if (publishes.isDefined || subscribes.isDefined || commands.isDefined)
        componentModel.map(ComponentInfo(_, publishes, subscribes, commands))
      else None
    }
  }

  /**
    * Gets information about the items published by a component
    *
    * @param subsystem        the source (publisher) subsystem
    * @param models           the model objects for the component
    * @param targetModelsList the target model objects
    */
  private def getPublishes(subsystem: String, models: IcdModels, targetModelsList: List[IcdModels]): Option[Publishes] = {
    models.componentModel match {
      case None => None
      case Some(componentModel) =>
        val prefix = componentModel.prefix
        val component = componentModel.component
        models.publishModel match {
          case None => None
          case Some(m) =>
            val eventList = m.eventList.map { t =>
              EventInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, Events, targetModelsList))
            }
            val observeEventList = m.observeEventList.map { t =>
              EventInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, ObserveEvents, targetModelsList))
            }
            val currentStateList = m.currentStateList.map { t =>
              EventInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, CurrentStates, targetModelsList))
            }
            val alarmList = m.alarmList.map { al =>
              AlarmInfo(al, getSubscribers(subsystem, component, prefix, al.name, al.description, Alarms, targetModelsList))
            }
            if (eventList.nonEmpty || observeEventList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(m.description, eventList, observeEventList, currentStateList, alarmList))
            else None
        }
    }
  }

  /**
    * Gets information about who subscribes to the given published item
    *
    * @param subsystem        the publisher's subsystem
    * @param component        the publisher's component
    * @param prefix           the publisher component's prefix
    * @param name             simple name of the published item
    * @param desc             description of the item
    * @param pubType          One of Event, ObserveEvent, Alarm.
    * @param targetModelsList the target model objects for the ICD
    */
  private def getSubscribers(subsystem: String, component: String, prefix: String, name: String, desc: String,
                             pubType: PublishType, targetModelsList: List[IcdModels]): List[SubscribeInfo] = {

    // Full path of the published item
    val path = s"$prefix.$name"

    // Returns the object if the component subscribes to the given value.
    //
    // targetInfo: list of items the target subscriber subscribes to
    // Returns the Subscribed object, if the component is a subscriber to the given path
    def subscribes(componentModel: ComponentModel, targetInfo: List[SubscribeModelInfo]): Option[Subscribed] = {
      targetInfo.find { subscribeInfo =>
        subscribeInfo.name == name && subscribeInfo.subsystem == subsystem && subscribeInfo.component == component
      }.map { subscribeInfo =>
        Subscribed(componentModel, subscribeInfo, pubType, path)
      }
    }

    // Gets the list of subscribed items from the model given the publish type
    def getSubscribeInfoByType(subscribeModel: SubscribeModel, pubType: PublishType): List[SubscribeModelInfo] = {
      pubType match {
        case Events => subscribeModel.eventList
        case ObserveEvents => subscribeModel.observeEventList
        case CurrentStates => subscribeModel.currentStateList
        case Alarms => subscribeModel.alarmList
      }
    }

    for {
      icdModel <- targetModelsList
      componentModel <- icdModel.componentModel
      subscribeModel <- icdModel.subscribeModel
      s <- subscribes(componentModel, getSubscribeInfoByType(subscribeModel, pubType))
    } yield {
      SubscribeInfo(componentModel, s.subscribeType, s.subscribeModelInfo)
    }
  }

  /**
    * Gets information about the items the component subscribes to, along with the publisher of each item
    *
    * @param models           the model objects for the component
    * @param targetModelsList the target model objects
    */
  private def getSubscribes(models: IcdModels, targetModelsList: List[IcdModels]): Option[Subscribes] = {

    // Gets additional information about the given subscription, including info from the publisher
    def getInfo(publishType: PublishType, si: SubscribeModelInfo): Option[DetailedSubscribeInfo] = {
      val x = for {
        t <- targetModelsList
        componentModel <- t.componentModel
        if componentModel.component == si.component && componentModel.subsystem == si.subsystem
        publishModel <- t.publishModel
      } yield {
        val (telem, alarm) = publishType match {
          case Events => (publishModel.eventList.find(t => t.name == si.name), None)
          case ObserveEvents => (publishModel.observeEventList.find(t => t.name == si.name), None)
          case CurrentStates => (publishModel.currentStateList.find(t => t.name == si.name), None)
          case Alarms => (None, publishModel.alarmList.find(a => a.name == si.name))
        }
        DetailedSubscribeInfo(publishType, si, telem, alarm, Some(componentModel))
      }
      x.headOption
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = (
          m.eventList.map(getInfo(Events, _)) ++
          m.observeEventList.map(getInfo(ObserveEvents, _)) ++
          m.currentStateList.map(getInfo(CurrentStates, _)) ++
          m.alarmList.map(getInfo(Alarms, _))).flatten
        val desc = m.description
        if (subscribeInfo.nonEmpty)
          Some(Subscribes(desc, subscribeInfo))
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
      icdModels <- targetModelsList
      componentModel <- icdModels.componentModel
      commandModel <- icdModels.commandModel
      if commandModel.send.exists(s => s.subsystem == subsystem && s.component == component && s.name == commandName)
    } yield {
      componentModel
    }
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
      cmd <- models.commandModel.toList
      received <- cmd.receive
    } yield {
      val senders = getCommandSenders(cmd.subsystem, cmd.component, received.name, targetModelsList)
      ReceivedCommandInfo(received, senders)
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
      icdModels <- targetModelsList
      commandModel <- icdModels.commandModel
      if commandModel.subsystem == subsystem && commandModel.component == component
      receiveCommandModel <- commandModel.receive.find(_.name == commandName)
    } yield receiveCommandModel

    result.headOption
  }

  /**
    * Gets a list of commands sent by the component, including information about the components
    * that receive each command.
    *
    * @param query            used to query the db
    * @param models           the model objects for the component
    * @param targetModelsList the target model objects
    */
  private def getCommandsSent(query: IcdDbQuery, models: IcdModels, targetModelsList: List[IcdModels]): List[SentCommandInfo] = {
    val result = for {
      cmd <- models.commandModel.toList
      sent <- cmd.send
      recv <- getCommand(sent.subsystem, sent.component, sent.name, targetModelsList)
    } yield {
      SentCommandInfo(sent.name, sent.subsystem, sent.component, Some(recv),
        query.getComponentModel(sent.subsystem, sent.component))
    }
    result
  }

  /**
    * Gets a list of commands sent or received by the component.
    *
    * @param query            used to query the db
    * @param models           the model objects for the component
    * @param targetModelsList the target model objects
    */
  private def getCommands(query: IcdDbQuery, models: IcdModels, targetModelsList: List[IcdModels]): Option[Commands] = {
    val received = getCommandsReceived(models, targetModelsList)
    val sent = getCommandsSent(query, models, targetModelsList)
    models.commandModel match {
      case None => None
      case Some(m) =>
        if (sent.nonEmpty || received.nonEmpty)
          Some(Commands(m.description, received, sent))
        else None
    }
  }
}

