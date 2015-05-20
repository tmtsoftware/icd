package controllers

import csw.services.icd.db.IcdDb
import csw.services.icd.db.IcdDbQuery.{ Health, Alarms, Events, EventStreams, Telemetry, PublishType }
import csw.services.icd.model.IcdModels
import shared.{ OtherComponent, CommandInfo, PublishInfo, SubscribeInfo }

object ComponentInfo {
  /**
   * Query the database for information about the given component
   */
  def apply(db: IcdDb, compName: String): shared.ComponentInfo = {
    // get the models for this component and it's subcomponents
    val modelsList = db.query.getModels(compName)
    val description = getDescription(modelsList)
    val publishInfo = for (models ← modelsList.headOption) yield {
      getPublishInfo(db, models)
    }
    val subscribeInfo = for (models ← modelsList.headOption) yield {
      getSubscribeInfo(db, models)
    }
    val commandsReceived = for (models ← modelsList.headOption) yield {
      getCommandsReceived(db, models)
    }
    val commandsSent = for (models ← modelsList.headOption) yield {
      getCommandsSent(db, models)
    }

    shared.ComponentInfo(compName, description,
      publishInfo.toList.flatten,
      subscribeInfo.toList.flatten,
      commandsReceived.getOrElse(Nil),
      commandsSent.getOrElse(Nil))
  }

  /**
   * Gets the component description, or an empty string if not found
   * @param modelsList list of model sets for the subsystem or component
   */
  private def getDescription(modelsList: List[IcdModels]): String = {
    if (modelsList.isEmpty) ""
    else {
      modelsList.head.componentModel match {
        case Some(model) ⇒ model.description
        case None        ⇒ ""
      }
    }
  }

  /**
   * Gets information about the items published by a component
   * @param db database handle
   * @param models the model objects for the component
   */
  private def getPublishInfo(db: IcdDb, models: IcdModels): List[PublishInfo] = {
    val prefix = models.componentModel.get.prefix
    val result = models.publishModel.map { m ⇒
      m.telemetryList.map { t ⇒
        PublishInfo("Telemetry", t.name, t.description, getSubscribers(db, prefix, t.name, t.description, Telemetry))
      } ++
        m.eventList.map { el ⇒
          PublishInfo("Event", el.name, el.description, getSubscribers(db, prefix, el.name, el.description, Events))
        } ++
        m.eventStreamList.map { esl ⇒
          PublishInfo("EventStream", esl.name, esl.description, getSubscribers(db, prefix, esl.name, esl.description, EventStreams))
        } ++
        m.alarmList.map { al ⇒
          PublishInfo("Alarm", al.name, al.description, getSubscribers(db, prefix, al.name, al.description, Alarms))
        } ++
        m.healthList.map { hl ⇒
          PublishInfo("Health", hl.name, hl.description, getSubscribers(db, prefix, hl.name, hl.description, Health))
        }
    }
    result.toList.flatten
  }

  /**
   * Gets information about who subscribes to the given published items
   * @param db database handle
   * @param prefix component's prefix
   * @param name simple name of the published item
   * @param desc description of the item
   * @param subscribeType telemetry, alarm, etc...
   */
  private def getSubscribers(db: IcdDb, prefix: String, name: String, desc: String,
                             subscribeType: PublishType): List[SubscribeInfo] = {
    db.query.subscribes(s"$prefix.$name", subscribeType).map { s ⇒
      SubscribeInfo(s.subscribeType.toString, s.name, desc, s.subsystem, s.componentName)
    }
  }

  /**
   * Gets a list of items the component subscribes to, along with the publisher of each item
   * @param db the database handle
   * @param models the model objects for the component
   */
  private def getSubscribeInfo(db: IcdDb, models: IcdModels): List[SubscribeInfo] = {

    def getInfo(publishType: PublishType, si: csw.services.icd.model.SubscribeInfo): List[SubscribeInfo] = {
      val info = db.query.publishes(si.name, publishType).map { pi ⇒
        SubscribeInfo(publishType.toString, si.name, pi.item.description, si.subsystem, pi.componentName)
      }
      if (info.nonEmpty) info else List(SubscribeInfo(publishType.toString, si.name, "", si.subsystem, ""))
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
   * @param db database handle
   * @param models model objects for component
   */
  private def getCommandsReceived(db: IcdDb, models: IcdModels): List[CommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = db.query.getCommandSenders(cmd.subsystem, cmd.component, received.name).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      CommandInfo(received.name, received.description, senders)
    }
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   * @param db database handle
   * @param models model objects for component
   */
  private def getCommandsSent(db: IcdDb, models: IcdModels): List[CommandInfo] = {
    val result = for {
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      db.query.getCommand(sent.subsystem, sent.component, sent.name).map { r ⇒
        CommandInfo(sent.name, r.description, List(OtherComponent(sent.subsystem, sent.component)))
      }
    }
    result.flatten
  }
}

