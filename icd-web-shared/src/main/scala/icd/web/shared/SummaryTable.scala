package icd.web.shared

import scalatags.Text
import scalatags.Text.all.*
import icd.web.shared.ComponentInfo.{Alarms, CurrentStates, Events, Images, ObserveEvents}
import icd.web.shared.IcdModels.{ComponentModel, EventModel, NameDesc}
import Headings.idFor
import SummaryTable.*
import SummaryInfo.*

//noinspection DuplicatedCode
object SummaryTable {

  // Insert a <wbr/> tag to help wrapping
//  private def wrapWithWbr(s: String) = raw(s.replace(".", ".<wbr/>").replace("_", "_<wbr/>"))
  private def wrapWithWbr(s: String) = s

  // Insert an element between the elements in the list (like a span with a comma)
  private def intersperse[T](xs: List[T], item: T): List[T] =
    xs match {
      case Nil      => xs
      case _ :: Nil => xs
      case a :: ys  => a :: item :: intersperse(ys, item)
    }

  private def addCommas(elems: List[Text.TypedTag[String]]): List[Text.TypedTag[String]] =
    intersperse(elems, span(", "))

  private def firstParagraph(s: String): String = {
    val i = s.indexOf("</p>")
    if (i == -1) s else s.substring(0, i + 4)
  }

}

/**
 * Displays a summary of the events published, commands received, etc. by the subsystem
 *
 * @param subsystemInfo subsystem to use
 * @param maybeTargetSv optional target subsystem and version
 * @param infoList      list of component info
 * @param nh            used for numbered headings and TOC, if needed
 * @param clientApi     if true, include event subscribers, command senders
 * @param displayTitle  if true, display the Summary title
 * @return the HTML
 */
