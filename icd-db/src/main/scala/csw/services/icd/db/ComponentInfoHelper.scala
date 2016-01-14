package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.{ Alarms, EventStreams, Events, PublishType, Telemetry }
import csw.services.icd.html.HtmlMarkup
import csw.services.icd.model.{ ComponentModel, IcdModels }
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
    compNames.map(getComponentInfo(query, subsystem, versionOpt, _))
  }

  /**
   * Query the database for information about the given component
   *
   * @param query      used to access the database
   * @param subsystem  the subsystem containing the component
   * @param versionOpt the version of the subsystem to use (determines the version of the component):
   *                   None for unpublished working version
   * @param compName   the component name
   * @return an object containing information about the component
   */
  def getComponentInfo(query: IcdDbQuery, subsystem: String, versionOpt: Option[String], compName: String): ComponentInfo = {
    // get the models for this component
    val versionManager = IcdVersionManager(query.db, query)
    val modelsList = versionManager.getModels(subsystem, versionOpt, Some(compName))
    val description = getComponentField(modelsList, _.description)
    val title = getComponentField(modelsList, _.title)
    val prefix = getComponentField(modelsList, _.prefix)
    val componentType = getComponentField(modelsList, _.componentType)
    val wbsId = getComponentField(modelsList, _.wbsId)
    val h = modelsList.headOption

    val publishes = h.flatMap(getPublishes(query, _))
    val subscribes = h.flatMap(getSubscribes(query, _))
    val commands = h.flatMap(getCommands(query, _))

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
            val publishInfo = m.telemetryList.map { t ⇒
              PublishInfo("Telemetry", t.name, HtmlMarkup.gfmToHtml(t.description),
                getSubscribers(query, prefix, t.name, t.description, Telemetry))
            } ++
              m.eventList.map { el ⇒
                PublishInfo("Event", el.name, HtmlMarkup.gfmToHtml(el.description),
                  getSubscribers(query, prefix, el.name, el.description, Events))
              } ++
              m.eventStreamList.map { esl ⇒
                PublishInfo("EventStream", esl.name, HtmlMarkup.gfmToHtml(esl.description),
                  getSubscribers(query, prefix, esl.name, esl.description, EventStreams))
              } ++
              m.alarmList.map { al ⇒
                PublishInfo("Alarm", al.name, HtmlMarkup.gfmToHtml(al.description),
                  getSubscribers(query, prefix, al.name, al.description, Alarms))
              }
            val desc = m.description
            if (desc.nonEmpty || publishInfo.nonEmpty)
              Some(Publishes(HtmlMarkup.gfmToHtml(desc), publishInfo))
            else None
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
      SubscribeInfo(s.subscribeType.toString, s.name, HtmlMarkup.gfmToHtml(desc),
        HtmlMarkup.gfmToHtml(s.usage), s.subsystem, s.componentName)
    }
  }

  /**
   * Gets information about the items the component subscribes to, along with the publisher of each item
   *
   * @param query  the database query handle
   * @param models the model objects for the component
   */
  private def getSubscribes(query: IcdDbQuery, models: IcdModels): Option[Subscribes] = {

    def getInfo(publishType: PublishType, si: csw.services.icd.model.SubscribeInfo): List[SubscribeInfo] = {
      val info = query.publishes(si.name, si.subsystem, publishType).map { pi ⇒
        SubscribeInfo(publishType.toString, si.name, HtmlMarkup.gfmToHtml(pi.item.description),
          HtmlMarkup.gfmToHtml(si.usage), si.subsystem, pi.componentName)
      }
      if (info.nonEmpty) info
      else {
        List(SubscribeInfo(publishType.toString, si.name, "", "", si.subsystem, ""))
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
  private def getCommandsReceived(query: IcdDbQuery, models: IcdModels): List[CommandInfo] = {
    for {
      cmd ← models.commandModel.toList
      received ← cmd.receive
    } yield {
      val senders = query.getCommandSenders(cmd.subsystem, cmd.component, received.name).map(comp ⇒
        OtherComponent(comp.subsystem, comp.component))
      CommandInfo(received.name, HtmlMarkup.gfmToHtml(received.description), senders)
    }
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommandsSent(query: IcdDbQuery, models: IcdModels): List[CommandInfo] = {
    val result = for {
      cmd ← models.commandModel.toList
      sent ← cmd.send
    } yield {
      query.getCommand(sent.subsystem, sent.component, sent.name).map { r ⇒
        CommandInfo(sent.name, HtmlMarkup.gfmToHtml(r.description),
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

