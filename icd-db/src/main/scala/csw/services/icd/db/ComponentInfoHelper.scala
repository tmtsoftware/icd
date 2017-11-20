package csw.services.icd.db

import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
 * Support for creating instances of the shared (scala/scala.js) ComponentInfo class.
 * (This code can't be shared, since it accesses the database, which is on the server.)
 */
object ComponentInfoHelper {

  /**
   * Query the database for information about the given components
   *
   * @param db         used to access the database
   * @param subsystem  the subsystem containing the component
   * @param versionOpt the version of the subsystem to use (determines the version of the component):
   *                   None for unpublished working version
   * @param compNames  list of component names to get information about
   * @return a list of objects containing information about the components
   */
  def getComponentInfoList(db: IcdDb, subsystem: String, versionOpt: Option[String], compNames: List[String]): List[ComponentInfo] = {
    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val query = new CachedIcdDbQuery(db.db)
    compNames.flatMap(getComponentInfo(query, subsystem, versionOpt, _))
  }

  /**
   * Query the database for information about the given component
   *
   * @param query      used to access the database
   * @param subsystem  the subsystem containing the component
   * @param versionOpt the version of the subsystem to use (determines the version of the component):
   *                   None for unpublished working version
   * @param compName   the component name
   * @return an object containing information about the component, if found
   */
  def getComponentInfo(query: IcdDbQuery, subsystem: String, versionOpt: Option[String], compName: String): Option[ComponentInfo] = {
    // get the models for this component
    val versionManager = IcdVersionManager(query.db, query)
    val modelsList = versionManager.getModels(subsystem, versionOpt, Some(compName))
    modelsList.headOption.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes = getPublishes(query, icdModels)
      val subscribes = getSubscribes(query, icdModels)
      val commands = getCommands(query, icdModels)
      componentModel.map { model => ComponentInfo(model, publishes, subscribes, commands) }
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   *
   * @param query         database query handle
   * @param prefix        component's prefix
   * @param name          simple name of the published item
   * @param desc          description of the item
   * @param subscribeType telemetry, alarm, etc...
   */
  private def getSubscribers(query: IcdDbQuery, prefix: String, name: String, desc: String,
                             subscribeType: PublishType): List[SubscribeInfo] = {
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
            val telemetryList = m.telemetryList.map { t =>
              TelemetryInfo(t, getSubscribers(query, prefix, t.name, t.description, Telemetry))
            }
            val eventList = m.eventList.map { t =>
              TelemetryInfo(t, getSubscribers(query, prefix, t.name, t.description, Events))
            }
            val eventStreamList = m.eventStreamList.map { t =>
              TelemetryInfo(t, getSubscribers(query, prefix, t.name, t.description, EventStreams))
            }
            val alarmList = m.alarmList.map { al =>
              AlarmInfo(al, getSubscribers(query, prefix, al.name, al.description, Alarms))
            }
            if (m.description.nonEmpty || telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(m.description, telemetryList, eventList, eventStreamList, alarmList))
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
        t <- query.getModels(si.subsystem, Some(si.component))
        publishModel <- t.publishModel
      } yield {
        val (telem, alarm) = publishType match {
          case Telemetry => (publishModel.telemetryList.find(t => t.name == si.name), None)
          case Events => (publishModel.eventList.find(t => t.name == si.name), None)
          case EventStreams => (publishModel.eventStreamList.find(t => t.name == si.name), None)
          case Alarms => (None, publishModel.alarmList.find(a => a.name == si.name))
        }
        DetailedSubscribeInfo(publishType, si, telem, alarm, t.componentModel)
      }
      x.headOption.getOrElse(DetailedSubscribeInfo(publishType, si, None, None, None))
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = m.telemetryList.map(getInfo(Telemetry, _)) ++
          m.eventList.map(getInfo(Events, _)) ++
          m.eventStreamList.map(getInfo(EventStreams, _)) ++
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
      cmd <- models.commandModel.toList
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
      cmd <- models.commandModel.toList
      sent <- cmd.send
    } yield {
      val recv = query.getCommand(sent.subsystem, sent.component, sent.name)
      SentCommandInfo(sent.name, sent.subsystem, sent.component, recv, query.getComponentModel(sent.subsystem, sent.component))
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
    val sent = getCommandsSent(query, models)
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

