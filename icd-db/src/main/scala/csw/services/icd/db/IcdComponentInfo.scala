package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.Subscribed
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.html.OpenApiToHtml
import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
 * Gathers information related to a component in a given version of an ICD.
 * Only information related to the component and the target subsystem version is included.
 */
//noinspection DuplicatedCode
object IcdComponentInfo {

  /**
   * Query the database for information about the given components in an ICD
   *
   * @param versionManager used to access the database
   * @param sv the subsystem
   * @param targetSv the target subsystem of the ICD
   * @param maybePdfOptions optional settings used when converting Markdown to HTML for use in PDF output
   * @param staticHtml for services documented by OpenApi JSON, determines the type of HTML generated
   *                   (static is plain HTML, non-static includes JavaScript)
   * @return an object containing information about the component
   */
  def getComponentInfoList(
      versionManager: IcdVersionManager,
      sv: SubsystemWithVersion,
      targetSv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions],
      staticHtml: Boolean,
      fitsKeyMap: FitsKeyMap
  ): List[ComponentInfo] = {

    val resolvedModelsList       = versionManager.getResolvedModels(sv, maybePdfOptions, fitsKeyMap)
    val resolvedTargetModelsList = versionManager.getResolvedModels(targetSv, maybePdfOptions, fitsKeyMap)

    resolvedModelsList
      .flatMap(m =>
        getComponentInfoFromModels(
          versionManager,
          Some(m),
          resolvedTargetModelsList,
          maybePdfOptions,
          staticHtml
        )
      )
      .map(ComponentInfo.applyIcdFilter)
  }

