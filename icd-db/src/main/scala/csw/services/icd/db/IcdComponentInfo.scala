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
    compNames.flatMap(getComponentInfo(query, subsystem, versionOpt, _, target, targetVersionOpt))
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
                       target: String, targetVersionOpt: Option[String]): Option[ComponentInfo] = {
    // get the models for this component
    val versionManager = new CachedIcdVersionManager(query)
    val modelsList = versionManager.getModels(subsystem, versionOpt, Some(compName))
    val targetModelsList = versionManager.getModels(target, targetVersionOpt, None)

    modelsList.headOption.flatMap { icdModels ⇒
      val componentModel = icdModels.componentModel
      val publishes = getPublishes(subsystem, icdModels, targetModelsList)
      val subscribes = getSubscribes(icdModels, targetModelsList)
      val commands = getCommands(icdModels, targetModelsList)

      componentModel.map { model ⇒ ComponentInfo(model, publishes, subscribes, commands) }
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
      case None ⇒ None
      case Some(componentModel) ⇒
        val prefix = componentModel.prefix
        val component = componentModel.component
        models.publishModel match {
          case None ⇒ None
          case Some(m) ⇒
            val telemetryList = m.telemetryList.map { t ⇒
              TelemetryInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, Telemetry, targetModelsList))
            }
            val eventList = m.eventList.map { t ⇒
              TelemetryInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, EventStreams, targetModelsList))
            }
            val eventStreamList = m.eventStreamList.map { t ⇒
              TelemetryInfo(t, getSubscribers(subsystem, component, prefix, t.name, t.description, EventStreams, targetModelsList))
            }
            val alarmList = m.alarmList.map { al ⇒
              AlarmInfo(al, getSubscribers(subsystem, component, prefix, al.name, al.description, Alarms, targetModelsList))
            }
            if (telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(m.description, telemetryList, eventList, eventStreamList, alarmList))
            else None
        }
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   *
   * @param subsystem        the publisher's subsystem
   * @param component        the publisher's component
   * @param prefix           the publisher component's prefix
   * @param name             simple name of the published item
   * @param desc             description of the item
   * @param pubType          One of Telemetry, Event, EventStream, Alarm.
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
    def subscribes(targetInfo: List[SubscribeModelInfo]): Option[Subscribed] = {
      targetInfo.find { subscribeInfo ⇒
        subscribeInfo.name == name && subscribeInfo.subsystem == subsystem && subscribeInfo.component == component
      }.map { subscribeInfo ⇒
        Subscribed(subscribeInfo, pubType, path)
      }
    }

    // Gets the list of subscribed items from the model given the publish type
    def getSubscribeInfoByType(subscribeModel: SubscribeModel, pubType: PublishType): List[SubscribeModelInfo] = {
      pubType match {
        case Telemetry    ⇒ subscribeModel.telemetryList
        case Events       ⇒ subscribeModel.eventList
        case EventStreams ⇒ subscribeModel.eventStreamList
        case Alarms       ⇒ subscribeModel.alarmList
      }
    }

    for {
      icdModel ← targetModelsList
      subscribeModel ← icdModel.subscribeModel
      s ← subscribes(getSubscribeInfoByType(subscribeModel, pubType))
    } yield {
      SubscribeInfo(s.subscribeType, s.subscribeModelInfo)
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
        t ← targetModelsList
        componentModel ← t.componentModel
        if componentModel.component == si.component && componentModel.subsystem == si.subsystem
        publishModel ← t.publishModel
      } yield {
        val (telem, alarm) = publishType match {
          case Alarms ⇒ (None, publishModel.alarmList.find(a ⇒ a.name == si.name))
          case _      ⇒ (publishModel.telemetryList.find(t ⇒ t.name == si.name), None)
        }
        DetailedSubscribeInfo(publishType, si, telem, alarm, componentModel)
      }
      x.headOption
    }

    models.subscribeModel match {
      case None ⇒ None
      case Some(m) ⇒
        val subscribeInfo = m.telemetryList.map(getInfo(Telemetry, _)) ++
          m.eventList.map(getInfo(Events, _)) ++
          m.eventStreamList.map(getInfo(EventStreams, _)) ++
          m.alarmList.map(getInfo(Alarms, _))
        val desc = m.description
        if (subscribeInfo.nonEmpty)
          Some(Subscribes(desc, subscribeInfo.flatten))
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
        SentCommandInfo(r, Some(OtherComponent(sent.subsystem, sent.component)))
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
        if (sent.nonEmpty || received.nonEmpty)
          Some(Commands(m.description, received, sent))
        else None
    }
  }
}

