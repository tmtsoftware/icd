package csw.services.icd.db

import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
 * Support for creating instances of the shared (scala/scala.js) ComponentInfo class.
 * (This code can't be shared, since it accesses the database, which is on the server.)
 *
 * @param displayWarnings if true warn when no publishers are found for a subscribed event etc.
 */
//noinspection DuplicatedCode
class ComponentInfoHelper(displayWarnings: Boolean) {

  /**
   * Query the database for information about the subsystem's components
   *
   * @param versionManager used to access the database
   * @param sv the subsystem
   * @return a list of objects containing information about the components
   */
  def getComponentInfoList(
      versionManager: IcdVersionManager,
      sv: SubsystemWithVersion
  ): List[ComponentInfo] = {
    val compNames = sv.maybeComponent match {
      case None           => versionManager.getComponentNames(sv)
      case Some(compName) => List(compName)
    }
    compNames.flatMap(
      component => getComponentInfo(versionManager, SubsystemWithVersion(sv.subsystem, sv.maybeVersion, Some(component)))
    )
  }

  /**
   * Query the database for information about the given component
   *
   * @param versionManager    used to access the database
   * @param sv       the subsystem and component
   * @return an object containing information about the component, if found
   */
  def getComponentInfo(versionManager: IcdVersionManager, sv: SubsystemWithVersion): Option[ComponentInfo] = {
    // get the models for this component
    val modelsList = versionManager.getModels(sv)
    modelsList.headOption.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes      = getPublishes(versionManager.query, icdModels)
      val subscribes     = getSubscribes(versionManager.query, icdModels)
      val commands       = getCommands(versionManager.query, icdModels)
      componentModel.map { model =>
        ComponentInfo(model, publishes, subscribes, commands)
      }
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   *
   * @param query         database query handle
   * @param prefix        component's prefix
   * @param name          simple name of the published item
   * @param desc          description of the item
   * @param subscribeType event, alarm, etc...
   */
  private def getSubscribers(
      query: IcdDbQuery,
      prefix: String,
      name: String,
      desc: String,
      subscribeType: PublishType
  ): List[SubscribeInfo] = {
    query.subscribes(s"$prefix.$name", subscribeType).map { s =>
      SubscribeInfo(s.component, s.subscribeType, s.subscribeModelInfo)
    }
  }

  /**
   * Gets information about the items published by a component, along with a reference to the subscribers to each item
   *
   * @param query  database query handle
   * @param models the model objects for the component
   */
  private def getPublishes(query: IcdDbQuery, models: IcdModels): Option[Publishes] = {
    models.componentModel match {
      case None => None
      case Some(componentModel) =>
        val prefix = componentModel.prefix
        models.publishModel match {
          case None => None
          case Some(m) =>
            val eventList = m.eventList.map { t =>
              EventInfo(t, getSubscribers(query, prefix, t.name, t.description, Events))
            }
            val observeEventList = m.observeEventList.map { t =>
              EventInfo(t, getSubscribers(query, prefix, t.name, t.description, ObserveEvents))
            }
            val currentStateList = m.currentStateList.map { t =>
              EventInfo(t, getSubscribers(query, prefix, t.name, t.description, CurrentStates))
            }
            val alarmList = m.alarmList.map { al =>
              AlarmInfo(al, getSubscribers(query, prefix, al.name, al.description, Alarms))
            }
            if (m.description.nonEmpty || eventList.nonEmpty || observeEventList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(m.description, eventList, observeEventList, currentStateList, alarmList))
            else None
        }
    }
  }

  /**
   * Gets information about the items the component subscribes to, along with the publisher of each item
   *
   * @param query  the database query handle
   * @param models the model objects for the component
   */
  private def getSubscribes(query: IcdDbQuery, models: IcdModels): Option[Subscribes] = {

    // Gets additional information about the given subscription, including info from the publisher
    def getInfo(publishType: PublishType, si: SubscribeModelInfo): DetailedSubscribeInfo = {
      val x = for {
        t            <- query.getModels(si.subsystem, Some(si.component))
        publishModel <- t.publishModel
      } yield {
        val (maybeEvent, maybeAlarm) = publishType match {
          case Events        => (publishModel.eventList.find(t => t.name == si.name), None)
          case ObserveEvents => (publishModel.observeEventList.find(t => t.name == si.name), None)
          case CurrentStates => (publishModel.currentStateList.find(t => t.name == si.name), None)
          case Alarms        => (None, publishModel.alarmList.find(a => a.name == si.name))
        }
        DetailedSubscribeInfo(publishType, si, maybeEvent, maybeAlarm, t.componentModel, displayWarnings)
      }
      x.headOption.getOrElse(DetailedSubscribeInfo(publishType, si, None, None, None, displayWarnings))
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = m.eventList.map(getInfo(Events, _)) ++
          m.observeEventList.map(getInfo(ObserveEvents, _)) ++
          m.currentStateList.map(getInfo(CurrentStates, _)) ++
          m.alarmList.map(getInfo(Alarms, _))
        val desc = m.description
        if (desc.nonEmpty || subscribeInfo.nonEmpty)
          Some(Subscribes(desc, subscribeInfo))
        else None
    }
  }

  /**
   * Gets a list of commands received by the component, including information about which components
   * send each command.
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommandsReceived(query: IcdDbQuery, models: IcdModels): List[ReceivedCommandInfo] = {
    for {
      cmd      <- models.commandModel.toList
      received <- cmd.receive
    } yield {
      val senders = query.getCommandSenders(cmd.subsystem, cmd.component, received.name)
      ReceivedCommandInfo(received, senders)
    }
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommandsSent(query: IcdDbQuery, models: IcdModels): List[SentCommandInfo] = {
    val result = for {
      cmd  <- models.commandModel.toList
      sent <- cmd.send
    } yield {
      val recv = query.getCommand(sent.subsystem, sent.component, sent.name)
      SentCommandInfo(sent.name, sent.subsystem, sent.component, recv, query.getComponentModel(sent.subsystem, sent.component), displayWarnings)
    }
    result
  }

  /**
   * Gets a list of commands sent or received by the component
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommands(query: IcdDbQuery, models: IcdModels): Option[Commands] = {
    val received = getCommandsReceived(query, models)
    val sent     = getCommandsSent(query, models)
    models.commandModel match {
      case None => None
      case Some(m) =>
        val desc = m.description
        if (desc.nonEmpty || sent.nonEmpty || received.nonEmpty)
          Some(Commands(desc, received, sent))
        else None
    }
  }
}