//  /**
//   * Query the database for information about the given component
//   *
//   * @param versionManager used to access versions of components
//   * @param sv    the subsystem
//   * @param targetSv    the target subsystem of the ICD
//   * @return an object containing information about the component
//   */
//  private def getComponentInfo(
//      versionManager: IcdVersionManager,
//      sv: SubsystemWithVersion,
//      targetSv: SubsystemWithVersion,
//      maybePdfOptions: Option[PdfOptions]
//  ): Option[ComponentInfo] = {
//    // get the models for this component
//    val modelsList       = versionManager.getModels(sv, subsystemOnly = false, maybePdfOptions)
//    val targetModelsList = versionManager.getModels(targetSv, subsystemOnly = false, maybePdfOptions)
//    getComponentInfoFromModels(versionManager, modelsList.headOption, targetModelsList, maybePdfOptions)
//  }

  /**
   * Query the database for information about the given component
   *
   * @param versionManager used to access versions of components
   * @param models    the component models for subsystem1
   * @param targetModelsList    the component models for the target subsystem
   * @param maybePdfOptions optional settings used when converting Markdown to HTML for use in PDF output
   * @param staticHtml for services documented by OpenApi JSON, determines the type of HTML generated
   *                   (static is plain HTML, non-static includes JavaScript)
   * @return an object containing information about the component
   */
  private def getComponentInfoFromModels(
      versionManager: IcdVersionManager,
      models: Option[IcdModels],
      targetModelsList: List[IcdModels],
      maybePdfOptions: Option[PdfOptions],
      staticHtml: Boolean
  ): Option[ComponentInfo] = {
    models.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes      = getPublishes(icdModels, targetModelsList)
      val subscribes     = getSubscribes(icdModels, targetModelsList)
      val commands       = getCommands(versionManager.query, icdModels, targetModelsList, maybePdfOptions)
      val services       = getServices(versionManager.query, icdModels, targetModelsList, maybePdfOptions, staticHtml)

      if (publishes.isDefined || subscribes.isDefined || commands.isDefined || services.isDefined)
        componentModel.map(ComponentInfo(_, publishes, subscribes, commands, services))
      else None
    }
  }

  /**
   * Gets information about the items published by a component
   *
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   */
  private def getPublishes(models: IcdModels, targetModelsList: List[IcdModels]): Option[Publishes] = {
    models.publishModel match {
      case None => None
      case Some(m) =>
        val prefix    = s"${m.subsystem}.${m.component}"
        val component = m.component
        val eventList = m.eventList.map { t =>
          EventInfo(t, getSubscribers(m.subsystem, component, prefix, t.name, t.description, Events, targetModelsList))
        }
        val observeEventList = m.observeEventList.map { t =>
          EventInfo(t, getSubscribers(m.subsystem, component, prefix, t.name, t.description, ObserveEvents, targetModelsList))
        }
        val currentStateList = m.currentStateList.map { t =>
          EventInfo(t, getSubscribers(m.subsystem, component, prefix, t.name, t.description, CurrentStates, targetModelsList))
        }
        if (eventList.nonEmpty || observeEventList.nonEmpty)
          Some(Publishes(m.description, eventList, observeEventList, currentStateList, Nil))
        else None
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
   * @param pubType          One of Event, ObserveEvent.
   * @param targetModelsList the target model objects for the ICD
   */
  private def getSubscribers(
      subsystem: String,
      component: String,
      prefix: String,
      name: String,
      desc: String,
      pubType: PublishType,
      targetModelsList: List[IcdModels]
  ): List[SubscribeInfo] = {

    // Full path of the published item
    val path = s"$prefix.$name"

    // Returns the object if the component subscribes to the given value.
    //
    // targetInfo: list of items the target subscriber subscribes to
    // Returns the Subscribed object, if the component is a subscriber to the given path
    def subscribes(componentModel: ComponentModel, targetInfo: List[SubscribeModelInfo]): Option[Subscribed] = {
      targetInfo
        .find { subscribeInfo =>
          subscribeInfo.name == name && subscribeInfo.subsystem == subsystem && subscribeInfo.component == component
        }
        .map { subscribeInfo =>
          Subscribed(componentModel, subscribeInfo, pubType, path)
        }
    }

    // Gets the list of subscribed items from the model given the publish type
    def getSubscribeInfoByType(subscribeModel: SubscribeModel, pubType: PublishType): List[SubscribeModelInfo] = {
      pubType match {
        case Events        => subscribeModel.eventList
        case ObserveEvents => subscribeModel.observeEventList
        case CurrentStates => subscribeModel.currentStateList
        case Alarms        => Nil
      }
    }

    for {
      icdModel       <- targetModelsList
      componentModel <- icdModel.componentModel
      subscribeModel <- icdModel.subscribeModel
      s              <- subscribes(componentModel, getSubscribeInfoByType(subscribeModel, pubType))
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
        t              <- targetModelsList
        componentModel <- t.componentModel
        if componentModel.component == si.component && componentModel.subsystem == si.subsystem
        publishModel <- t.publishModel
      } yield {
        val maybeEvent = publishType match {
          case Events        => publishModel.eventList.find(t => t.name == si.name)
          case ObserveEvents => publishModel.observeEventList.find(t => t.name == si.name)
          case CurrentStates => publishModel.currentStateList.find(t => t.name == si.name)
          case Alarms        => None
        }
        DetailedSubscribeInfo(publishType, si, maybeEvent, Some(componentModel))
      }
      x.headOption
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = (m.eventList.map(getInfo(Events, _)) ++
          m.observeEventList.map(getInfo(ObserveEvents, _)) ++
          m.currentStateList.map(getInfo(CurrentStates, _))).flatten
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
  private def getCommandSenders(
      subsystem: String,
      component: String,
      commandName: String,
      targetModelsList: List[IcdModels]
  ): List[ComponentModel] = {
    for {
      icdModels      <- targetModelsList
      componentModel <- icdModels.componentModel
      commandModel   <- icdModels.commandModel
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
      cmd      <- models.commandModel.toList
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
  private def getCommand(
      subsystem: String,
      component: String,
      commandName: String,
      targetModelsList: List[IcdModels]
  ): Option[ReceiveCommandModel] = {
    val result = for {
      icdModels    <- targetModelsList
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
  private def getCommandsSent(
      query: IcdDbQuery,
      models: IcdModels,
      targetModelsList: List[IcdModels],
      maybePdfOptions: Option[PdfOptions]
  ): List[SentCommandInfo] = {
    val result = for {
      cmd  <- models.commandModel.toList
      sent <- cmd.send
    } yield {
      val maybeReceiver = getCommand(sent.subsystem, sent.component, sent.name, targetModelsList)
      SentCommandInfo(
        sent.name,
        sent.subsystem,
        sent.component,
        maybeReceiver,
        query.getComponentModel(sent.subsystem, sent.component, maybePdfOptions)
      )
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
  private def getCommands(
      query: IcdDbQuery,
      models: IcdModels,
      targetModelsList: List[IcdModels],
      maybePdfOptions: Option[PdfOptions]
  ): Option[Commands] = {
    val received = getCommandsReceived(models, targetModelsList)
    val sent     = getCommandsSent(query, models, targetModelsList, maybePdfOptions)
    models.commandModel match {
      case None => None
      case Some(m) =>
        if (sent.nonEmpty || received.nonEmpty)
          Some(Commands(m.description, received, sent))
        else None
    }
  }

  /**
   * Returns the service model provider for the given service, if found.
   *
   * @param subsystem        the subsystem that contains the component that provides the service
   * @param component        the component that provides the service
   * @param serviceName      the service name
   * @param targetModelsList the target model objects (from other subsystem in icd)
   */
  private def getServiceModelProvider(
      subsystem: String,
      component: String,
      serviceName: String,
      targetModelsList: List[IcdModels]
  ): Option[ServiceModelProvider] = {
    val result = for {
      icdModels    <- targetModelsList
      serviceModel <- icdModels.serviceModel
      if serviceModel.subsystem == subsystem && serviceModel.component == component
      serviceModelProvider <- serviceModel.provides.find(_.name == serviceName)
    } yield serviceModelProvider

    result.headOption
  }

  /**
   * Returns a list of components that require the given service from the given component/subsystem
   *
   * @param subsystem   the service provider subsystem
   * @param component   the service provider component
   * @param serviceName the name of the service
   * @return list containing one item for each component that requires the service
   */
  private def getServiceClients(
      subsystem: String,
      component: String,
      serviceName: String,
      targetModelsList: List[IcdModels]
  ): List[ComponentModel] = {
    for {
      icdModels      <- targetModelsList
      componentModel <- icdModels.componentModel
      serviceModel   <- icdModels.serviceModel
      if serviceModel.requires.exists(s => s.subsystem == subsystem && s.component == component && s.name == serviceName)
    } yield {
      componentModel
    }
  }

  /**
   * Gets a list of services provided by the component
   *
   * @param models model objects for component
   * @param targetModelsList model objects for the other component in the ICD
   * @param staticHtml for services documented by OpenApi JSON, determines the type of HTML generated
   *                   (static is plain HTML, non-static includes JavaScript)
   */
  private def getServicesProvided(
      models: IcdModels,
      targetModelsList: List[IcdModels],
      staticHtml: Boolean
  ): List[ServiceProvidedInfo] = {
    for {
      serviceModel <- models.serviceModel.toList
      provides     <- serviceModel.provides
    } yield {
      val clientComponents = getServiceClients(serviceModel.subsystem, serviceModel.component, provides.name, targetModelsList)
      val html = if (clientComponents.size == 1 && clientComponents.head.subsystem != serviceModel.subsystem) {
        // If there is only one client in another subsystem (might be an ICD), only document the used routes.
        // Note that the getServiceClients() call above tells us that the following service client exists
        val paths = targetModelsList.head.serviceModel.head.requires
          .find(s => s.subsystem == serviceModel.subsystem && s.component == serviceModel.component && s.name == provides.name)
          .get
          .paths
        OpenApiToHtml.getHtml(OpenApiToHtml.filterOpenApiJson(provides.openApi, paths), staticHtml)
      }
      else {
        // Display the full API
        OpenApiToHtml.getHtml(provides.openApi, staticHtml)
      }

      ServiceProvidedInfo(provides, clientComponents, html)
    }
  }

  /**
   * Gets a list of services required by the component, including information about the components
   * that provide each service.
   *
   * @param query            used to query the db
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   * @param staticHtml       for services documented by OpenApi JSON, determines the type of HTML generated
   *                        (static is plain HTML, non-static includes JavaScript)
   */
  private def getServicesRequired(
      query: IcdDbQuery,
      models: IcdModels,
      targetModelsList: List[IcdModels],
      maybePdfOptions: Option[PdfOptions],
      staticHtml: Boolean
  ): List[ServicesRequiredInfo] = {
    val result = for {
      serviceModel       <- models.serviceModel.toList
      serviceModelClient <- serviceModel.requires
    } yield {
      val maybeServiceModelProvider = getServiceModelProvider(
        serviceModelClient.subsystem,
        serviceModelClient.component,
        serviceModelClient.name,
        targetModelsList
      )
      val maybeHtml = maybeServiceModelProvider.map { p =>
        OpenApiToHtml.getHtml(OpenApiToHtml.filterOpenApiJson(p.openApi, serviceModelClient.paths), staticHtml)
      }
      ServicesRequiredInfo(
        serviceModelClient,
        maybeServiceModelProvider,
        query.getComponentModel(serviceModelClient.subsystem, serviceModelClient.component, maybePdfOptions),
        maybeHtml
      )
    }
    result
  }

  /**
   * Gets a list of HTTP services required or provided by the component.
   *
   * @param query            used to query the db
   * @param models           the model objects for the component
   * @param targetModelsList the target model objects
   * @param maybePdfOptions optional settings used when converting Markdown to HTML for use in PDF output
   * @param staticHtml for services documented by OpenApi JSON, determines the type of HTML generated
   *                   (static is plain HTML, non-static includes JavaScript)
   */
  private def getServices(
      query: IcdDbQuery,
      models: IcdModels,
      targetModelsList: List[IcdModels],
      maybePdfOptions: Option[PdfOptions],
      staticHtml: Boolean
  ): Option[Services] = {
    val provided = getServicesProvided(models, targetModelsList, staticHtml)
    val required = getServicesRequired(query, models, targetModelsList, maybePdfOptions, staticHtml)
    models.serviceModel match {
      case None => None
      case Some(m) =>
        val desc = m.description
        if (desc.nonEmpty || provided.nonEmpty || required.nonEmpty)
          Some(Services(m.description, provided, required))
        else None
    }
  }
}
