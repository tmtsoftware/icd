package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.{ Alarms, EventStreams, Events, Health, PublishType, Telemetry }
import csw.services.icd.model.{ ComponentModel, IcdModels }
import icd.web.shared.{ CommandInfo, OtherComponent, SubscribeInfo, PublishInfo, ComponentInfo }

/**
 * Support for creating instances of the shared (scala/scala.js) ComponentInfo class.
 * (This code can't be shared, since it accesses the database, which is on the server.)
 */
object ComponentInfoHelper {
  /**
   * Query the database for information about the given component
   * @param db used to access the database
   * @param subsystem the subsystem containing the component
   * @param versionOpt the version of the subsystem to use (determines the version of the component):
   *                   None for unpublished working version
   * @param compName the component name
   * @return an object containing information about the component
   */
  def apply(db: IcdDb, subsystem: String, versionOpt: Option[String], compName: String): ComponentInfo = {
    // get the models for this component

    // Use caching, since we need to look at all the components multiple times, in order to determine who
    // subscribes, who calls commands, etc.
    val query = new CachedIcdDbQuery(db.db)
    val versionManager = new IcdVersionManager(db.db, query)

    val modelsList = db.versionManager.getModels(subsystem, versionOpt, Some(compName))
    val description = getComponentField(modelsList, _.description)
    val title = getComponentField(modelsList, _.title)
    val prefix = getComponentField(modelsList, _.prefix)
    val wbsId = getComponentField(modelsList, _.wbsId)
    val h = modelsList.headOption

    val publishInfo = h.map(getPublishInfo(query, _))
    val subscribeInfo = h.map(getSubscribeInfo(query, _))
    val commandsReceived = h.map(getCommandsReceived(query, _))
    val commandsSent = h.map(getCommandsSent(query, _))

    ComponentInfo(subsystem, compName, title, description, prefix, wbsId,
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
   * @param query database query handle
   * @param models the model objects for the component
   */
  private def getPublishInfo(query: IcdDbQuery, models: IcdModels): List[PublishInfo] = {
    models.componentModel match {
      case None ⇒ Nil
      case Some(componentModel) ⇒
        val prefix = componentModel.prefix
        val result = models.publishModel.map { m ⇒
          m.telemetryList.map { t ⇒
            PublishInfo("Telemetry", t.name, t.description, getSubscribers(query, prefix, t.name, t.description, Telemetry))
          } ++
            m.eventList.map { el ⇒
              PublishInfo("Event", el.name, el.description, getSubscribers(query, prefix, el.name, el.description, Events))
            } ++
            m.eventStreamList.map { esl ⇒
              PublishInfo("EventStream", esl.name, esl.description, getSubscribers(query, prefix, esl.name, esl.description, EventStreams))
            } ++
            m.alarmList.map { al ⇒
              PublishInfo("Alarm", al.name, al.description, getSubscribers(query, prefix, al.name, al.description, Alarms))
            } ++
            m.healthList.map { hl ⇒
              PublishInfo("Health", hl.name, hl.description, getSubscribers(query, prefix, hl.name, hl.description, Health))
            }
        }
        result.toList.flatten
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   * @param query database query handle
   * @param prefix component's prefix
   * @param name simple name of the published item
   * @param desc description of the item
   * @param subscribeType telemetry, alarm, etc...
   */
  private def getSubscribers(query: IcdDbQuery, prefix: String, name: String, desc: String,
                             subscribeType: PublishType): List[SubscribeInfo] = {
    query.subscribes(s"$prefix.$name", subscribeType).map { s ⇒
      SubscribeInfo(s.subscribeType.toString, s.name, desc, s.subsystem, s.componentName)
    }
  }

  /**
   * Gets a list of items the component subscribes to, along with the publisher of each item
   * @param query the database query handle
   * @param models the model objects for the component
   */
  private def getSubscribeInfo(query: IcdDbQuery, models: IcdModels): List[SubscribeInfo] = {

    def getInfo(publishType: PublishType, si: csw.services.icd.model.SubscribeInfo): List[SubscribeInfo] = {
      val info = query.publishes(si.name, si.subsystem, publishType).map { pi ⇒
        SubscribeInfo(publishType.toString, si.name, pi.item.description, si.subsystem, pi.componentName)
      }
      if (info.nonEmpty) info else {
        List(SubscribeInfo(publishType.toString, si.name, "", si.subsystem, ""))
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
   * Gets a list of commands received by the component, including information about which components
   * send each command.
   * @param query database query handle
   * @param models model objects for component
   */
  private def getCommandsReceived(query: IcdDbQuery, models: IcdModels): List[CommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = query.getCommandSenders(cmd.subsystem, cmd.component, received.name).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      CommandInfo(received.name, received.description, senders)
    }
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   * @param query database query handle
   * @param models model objects for component
   */
  private def getCommandsSent(query: IcdDbQuery, models: IcdModels): List[CommandInfo] = {
    val result = for {
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      query.getCommand(sent.subsystem, sent.component, sent.name).map { r ⇒
        CommandInfo(sent.name, r.description, List(OtherComponent(sent.subsystem, sent.component)))
      }
    }
    result.flatten
  }
}