case class SummaryTable(
    subsystemInfo: SubsystemInfo,
    maybeTargetSv: Option[SubsystemWithVersion],
    infoList: List[ComponentInfo],
    nh: Headings,
    clientApi: Boolean,
    displayTitle: Boolean
) {
  private val isIcd               = maybeTargetSv.isDefined
  private val componentPart       = subsystemInfo.sv.maybeComponent.map("." + _).getOrElse("")
  private val targetComponentPart = maybeTargetSv.flatMap(_.maybeComponent.map("." + _)).getOrElse("")
  private val sourceStr           = subsystemInfo.sv.subsystem + componentPart

  private def linkToSubscriber(subscriber: ComponentModel): Text.TypedTag[String] = {
    val name = s"${subscriber.subsystem}.${subscriber.component}"
    if ((isIcd && subscriber.subsystem == maybeTargetSv.get.subsystem) || subscriber.subsystem == subsystemInfo.sv.subsystem)
      span(a(href := s"#${subscriber.component}")(wrapWithWbr(name)))
    else
      span(wrapWithWbr(name))
  }

  // Displays a summary for published items of a given event type or commands received.
  def publishedSummaryMarkup(
      itemType: String,
      list: List[PublishedItem],
      heading: String,
      prep: String
  ): Text.TypedTag[String] = {
    val (action, publisher, subscribers) = heading.toLowerCase() match {
      case "published by" => ("publishes", "Publisher", "Subscribers")
      case "received by"  => ("receives", "Receiver", "Senders")
      case "provided by"  => ("provides", "Provider", "Consumers")
    }

    val targetStr       = if (maybeTargetSv.isDefined) s" $prep ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""
    val showYearlyAccum = !isIcd && itemType.endsWith("Events")

    def totalArchiveSpace(): Text.TypedTag[String] = {
      val sumTotal = EventModel.getTotalArchiveSpace(list.map(_.item.asInstanceOf[EventModel]))
      if (sumTotal.nonEmpty)
        p(strong(s"Total yearly space required for archiving events published by $sourceStr: $sumTotal"))
      else span()
    }

    if (list.isEmpty) div()
    else {
      div(
        nh.H3(s"$itemType $heading $sourceStr$targetStr"),
        table(
          thead(
            tr(
              th(s"$publisher Prefix"),
              if (clientApi || isIcd) th(subscribers) else span,
              th("Name"),
              if (showYearlyAccum) th("Yearly", br, "Accum.") else span(),
              th("Description")
            )
          ),
          tbody(
            for {
              info <- list
            } yield {
              tr(
                td(p(a(href := s"#${info.publisher.component}")(wrapWithWbr(info.publisher.prefix)))),
                if (clientApi || isIcd) td(p(addCommas(info.subscribers.map(linkToSubscriber)))) else span(),
                td(
                  p(
                    a(
                      href := s"#${idFor(info.publisher.component, action, itemType, info.publisher.subsystem, info.publisher.component, info.item.name)}"
                    )(wrapWithWbr(info.item.name))
                  )
                ),
                if (showYearlyAccum) {
                  val yearlyAccum =
                    if (itemType.endsWith("Events")) info.item.asInstanceOf[EventModel].totalArchiveSpacePerYear else ""
                  td(yearlyAccum)
                }
                else span(),
                td(raw(firstParagraph(info.item.description)))
              )
            }
          )
        ),
        if (showYearlyAccum) totalArchiveSpace() else span()
      )
    }
  }

  // Displays a summary for subscribed items of a given event type or commands sent.
  private def subscribedSummaryMarkup(
      itemType: String,
      list: List[SubscribedItem],
      heading: String,
      prep: String
  ): Text.TypedTag[String] = {

    // For APIs, display the list of subscribed items
    def subscriberView(): Text.TypedTag[String] = {
      val (action, subscribes, subscriber, publisher) = heading.toLowerCase() match {
        case "subscribed to by" => ("publishes", "subscribes", "Subscriber", "Publisher")
        case "sent by"          => ("receives", "sends", "Sender", "Receiver")
        case "required by"      => ("provides", "requires", "Consumer", "Provider")
      }
      val targetStr = if (maybeTargetSv.isDefined) s" $prep ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""
      div(
        nh.H3(s"$itemType $heading $sourceStr$targetStr"),
        table(
          thead(
            tr(
              th(s"$publisher Prefix"),
              if (isIcd) span() else th(subscriber),
              th("Name"),
              th("Description")
            )
          ),
          tbody(
            for {
              info <- list
            } yield {
              val prefix     = wrapWithWbr(s"${info.publisherSubsystem}.${info.publisherComponent}")
              val prefixItem = span(prefix)
              val publisherComponent =
                if (isIcd || info.publisherSubsystem == info.subscriber.subsystem)
                  a(href := s"#${info.publisherComponent}")(wrapWithWbr(info.publisherComponent))
                // XXX TODO FIXME: Make link in web app for components in other subsystems also!
                else span(wrapWithWbr(s"${info.publisherComponent}"))

              val publisherPrefix =
                if (isIcd || info.publisherSubsystem == info.subscriber.subsystem)
                  a(href := s"#${info.publisherComponent}")(prefixItem)
                else span(prefixItem)

              val subscriberPrefix =
                if (isIcd) span()
                else
                  a(href := s"#${info.subscriber.component}")(s"${info.subscriber.subsystem}.${info.subscriber.component}")

              val description = info.maybeWarning match {
                case Some(msg) => p(em("Warning: ", msg))
                case None      => raw(firstParagraph(info.item.description))
              }

              val linkId = if (isIcd) {
                // For ICDs, link to the publisher/receiver/provider
                idFor(
                  info.publisherComponent,
                  action,
                  itemType,
                  info.publisherSubsystem,
                  info.publisherComponent,
                  info.item.name
                )
              }
              else {
                // For APIs with client details option, link to the subscriber/sender etc.
                idFor(
                  info.subscriber.component,
                  subscribes,
                  itemType,
                  info.publisherSubsystem,
                  info.publisherComponent,
                  info.item.name
                )
              }
              tr(
                td(p(publisherPrefix)),
                if (isIcd) span() else td(p(subscriberPrefix)),
                td(p(a(href := s"#$linkId")(wrapWithWbr(info.item.name)))),
                td(description)
              )
            }
          )
        )
      )
    }

    if (list.isEmpty)
      div()
    else subscriberView()
  }

  def subscribedSummary(): Text.TypedTag[String] = {
    // For subscribed items and sent commands, the info from the other subsystem might not be available
    val allSubscribed = for {
      info  <- infoList
      sub   <- info.subscribes.toList
      event <- sub.subscribeInfo
    } yield (
      event.itemType,
      SubscribedItem(
        event.subscribeModelInfo.subsystem,
        event.subscribeModelInfo.component,
        event.publisher,
        event.warning,
        info.componentModel,
        OptionalNameDesc(event.subscribeModelInfo.name, event.eventModel.orElse(event.imageModel))
      )
    )

    val `subscribedEvents`      = allSubscribed.filter(_._1 == Events).map(_._2)
    val subscribedObserveEvents = allSubscribed.filter(_._1 == ObserveEvents).map(_._2)
    val subscribedCurrentStates = allSubscribed.filter(_._1 == CurrentStates).map(_._2)
    val subscribedImages        = allSubscribed.filter(_._1 == Images).map(_._2)
    val subscribedAlarms        = allSubscribed.filter(_._1 == Alarms).map(_._2)

    val sentCommands = for {
      info     <- infoList
      commands <- info.commands.toList
      command  <- commands.commandsSent
    } yield SubscribedItem(
      command.subsystem,
      command.component,
      command.receiver,
      command.warning,
      info.componentModel,
      OptionalNameDesc(command.name, command.receiveCommandModel)
    )

    val requiredServices =
      if (isIcd) Nil
      else
        for {
          info     <- infoList
          services <- info.services.toList
          service  <- services.servicesRequired
        } yield {
          val nameDesc = new NameDesc {
            override val name: String        = service.serviceModelClient.name
            override val description: String = service.maybeServiceModelProvider.map(_.description).getOrElse("")
          }
          SubscribedItem(
            service.serviceModelClient.subsystem,
            service.serviceModelClient.component,
            service.provider,
            None,
            info.componentModel,
            OptionalNameDesc(nameDesc.name, Some(nameDesc))
          )
        }

    val requiredServicesForIcd =
      if (isIcd)
        for {
          info     <- infoList
          services <- info.services.toList
          service  <- services.servicesRequired
        } yield {
          service
        }
      else Nil

    // Displays a summary for services provided
    def servicesRequiredSummaryMarkup(list: List[ServicesRequiredInfo]): Text.TypedTag[String] = {
      val targetStr = if (maybeTargetSv.isDefined) s" from ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""
      if (list.isEmpty) div()
      else {
        div(
          nh.H3(s"Services Required by $sourceStr$targetStr"),
          table(
            thead(
              tr(
                th("Service Name"),
                th("Prefix"),
                th("Path"),
                th("Method"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
                path <- info.serviceModelClient.paths
              } yield {
                val component   = info.provider.map(_.component).getOrElse("")
                val subsystem   = info.provider.map(_.subsystem).getOrElse("")
                val prefix      = info.provider.map(_.prefix).getOrElse("")
                val serviceName = info.serviceModelClient.name
                val idStr       = idFor(component, "provides", "Service", subsystem, component, serviceName)
                tr(
                  td(p(a(href := s"#$idStr")(wrapWithWbr(serviceName)))),
                  td(p(a(href := s"#$component")(wrapWithWbr(prefix)))),
                  td(p(path.path)),
                  td(p(path.method)),
                  td(raw(firstParagraph(path.description)))
                )
              }
            )
          )
        )
      }
    }

    div(
      subscribedSummaryMarkup("Events", subscribedEvents, "Subscribed to by", "from"),
      subscribedSummaryMarkup("Observe Events", subscribedObserveEvents, "Subscribed to by", "from"),
      subscribedSummaryMarkup("Current States", subscribedCurrentStates, "Subscribed to by", "from"),
      subscribedSummaryMarkup("Images", subscribedImages, "Subscribed to by", "from"),
      subscribedSummaryMarkup("Alarms", subscribedAlarms, "Subscribed to by", "from"),
      subscribedSummaryMarkup("Commands", sentCommands, "Sent by", "to"),
      if (isIcd)
        servicesRequiredSummaryMarkup(requiredServicesForIcd)
      else
        subscribedSummaryMarkup("Services", requiredServices, "Required by", "from")
    )
  }

  /**
   * Returns a set of HTML tables containing a summary of the events published, commands received by the subsystem, etc.
   */
  def displaySummary(): Text.TypedTag[String] = {

    // Displays a summary for services provided
    def serviceProviderSummaryMarkup(list: List[ProvidedServiceItem]): Text.TypedTag[String] = {
      val targetStr = if (maybeTargetSv.isDefined) s" and used by ${maybeTargetSv.get.subsystem}$targetComponentPart" else ""
      if (list.isEmpty) div()
      else {
        div(
          nh.H3(s"Services Provided by $sourceStr$targetStr"),
          table(
            thead(
              tr(
                th("Service Name"),
                if (clientApi || isIcd) th("Used by") else span,
                th("Prefix"),
                th("Path"),
                th("Method"),
                th("Description")
              )
            ),
            tbody(
              for {
                info <- list
                path <- info.service.serviceModelProvider.paths
              } yield {
                val component   = info.provider.component
                val subsystem   = info.provider.subsystem
                val serviceName = info.service.serviceModelProvider.name
                val idStr       = idFor(component, "provides", "Service", subsystem, component, serviceName)
                tr(
                  td(p(a(href := s"#$idStr")(wrapWithWbr(serviceName)))),
                  if (clientApi || isIcd) td(p(addCommas(info.consumers.map(linkToSubscriber)))) else span(),
                  td(p(a(href := s"#$component")(wrapWithWbr(info.provider.prefix)))),
                  td(p(path.path)),
                  td(p(path.method)),
                  td(raw(firstParagraph(path.description)))
                )
              }
            )
          )
        )
      }
    }

    def publishedSummary(): Text.TypedTag[String] = {
      val publishedEvents = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.eventList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel).distinct)

      val publishedObserveEvents = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.observeEventList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel).distinct)

      val publishedCurrentStates = for {
        info  <- infoList
        pub   <- info.publishes.toList
        event <- pub.currentStateList
      } yield PublishedItem(info.componentModel, event.eventModel, event.subscribers.map(_.componentModel).distinct)

      val publishedImages = for {
        info  <- infoList
        pub   <- info.publishes.toList
        image <- pub.imageList
      } yield PublishedItem(info.componentModel, image.imageModel, image.subscribers.map(_.componentModel).distinct)

      val publishedAlarms = for {
        info  <- infoList
        pub   <- info.publishes.toList
        alarm <- pub.alarmList
      } yield PublishedItem(info.componentModel, alarm, Nil)

      val receivedCommands = for {
        info     <- infoList
        commands <- info.commands.toList
        command  <- commands.commandsReceived
      } yield PublishedItem(info.componentModel, command.receiveCommandModel, command.senders.distinct)

      // For ICDs, list the service paths (routes) used by the client
      val providedServicesForIcd = if (isIcd) {
        for {
          info     <- infoList
          services <- info.services.toList
          service  <- services.servicesProvided
        } yield {
          ProvidedServiceItem(info.componentModel, service, service.requiredBy.map(_.component).distinct)
        }
      }
      else Nil

      // For APIs, list only the services used without path details here
      val providedServices = if (!isIcd) {
        for {
          info     <- infoList
          services <- info.services.toList
          service  <- services.servicesProvided
        } yield {
          val nameDesc = new NameDesc {
            override val name: String        = service.serviceModelProvider.name
            override val description: String = service.serviceModelProvider.description
          }
          PublishedItem(info.componentModel, nameDesc, service.requiredBy.map(_.component).distinct)
        }
      }
      else Nil

      div(
        publishedSummaryMarkup("Events", publishedEvents, "Published by", "for"),
        publishedSummaryMarkup("Observe Events", publishedObserveEvents, "Published by", "for"),
        publishedSummaryMarkup("Current States", publishedCurrentStates, "Published by", "for"),
        publishedSummaryMarkup("Images", publishedImages, "Published by", "for"),
        publishedSummaryMarkup("Alarms", publishedAlarms, "Published by", "for"),
        publishedSummaryMarkup("Commands", receivedCommands, "Received by", "from"),
        if (isIcd)
          serviceProviderSummaryMarkup(providedServicesForIcd)
        else
          publishedSummaryMarkup("Services", providedServices, "Provided by", "and used by")
      )
    }

    try {
      div(
        if (displayTitle) nh.H2("Summary") else span(),
        publishedSummary(),
        if (clientApi) subscribedSummary() else span()
      )
    }
    catch {
      case ex: Exception =>
        ex.printStackTrace()
        div(p(s"Summary Table: Internal error: ${ex.toString}"))

    }
  }

}
