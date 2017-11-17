package csw.services.icd.db

import java.io.{File, FileOutputStream}

import csw.services.icd.IcdToPdf
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels._

import scalatags.Text

object MissingItemsReport {

  case class PublishedItemInfo(publisherSubsystem: String,
                               publisherComponent: String,
                               prefix: String,
                               name: String) {
    val key = s"$prefix.$name"
  }

  case class SubscribedItemInfo(subscriberSubsystem: String,
                                subscriberComponent: String,
                                publisherSubsystem: String,
                                publisherComponent: String,
                                prefix: String,
                                name: String) {
    val key = s"$prefix.$name"
  }

  case class Items(publishedAlarms: List[PublishedItemInfo],
                   publishedEvents: List[PublishedItemInfo],
                   publishedEventStreams: List[PublishedItemInfo],
                   publishedTelemetry: List[PublishedItemInfo],
                   subscribedAlarms: List[SubscribedItemInfo],
                   subscribedEvents: List[SubscribedItemInfo],
                   subscribedEventStreams: List[SubscribedItemInfo],
                   subscribedTelemetry: List[SubscribedItemInfo],
                   receivedCommands: List[PublishedItemInfo],
                   sentCommands: List[SubscribedItemInfo],
                   badComponentNames: Set[String])

}

case class MissingItemsReport(db: IcdDb) {

  import MissingItemsReport._

  val query = new CachedIcdDbQuery(db.db)

  // Returns a list of items missing a publisher, subscriber, sender or receiver
  private def getMissingItems: Items = {

    def getPublishedItems(c: ComponentModel, names: List[String]): List[PublishedItemInfo] = {
      names.map(name => PublishedItemInfo(c.subsystem, c.component, c.prefix, name))
    }

    def getSubscribedItems(c: ComponentModel, list: List[SubsystemComponentName]): List[SubscribedItemInfo] = {
      list.map(p => SubscribedItemInfo(
        c.subsystem, c.component,
        p.subsystem, p.component,
        query.getComponentModel(p.subsystem, p.component).map(_.prefix).getOrElse(""),
        p.name))
    }

    val components = query.getComponents.filter(!_.subsystem.startsWith("TEST"))

    def getItems: List[Items] = {
      for {
        component <- components
        publishModel <- query.getPublishModel(component)
        subscribeModel <- query.getSubscribeModel(component)
        commandModel <- query.getCommandModel(component)
      } yield {
        val publishedAlarms = getPublishedItems(component, publishModel.alarmList.map(_.name))
        val publishedEvents = getPublishedItems(component, publishModel.eventList.map(_.name))
        val publishedEventStreams = getPublishedItems(component, publishModel.eventStreamList.map(_.name))
        val publishedTelemetry = getPublishedItems(component, publishModel.telemetryList.map(_.name))

        val subscribedAlarms = getSubscribedItems(component, subscribeModel.alarmList)
        val subscribedEvents = getSubscribedItems(component, subscribeModel.eventList)
        val subscribedEventStreams = getSubscribedItems(component, subscribeModel.eventStreamList)
        val subscribedTelemetry = getSubscribedItems(component, subscribeModel.telemetryList)

        val receivedCommands = getPublishedItems(component, commandModel.receive.map(_.name))
        val sentCommands = getSubscribedItems(component, commandModel.send)

        val compRefs =
          publishedAlarms.map(_.publisherComponent) ++
            publishedEvents.map(_.publisherComponent) ++
            publishedEventStreams.map(_.publisherComponent) ++
            publishedTelemetry.map(_.publisherComponent) ++
            subscribedAlarms.map(_.publisherComponent) ++ subscribedAlarms.map(_.subscriberComponent) ++
            subscribedEvents.map(_.publisherComponent) ++ subscribedEvents.map(_.subscriberComponent) ++
            subscribedEventStreams.map(_.publisherComponent) ++ subscribedEventStreams.map(_.subscriberComponent) ++
            subscribedTelemetry.map(_.publisherComponent) ++ subscribedTelemetry.map(_.subscriberComponent) ++
            receivedCommands.map(_.publisherComponent) ++
            sentCommands.map(_.publisherComponent) ++ sentCommands.map(_.subscriberComponent)
        val compSet = components.map(_.component).toSet
        val badComponentNames = compRefs.filter(!compSet.contains(_)).toSet

        Items(publishedAlarms, publishedEvents, publishedEventStreams, publishedTelemetry,
          subscribedAlarms, subscribedEvents, subscribedEventStreams, subscribedTelemetry,
          receivedCommands, sentCommands, badComponentNames)
      }
    }

    // Return list of published items with no subscribers
    def getPubNoSub(published: List[PublishedItemInfo], subscribed: Map[String, SubscribedItemInfo]): List[PublishedItemInfo] = {
      published.filter(p => !subscribed.contains(p.key))
    }

    // Return list of subscribed items with no publisher
    def getSubNoPub(subscribes: List[SubscribedItemInfo], publishes: Map[String, PublishedItemInfo]): List[SubscribedItemInfo] = {
      subscribes.filter(p => !publishes.contains(p.key))
    }

    val items = getItems

    val publishedAlarms = items.flatMap(_.publishedAlarms)
    val publishedAlarmMap = publishedAlarms.map(a => a.key -> a).toMap
    val publishedEvents = items.flatMap(_.publishedEvents)
    val publishedEventsMap = publishedEvents.map(e => e.key -> e).toMap
    val publishedEventStreams = items.flatMap(_.publishedEventStreams)
    val publishedEventStreamMap = publishedEventStreams.map(e => e.key -> e).toMap
    val publishedTelemetry = items.flatMap(_.publishedTelemetry)
    val publishedTelemetryMap = publishedTelemetry.map(e => e.key -> e).toMap

    val subscribedAlarms = items.flatMap(_.subscribedAlarms)
    val subscribedAlarmsMap = subscribedAlarms.map(a => a.key -> a).toMap
    val subscribedEvents = items.flatMap(_.subscribedEvents)
    val subscribedEventsMap = subscribedEvents.map(e => e.key -> e).toMap
    val subscribedEventStreams = items.flatMap(_.subscribedEventStreams)
    val subscribedEventStreamsMap = subscribedEventStreams.map(e => e.key -> e).toMap
    val subscribedTelemetry = items.flatMap(_.subscribedTelemetry)
    val subscribedTelemetryMap = subscribedTelemetry.map(e => e.key -> e).toMap

    val receivedCommands = items.flatMap(_.receivedCommands)
    val receivedCommandMap = receivedCommands.map(c => c.key -> c).toMap
    val sentCommands = items.flatMap(_.sentCommands)
    val sentCommandsMap = sentCommands.map(c => c.key -> c).toMap

    val publishedAlarmsWithNoSubscribers = getPubNoSub(publishedAlarms, subscribedAlarmsMap)
    val publishedEventsWithNoSubscribers = getPubNoSub(publishedEvents, subscribedEventsMap)
    val publishedEventStreamsWithNoSubscribers = getPubNoSub(publishedEventStreams, subscribedEventStreamsMap)
    val publishedTelemetryWithNoSubscribers = getPubNoSub(publishedTelemetry, subscribedTelemetryMap)

    val subscribedAlarmsWithNoPublisher = getSubNoPub(subscribedAlarms, publishedAlarmMap)
    val subscribedEventsWithNoPublisher = getSubNoPub(subscribedEvents, publishedEventsMap)
    val subscribedEventStreamsWithNoPublisher = getSubNoPub(subscribedEventStreams, publishedEventStreamMap)
    val subscribedTelemetryWithNoPublisher = getSubNoPub(subscribedTelemetry, publishedTelemetryMap)

    val receivedCommandsWithNoSenders = getPubNoSub(receivedCommands, sentCommandsMap)
    val sentCommandsWithNoReceivers = getSubNoPub(sentCommands, receivedCommandMap)

    val badComponentNames = items.flatMap(_.badComponentNames).toSet

    Items(
      publishedAlarmsWithNoSubscribers,
      publishedEventsWithNoSubscribers,
      publishedEventStreamsWithNoSubscribers,
      publishedTelemetryWithNoSubscribers,
      subscribedAlarmsWithNoPublisher,
      subscribedEventsWithNoPublisher,
      subscribedEventStreamsWithNoPublisher,
      subscribedTelemetryWithNoPublisher,
      receivedCommandsWithNoSenders,
      sentCommandsWithNoReceivers,
      badComponentNames
    )
  }

  // Generates the HTML for the report
  private def makeReport(): String = {
    import scalatags.Text.all._

    def missingPubItemMarkup(title: String, info: List[PublishedItemInfo]): Text.TypedTag[String] = {
      if (info.isEmpty) div() else
        div(
          h3(title),
          div(
            table(
              thead(
                tr(
                  th("Subsystem"),
                  th("Component"),
                  th("Prefix"),
                  th("Name"),
                )
              ),
              tbody(
                for {
                  item <- info
                } yield {
                  tr(
                    td(p(item.publisherSubsystem)),
                    td(p(item.publisherComponent)),
                    td(p(item.prefix)),
                    td(p(item.name)))
                }
              )
            )
          )
        )
    }

    def missingSubItemMarkup(title: String, info: List[SubscribedItemInfo],
                             publisherTitle: String = "Publisher",
                             subscriberTitle: String = "Subscriber"): Text.TypedTag[String] = {
      if (info.isEmpty) div() else
        div(
          h3(title),
          div(
            table(
              thead(
                tr(
                  th(s"$subscriberTitle Subsystem"),
                  th(s"$subscriberTitle Component"),
                  th(s"Declared $publisherTitle Subsystem"),
                  th(s"Declared $publisherTitle Component"),
                  th("Prefix"),
                  th("Name"),
                )
              ),
              tbody(
                for {
                  item <- info
                } yield {
                  tr(
                    td(p(item.subscriberSubsystem)),
                    td(p(item.subscriberComponent)),
                    td(p(item.publisherSubsystem)),
                    td(p(item.publisherComponent)),
                    td(p(item.prefix)),
                    td(p(item.name)))
                }
              )
            )
          )
        )
    }

    val missingItems = getMissingItems
    val markup = html(
      head(
        scalatags.Text.tags2.title("Missing Items"),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss))
      ),
      body(
        h2("Missing Items"),
        missingPubItemMarkup("Published Alarms with no Subscribers", missingItems.publishedAlarms),
        missingPubItemMarkup("Published Events with no Subscribers", missingItems.publishedEvents),
        missingPubItemMarkup("Published Event Streams with no Subscribers", missingItems.publishedEventStreams),
        missingPubItemMarkup("Published Telemetry with no Subscribers", missingItems.publishedTelemetry),

        missingSubItemMarkup("Subscribed Alarms that are not Published Anywhere", missingItems.subscribedAlarms),
        missingSubItemMarkup("Subscribed Events that are not Published Anywhere", missingItems.subscribedEvents),
        missingSubItemMarkup("Subscribed Event Streams that are not Published Anywhere", missingItems.subscribedEventStreams),
        missingSubItemMarkup("Subscribed Telemetry that is not Published Anywhere", missingItems.subscribedTelemetry),

        missingPubItemMarkup("Received Commands with no Senders", missingItems.receivedCommands),
        missingSubItemMarkup("Sent Commands that are not Defined Anywhere", missingItems.sentCommands, "Receiver", "Sender"),

        div(
          h3("Component names that were Referenced but not Defined Anywhere"),
          ul(
            missingItems.badComponentNames.toList.map(s => li(s))
          )
        )
      )
    )
    markup.render
  }

  /**
    * Saves the report in HTML or PDF, depending on the file suffix
    */
  def saveToFile(file: File): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html, showLogo = false)

    val html = makeReport()
    file.getName.split('.').drop(1).lastOption match {
      case Some("html") => saveAsHtml(html)
      case Some("pdf") => saveAsPdf(html)
      case _ => // XXX TODO: Save tables in csv format separated by blank lines?
        println(s"Unsupported output format: Expected *.html or *.pdf")
    }
  }
}
