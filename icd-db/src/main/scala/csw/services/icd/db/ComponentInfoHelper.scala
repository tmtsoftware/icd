package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.{Alarms, EventStreams, Events, PublishType, Telemetry}
import csw.services.icd.html.HtmlMarkup
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
    modelsList.headOption.flatMap { icdModels ⇒
      val componentModel = icdModels.componentModel
      val publishes = getPublishes(query, icdModels)
      val subscribes = getSubscribes(query, icdModels)
      val commands = getCommands(query, icdModels)

      componentModel.map { model ⇒
        ComponentInfo(
          model.copy(description = HtmlMarkup.gfmToHtml(model.description)),
          publishes,
          subscribes,
          commands
        )
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
   * @param subscribeType telemetry, alarm, etc...
   */
  private def getSubscribers(query: IcdDbQuery, prefix: String, name: String, desc: String,
                             subscribeType: PublishType): List[SubscribeInfo] = {
    query.subscribes(s"$prefix.$name", subscribeType).map { s ⇒
      val sf = subscribeModelInfoMarkup(s.subscribeModelInfo)
      SubscribeInfo(s.subscribeType.toString, sf, s.path, HtmlMarkup.gfmToHtml(desc))
    }
  }

  // Replaces description fields with markdown formatted HTML
  private[db] def attributeMarkup(a: AttributeModel): AttributeModel = {
    a.copy(description = HtmlMarkup.gfmToHtml(a.description))
  }
  private[db] def telemtryMarkup(t: TelemetryModel): TelemetryModel = {
    t.copy(description = HtmlMarkup.gfmToHtml(t.description), attributesList = t.attributesList.map(attributeMarkup))
  }
  private[db] def alarmMarkup(t: AlarmModel): AlarmModel = {
    t.copy(description = HtmlMarkup.gfmToHtml(t.description))
  }
  private[db] def subscribeModelInfoMarkup(t: SubscribeModelInfo): SubscribeModelInfo = {
    t.copy(usage = HtmlMarkup.gfmToHtml(t.usage))
  }

  /**
   * Gets information about the items published by a component, along with a reference to the subscribers to each item
   *
   * @param query  database query handle
   * @param models the model objects for the component
   */
  private def getPublishes(query: IcdDbQuery, models: IcdModels): Option[Publishes] = {
    models.componentModel match {
      case None ⇒ None
      case Some(componentModel) ⇒
        val prefix = componentModel.prefix
        models.publishModel match {
          case None ⇒ None
          case Some(m) ⇒
            val desc = HtmlMarkup.gfmToHtml(m.description)
            val telemetryList = m.telemetryList.map { t ⇒
              val tf = telemtryMarkup(t)
              TelemetryInfo(tf, getSubscribers(query, prefix, tf.name, tf.description, Telemetry))
            }
            val eventList = m.eventList.map { t ⇒
              val tf = telemtryMarkup(t)
              TelemetryInfo(tf, getSubscribers(query, prefix, tf.name, tf.description, EventStreams))
            }
            val eventStreamList = m.eventStreamList.map { t ⇒
              val tf = telemtryMarkup(t)
              TelemetryInfo(tf, getSubscribers(query, prefix, tf.name, tf.description, EventStreams))
            }
            val alarmList = m.alarmList.map { al ⇒
              val af = alarmMarkup(al)
              AlarmInfo(af, getSubscribers(query, prefix, af.name, af.description, Alarms))
            }
            if (desc.nonEmpty || telemetryList.nonEmpty || eventList.nonEmpty || eventStreamList.nonEmpty || alarmList.nonEmpty)
              Some(Publishes(desc, telemetryList, eventList, eventStreamList, alarmList))
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

    def getInfo(publishType: PublishType, si: SubscribeModelInfo): List[SubscribeInfo] = {
      val prefix = query.getPrefix(si.subsystem, si.component)
      val path = s"$prefix.${si.name}"
      val sf = subscribeModelInfoMarkup(si)
      val info = query.publishes(path, si.subsystem, publishType).map { pi ⇒
        SubscribeInfo(publishType.toString, sf, s"${pi.prefix}.${si.name}", HtmlMarkup.gfmToHtml(pi.item.description))
      }
      if (info.nonEmpty) info
      else {
        val prefix = query.getPrefix(si.subsystem, si.component)
        List(SubscribeInfo(publishType.toString, sf, s"$prefix.${si.name}", ""))
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
   * Gets a list of commands received by the component, including information about which components
   * send each command.
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommandsReceived(query: IcdDbQuery, models: IcdModels): List[ReceivedCommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = query.getCommandSenders(cmd.subsystem, cmd.component, received.name).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      val desc = HtmlMarkup.gfmToHtml(received.description)
      ReceivedCommandInfo(received.name, desc, senders, received.requirements, received.requiredArgs,
        received.args.map(attributeMarkup))
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
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      query.getCommand(sent.subsystem, sent.component, sent.name).map { r ⇒
        SentCommandInfo(sent.name, HtmlMarkup.gfmToHtml(r.description),
          List(OtherComponent(sent.subsystem, sent.component)))
      }
    }
    result.flatten
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
      case None ⇒ None
      case Some(m) ⇒
        val desc = m.description
        if (desc.nonEmpty || sent.nonEmpty || received.nonEmpty)
          Some(Commands(HtmlMarkup.gfmToHtml(desc), received, sent))
        else None
    }
  }
}

