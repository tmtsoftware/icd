package csw.services.icd.db

import csw.services.icd.html.OpenApiToHtml
import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
 * Support for creating instances of the shared (scala/scala.js) ComponentInfo class.
 * (This code can't be shared, since it accesses the database, which is on the server.)
 *
 * @param displayWarnings if true warn when no publishers are found for a subscribed event etc.
 * @param clientApi if true include subscribed events and sent commands
 * @param maybeStaticHtml  for services documented by OpenApi JSON, determines the type of HTML generated
 *                       (static (true) is plain HTML, non-static (false) includes JavaScript)
 *                       A value of None means don't generate the HTML at all.
 */
//noinspection DuplicatedCode
class ComponentInfoHelper(displayWarnings: Boolean, clientApi: Boolean, maybeStaticHtml: Option[Boolean]) {

  /**
   * Query the database for information about all the subsystem's components
   *
   * @param versionManager used to access the database
   * @param sv the subsystem
   * @return a list of objects containing information about the components
   */
  def getComponentInfoList(
      versionManager: IcdVersionManager,
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions]
  ): List[ComponentInfo] = {
    val resolvedIcdModels = versionManager.getResolvedModels(sv, maybePdfOptions)
    resolvedIcdModels.flatMap(m => getComponentInfoFromModels(versionManager, Some(m), maybePdfOptions))
  }

  /**
   * Query the database for information about the given component
   *
   * @param versionManager    used to access the database
   * @param sv       the subsystem and component
   * @return an object containing information about the component, if found
   */
  def getComponentInfo(
      versionManager: IcdVersionManager,
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions]
  ): Option[ComponentInfo] = {
    // get the models for this component
    val resolvedIcdModels = versionManager.getResolvedModels(sv, maybePdfOptions)
    getComponentInfoFromModels(versionManager, resolvedIcdModels.headOption, maybePdfOptions)
  }

  /**
   * Gets component info from the given IcdModels object
   *
   * @param versionManager    used to access the database
   * @param models            models for a component
   * @return an object containing information about the component, if found
   */
  private def getComponentInfoFromModels(
      versionManager: IcdVersionManager,
      models: Option[IcdModels],
      maybePdfOptions: Option[PdfOptions]
  ): Option[ComponentInfo] = {
    models.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes      = getPublishes(versionManager.query, icdModels, maybePdfOptions)
      val subscribes     = if (clientApi) getSubscribes(versionManager.query, icdModels, maybePdfOptions) else None
      val commands       = getCommands(versionManager.query, icdModels, maybePdfOptions)
      val services       = getServices(versionManager.query, icdModels, maybePdfOptions)
      componentModel.map { model =>
        ComponentInfo(model, publishes, subscribes, commands, services)
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
      subscribeType: PublishType,
      maybePdfOptions: Option[PdfOptions]
  ): List[SubscribeInfo] = {
    if (clientApi)
      query.subscribes(s"$prefix.$name", subscribeType, maybePdfOptions).map { s =>
        SubscribeInfo(s.component, s.subscribeType, s.subscribeModelInfo)
      }
    else Nil
  }

  /**
   * Gets information about the items published by a component, along with a reference to the subscribers to each item
   *
   * @param query  database query handle
   * @param models the model objects for the component
   */
  private def getPublishes(query: IcdDbQuery, models: IcdModels, maybePdfOptions: Option[PdfOptions]): Option[Publishes] = {
    models.publishModel match {
      case None => None
      case Some(m) =>
        val prefix = s"${m.subsystem}.${m.component}"
        val eventList = m.eventList.map { t =>
          EventInfo(t, getSubscribers(query, prefix, t.name, t.description, Events, maybePdfOptions))
        }
        val observeEventList = m.observeEventList.map { t =>
          EventInfo(t, getSubscribers(query, prefix, t.name, t.description, ObserveEvents, maybePdfOptions))
        }
        val currentStateList = m.currentStateList.map { t =>
          EventInfo(t, getSubscribers(query, prefix, t.name, t.description, CurrentStates, maybePdfOptions))
        }
        // TODO: Ignore alarms in publish-model.conf if alarm-model.conf is present? Or merge any alarms found?
//        val alarmList = models.alarmsModel.map(_.alarmList).getOrElse(m.alarmList)
        val alarmList = models.alarmsModel.toList.flatMap(_.alarmList) ++ m.alarmList
        if (m.description.nonEmpty || eventList.nonEmpty || observeEventList.nonEmpty || alarmList.nonEmpty)
          Some(Publishes(m.description, eventList, observeEventList, currentStateList, alarmList))
        else None
    }
  }

  /**
   * Gets information about the items the component subscribes to, along with the publisher of each item
   *
   * @param query  the database query handle
   * @param models the model objects for the component
   */
  private def getSubscribes(query: IcdDbQuery, models: IcdModels, maybePdfOptions: Option[PdfOptions]): Option[Subscribes] = {

    // Gets additional information about the given subscription, including info from the publisher
    def getInfo(publishType: PublishType, si: SubscribeModelInfo): DetailedSubscribeInfo = {
      // XXX TODO: Would be more efficient to just get the publishModel
      val x = for {
        t            <- query.getModels(si.subsystem, Some(si.component), maybePdfOptions)
        publishModel <- t.publishModel
      } yield {
        val maybeEvent = publishType match {
          case Events        => publishModel.eventList.find(t => t.name == si.name)
          case ObserveEvents => publishModel.observeEventList.find(t => t.name == si.name)
          case CurrentStates => publishModel.currentStateList.find(t => t.name == si.name)
          case Alarms        => None
        }
        DetailedSubscribeInfo(publishType, si, maybeEvent, t.componentModel, displayWarnings)
      }
      x.headOption.getOrElse(DetailedSubscribeInfo(publishType, si, None, None, displayWarnings))
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = m.eventList.map(getInfo(Events, _)) ++
          m.observeEventList.map(getInfo(ObserveEvents, _)) ++
          m.currentStateList.map(getInfo(CurrentStates, _))
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
  private def getCommandsReceived(
      query: IcdDbQuery,
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ReceivedCommandInfo] = {
    for {
      cmd      <- models.commandModel.toList
      received <- cmd.receive
    } yield {
      val senders =
        if (clientApi)
          query.getCommandSenders(cmd.subsystem, cmd.component, received.name, maybePdfOptions)
        else Nil
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
  private def getCommandsSent(
      query: IcdDbQuery,
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[SentCommandInfo] = {
    val result = for {
      cmd  <- models.commandModel.toList
      sent <- cmd.send
    } yield {
      val recv = query.getCommand(sent.subsystem, sent.component, sent.name, maybePdfOptions)
      SentCommandInfo(
        sent.name,
        sent.subsystem,
        sent.component,
        recv,
        query.getComponentModel(sent.subsystem, sent.component, maybePdfOptions),
        displayWarnings
      )
    }
    result
  }

  /**
   * Gets a list of commands sent or received by the component
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getCommands(query: IcdDbQuery, models: IcdModels, maybePdfOptions: Option[PdfOptions]): Option[Commands] = {
    val received = getCommandsReceived(query, models, maybePdfOptions)
    val sent     = if (clientApi) getCommandsSent(query, models, maybePdfOptions) else Nil
    models.commandModel match {
      case None => None
      case Some(m) =>
        val desc = m.description
        if (desc.nonEmpty || sent.nonEmpty || received.nonEmpty)
          Some(Commands(desc, received, sent))
        else None
    }
  }

  /**
   * Gets a list of services provided by the component
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getServicesProvided(
      query: IcdDbQuery,
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ServiceProvidedInfo] = {
    for {
      serviceModel <- models.serviceModel.toList
      provides     <- serviceModel.provides
    } yield {
      val clientComponents =
        if (clientApi)
          query.getServiceClients(serviceModel.subsystem, serviceModel.component, provides.name, maybePdfOptions)
        else Nil
      val html = maybeStaticHtml.map(staticHtml => OpenApiToHtml.getHtml(provides.openApi, staticHtml)).getOrElse("<div/>")
      ServiceProvidedInfo(provides, clientComponents, html)
    }
  }

  /**
   * Gets a list of services required by the component
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getServicesRequired(
      query: IcdDbQuery,
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ServicesRequiredInfo] = {
    val result = for {
      serviceModel       <- models.serviceModel.toList
      serviceModelClient <- serviceModel.requires
    } yield {
      val maybeServiceModel         = query.getServiceModel(serviceModelClient.subsystem, serviceModelClient.component, maybePdfOptions)
      val maybeServiceModelProvider = maybeServiceModel.map(_.provides).flatMap(_.find(_.name == serviceModelClient.name))
      ServicesRequiredInfo(
        serviceModelClient,
        maybeServiceModelProvider,
        query.getComponentModel(serviceModelClient.subsystem, serviceModelClient.component, maybePdfOptions),
        displayWarnings
      )
    }
    result
  }

  /**
   * Gets a list of services used or provided by the component
   *
   * @param query  database query handle
   * @param models model objects for component
   */
  private def getServices(query: IcdDbQuery, models: IcdModels, maybePdfOptions: Option[PdfOptions]): Option[Services] = {
    val provided = getServicesProvided(query, models, maybePdfOptions)
    val required = if (clientApi) getServicesRequired(query, models, maybePdfOptions) else Nil
    models.serviceModel match {
      case None => None
      case Some(m) =>
        val desc = m.description
        if (desc.nonEmpty || provided.nonEmpty || required.nonEmpty)
          Some(Services(desc, provided, required))
        else None
    }
  }
}
