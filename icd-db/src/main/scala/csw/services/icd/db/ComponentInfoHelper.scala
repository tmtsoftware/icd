package csw.services.icd.db

import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.html.OpenApiToHtml
import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import icd.web.shared._

/**
 * Support for creating instances of the shared (scala/scala.js) ComponentInfo class.
 * (This code can't be shared, since it accesses the database, which is on the server.)
 *
 * @param versionManager used to access the database
 * @param displayWarnings if true warn when no publishers are found for a subscribed event etc.
 * @param clientApi if true include subscribed events and sent commands
 * @param maybeStaticHtml  for services documented by OpenApi JSON, determines the type of HTML generated
 *                       (static (true) is plain HTML, non-static (false) includes JavaScript)
 *                       A value of None means don't generate the HTML at all.
 * @param subsystemsWithVersion a list of subsystems for the queries that have non-default versions
 */
//noinspection DuplicatedCode
class ComponentInfoHelper(
    versionManager: IcdVersionManager,
    displayWarnings: Boolean,
    clientApi: Boolean,
    maybeStaticHtml: Option[Boolean],
    subsystemsWithVersion: List[SubsystemWithVersion] = Nil
) {

  /**
   * Query the database for information about all the subsystem's components
   *
   * @param sv the subsystem
   * @return a list of objects containing information about the components
   */
  def getComponentInfoList(
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): List[ComponentInfo] = {
    val resolvedIcdModels = versionManager.getResolvedModels(sv, maybePdfOptions, fitsKeyMap)
    resolvedIcdModels.flatMap(m => getComponentInfoFromModels(Some(m), maybePdfOptions))
  }

  /**
   * Query the database for information about the given component
   *
   * @param sv       the subsystem and component
   * @return an object containing information about the component, if found
   */
  def getComponentInfo(
      sv: SubsystemWithVersion,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap
  ): Option[ComponentInfo] = {
    // get the models for this component
    val resolvedIcdModels = versionManager.getResolvedModels(sv, maybePdfOptions, fitsKeyMap)
    getComponentInfoFromModels(resolvedIcdModels.headOption, maybePdfOptions)
  }

  /**
   * Gets component info from the given IcdModels object
   *
   * @param models            models for a component
   * @return an object containing information about the component, if found
   */
  private def getComponentInfoFromModels(
      models: Option[IcdModels],
      maybePdfOptions: Option[PdfOptions]
  ): Option[ComponentInfo] = {
    models.flatMap { icdModels =>
      val componentModel = icdModels.componentModel
      val publishes      = getPublishes(icdModels, maybePdfOptions)
      val subscribes     = if (clientApi) getSubscribes(icdModels, maybePdfOptions) else None
      val commands       = getCommands(icdModels, maybePdfOptions)
      val services       = getServices(icdModels, maybePdfOptions)
      componentModel.map { model =>
        ComponentInfo(model, publishes, subscribes, commands, services)
      }
    }
  }

  /**
   * Gets information about who subscribes to the given published items
   *
   * @param prefix        component's prefix
   * @param name          simple name of the published item
   * @param desc          description of the item
   * @param subscribeType event, alarm, etc...
   */
  private def getSubscribers(
      prefix: String,
      name: String,
      desc: String,
      subscribeType: PublishType,
      maybePdfOptions: Option[PdfOptions]
  ): List[SubscribeInfo] = {
    if (clientApi) {
      versionManager.subscribes(s"$prefix.$name", subscribeType, maybePdfOptions, subsystemsWithVersion).map { s =>
        SubscribeInfo(s.component, s.subscribeType, s.subscribeModelInfo)
      }
    }
    else Nil
  }

  /**
   * Gets information about the items published by a component, along with a reference to the subscribers to each item
   *
   * @param models the model objects for the component
   */
  private def getPublishes(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): Option[Publishes] = {
    models.publishModel match {
      case None => None
      case Some(m) =>
        val prefix = s"${m.subsystem}.${m.component}"
        val eventList = m.eventList.map { t =>
          EventInfo(t, getSubscribers(prefix, t.name, t.description, Events, maybePdfOptions))
        }
        val observeEventList = m.observeEventList.map { t =>
          EventInfo(t, getSubscribers(prefix, t.name, t.description, ObserveEvents, maybePdfOptions))
        }
        val currentStateList = m.currentStateList.map { t =>
          EventInfo(t, getSubscribers(prefix, t.name, t.description, CurrentStates, maybePdfOptions))
        }
        val imageList = m.imageList.map { t =>
          ImageInfo(t, getSubscribers(prefix, t.name, t.description, Images, maybePdfOptions))
        }
        val alarmList = models.alarmsModel.toList.flatMap(_.alarmList) ++ m.alarmList

        if (
          m.description.nonEmpty || eventList.nonEmpty || observeEventList.nonEmpty || currentStateList.nonEmpty
          || imageList.nonEmpty || alarmList.nonEmpty
        )
          Some(Publishes(m.description, eventList, observeEventList, currentStateList, imageList, alarmList))
        else None
    }
  }

  private def makeSubsystemWithVersion(subsystem: String, maybeComponent: Option[String]): SubsystemWithVersion = {
    subsystemsWithVersion.find(_.subsystem == subsystem) match {
      case Some(sv) => SubsystemWithVersion(subsystem, sv.maybeVersion, maybeComponent)
      case None     => SubsystemWithVersion(subsystem, None, maybeComponent)
    }
  }

  /**
   * Gets information about the items the component subscribes to, along with the publisher of each item
   *
   * @param models the model objects for the component
   */
  private def getSubscribes(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): Option[Subscribes] = {
    // Gets additional information about the given subscription, including info from the publisher
    def getInfo(publishType: PublishType, si: SubscribeModelInfo): DetailedSubscribeInfo = {
      val x = for {
        t <- versionManager.getModels(
          makeSubsystemWithVersion(si.subsystem, Some(si.component)),
          maybePdfOptions,
          Map.empty
        )
        // need to resolve any "refs" in the publisher's publish model
        resolvedPublishModel <- t.publishModel.map(p => Resolver(List(t)).resolvePublishModel(p))
      } yield {
        val maybeEventModel = publishType match {
          case Events        => resolvedPublishModel.eventList.find(t => t.name == si.name)
          case ObserveEvents => resolvedPublishModel.observeEventList.find(t => t.name == si.name)
          case CurrentStates => resolvedPublishModel.currentStateList.find(t => t.name == si.name)
          case _             => None
        }
        val maybeImageModel = publishType match {
          case Images => resolvedPublishModel.imageList.find(t => t.name == si.name)
          case _      => None
        }
        DetailedSubscribeInfo(publishType, si, maybeEventModel, maybeImageModel, t.componentModel, displayWarnings)
      }
      x.headOption.getOrElse(DetailedSubscribeInfo(publishType, si, None, None, None, displayWarnings))
    }

    models.subscribeModel match {
      case None => None
      case Some(m) =>
        val subscribeInfo = m.eventList.map(getInfo(Events, _)) ++
          m.observeEventList.map(getInfo(ObserveEvents, _)) ++
          m.currentStateList.map(getInfo(CurrentStates, _)) ++
          m.imageList.map(getInfo(Images, _))
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
   * @param models model objects for component
   */
  private def getCommandsReceived(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ReceivedCommandInfo] = {
    for {
      cmd      <- models.commandModel.toList
      received <- cmd.receive
    } yield {
      val senders =
        if (clientApi)
          versionManager.getCommandSenders(cmd.subsystem, cmd.component, received.name, maybePdfOptions, subsystemsWithVersion)
        else Nil
      ReceivedCommandInfo(received, senders)
    }
  }

  /**
   * Gets a list of commands sent by the component, including information about the components
   * that receive each command.
   *
   * @param models model objects for component
   */
  private def getCommandsSent(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[SentCommandInfo] = {
    val result = for {
      cmd  <- models.commandModel.toList
      sent <- cmd.send
    } yield {
      // Need to resolve any refs in the receiver's model
      val allModels = versionManager.getModels(
        makeSubsystemWithVersion(sent.subsystem, Some(sent.component)),
        maybePdfOptions,
        Map.empty
      )
      val targetComponentModel = allModels.flatMap(_.componentModel).headOption
      val targetCmdModel = allModels
        .flatMap(_.commandModel)
        .headOption
        .getOrElse(
          CommandModel(
            sent.subsystem,
            sent.component,
            "",
            Nil,
            Nil
          )
        )
      val targetRecvModel   = targetCmdModel.receive.find(_.name == sent.name)
      val resolvedRecvModel = targetRecvModel.map(r => Resolver(allModels).resolveReceiveCommandModel(targetCmdModel, r))
      SentCommandInfo(
        sent.name,
        sent.subsystem,
        sent.component,
        resolvedRecvModel,
        targetComponentModel,
        displayWarnings
      )
    }
    result
  }

  /**
   * Gets a list of commands sent or received by the component
   *
   * @param models model objects for component
   */
  private def getCommands(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): Option[Commands] = {
    val received = getCommandsReceived(models, maybePdfOptions)
    val sent     = if (clientApi) getCommandsSent(models, maybePdfOptions) else Nil
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
   * @param models model objects for component
   */
  private def getServicesProvided(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ServiceProvidedInfo] = {
    for {
      serviceModel <- models.serviceModel.toList
      provides     <- serviceModel.provides
    } yield {
      val clientComponents =
        if (clientApi)
          versionManager.query.getServiceClients(serviceModel.subsystem, serviceModel.component, provides.name, maybePdfOptions)
        else Nil
      val html = maybeStaticHtml.map(staticHtml => OpenApiToHtml.getHtml(provides.openApi, staticHtml)).getOrElse("<div/>")
      ServiceProvidedInfo(provides, clientComponents, html)
    }
  }

  /**
   * Gets a list of services required by the component
   *
   * @param models model objects for component
   */
  private def getServicesRequired(
      models: IcdModels,
      maybePdfOptions: Option[PdfOptions]
  ): List[ServicesRequiredInfo] = {
    for {
      serviceModel       <- models.serviceModel.toList
      serviceModelClient <- serviceModel.requires
    } yield {
      val maybeServiceModel =
        versionManager.query.getServiceModel(serviceModelClient.subsystem, serviceModelClient.component, maybePdfOptions)
      val maybeServiceModelProvider = maybeServiceModel.flatMap(_.provides.find(_.name == serviceModelClient.name))
      val maybeHtml = for {
        p          <- maybeServiceModelProvider
        staticHtml <- maybeStaticHtml
      } yield OpenApiToHtml.getHtml(OpenApiToHtml.filterOpenApiJson(p.openApi, serviceModelClient.paths), staticHtml)
      ServicesRequiredInfo(
        serviceModelClient,
        maybeServiceModelProvider,
        versionManager.query.getComponentModel(serviceModelClient.subsystem, serviceModelClient.component, maybePdfOptions),
        maybeHtml,
        displayWarnings
      )
    }
  }

  /**
   * Gets a list of services used or provided by the component
   *
   * @param models model objects for component
   */
  private def getServices(models: IcdModels, maybePdfOptions: Option[PdfOptions]): Option[Services] = {
    val provided = getServicesProvided(models, maybePdfOptions)
    val required = if (clientApi) getServicesRequired(models, maybePdfOptions) else Nil
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
