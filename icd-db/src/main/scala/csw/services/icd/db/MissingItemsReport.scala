package csw.services.icd.db

import java.io._

import csw.services.icd.IcdToPdf
import csw.services.icd.html.IcdToHtml
import icd.web.shared.IcdModels._
import icd.web.shared.{PdfOptions, SubsystemWithVersion}
import scalatags.Text

/**
 * Defines classes used to generate the "missing items" report
 */
object MissingItemsReport {

  case class PublishedItemInfo(publisherSubsystem: String, publisherComponent: String, prefix: String, name: String) {
    val key = s"$prefix.$name"
  }

  case class SubscribedItemInfo(
      subscriberSubsystem: String,
      subscriberComponent: String,
      publisherSubsystem: String,
      publisherComponent: String,
      prefix: String,
      name: String
  ) {
    val key = s"$prefix.$name"
  }

  case class Items(
      publishedEvents: List[PublishedItemInfo],
      publishedObserveEvents: List[PublishedItemInfo],
      publishedCurrentStates: List[PublishedItemInfo],
      subscribedEvents: List[SubscribedItemInfo],
      subscribedObserveEvents: List[SubscribedItemInfo],
      subscribedCurrentStates: List[SubscribedItemInfo],
      receivedCommands: List[PublishedItemInfo],
      sentCommands: List[SubscribedItemInfo],
      badComponentNames: Set[String]
  ) {
    def isEmpty: Boolean =
        publishedEvents.isEmpty &&
        publishedObserveEvents.isEmpty &&
        publishedCurrentStates.isEmpty &&
        subscribedEvents.isEmpty &&
        subscribedObserveEvents.isEmpty &&
        subscribedCurrentStates.isEmpty &&
        receivedCommands.isEmpty &&
        sentCommands.isEmpty &&
        badComponentNames.isEmpty
  }

}

/**
 * Supports generating a "Missing Items" report.
 *
 * @param db      the database to use
 * @param options command line options used to narrow the scope of the report to given subsystems and components
 * @param pdfOptions PDF generation options
 */
//noinspection DuplicatedCode
case class MissingItemsReport(db: IcdDb, options: IcdDbOptions, pdfOptions: PdfOptions) {
  import MissingItemsReport._

  private val selectedSubsystemsWithVersions = List(options.subsystem, options.target).flatten
    .map(IcdVersionManager.SubsystemAndVersion(_))
    .map(s => SubsystemWithVersion(s.subsystem, s.maybeVersion, None))

  // Note: Need to search entire database in order to find the missing items
  private val query          = new CachedIcdDbQuery(db.db, db.admin, None)
  private val versionManager = new CachedIcdVersionManager(query)

  // s"$subsystem.$component" for all components in all subsystems (latest versions)
  private val allComponents = query.getComponents
    .map(c => s"${c.subsystem}.${c.component}")

  // Make the report only for the selected subsystems (-s and -t options), or all subsystems by default
  private val subsystemsWithVersions =
    if (selectedSubsystemsWithVersions.nonEmpty)
      selectedSubsystemsWithVersions
    else
      query.getSubsystemNames.map(SubsystemWithVersion(_, None, None))

  // Returns a list of items missing a publisher, subscriber, sender or receiver
  private def getMissingItems: Items = {

    def getPublishedItems(c: ComponentModel, names: List[String]): List[PublishedItemInfo] = {
      names.map(name => PublishedItemInfo(c.subsystem, c.component, c.prefix, name))
    }

    def getSubscribedItems(c: ComponentModel, list: List[SubsystemComponentName]): List[SubscribedItemInfo] = {
      list.map(
        p =>
          SubscribedItemInfo(
            c.subsystem,
            c.component,
            p.subsystem,
            p.component,
            query.getComponentModel(p.subsystem, p.component).map(_.prefix).getOrElse(""),
            p.name
          )
      )
    }

    def getItems: List[Items] = {
      for {
        sv        <- subsystemsWithVersions
        models    <- versionManager.getModels(sv)
        component <- models.componentModel
      } yield {
        val publishModel           = models.publishModel
        val subscribeModel         = models.subscribeModel
        val commandModel           = models.commandModel
        val publishedEvents        = getPublishedItems(component, publishModel.map(_.eventList.map(_.name)).getOrElse(Nil))
        val publishedObserveEvents = getPublishedItems(component, publishModel.map(_.observeEventList.map(_.name)).getOrElse(Nil))
        val publishedCurrentStates = getPublishedItems(component, publishModel.map(_.currentStateList.map(_.name)).getOrElse(Nil))

        val subscribedEvents        = getSubscribedItems(component, subscribeModel.map(_.eventList).getOrElse(Nil))
        val subscribedObserveEvents = getSubscribedItems(component, subscribeModel.map(_.observeEventList).getOrElse(Nil))
        val subscribedCurrentStates = getSubscribedItems(component, subscribeModel.map(_.currentStateList).getOrElse(Nil))

        val receivedCommands = getPublishedItems(component, commandModel.map(_.receive.map(_.name)).getOrElse(Nil))
        val sentCommands     = getSubscribedItems(component, commandModel.map(_.send).getOrElse(Nil))

        // "$subsystem.$component" for referenced components
        def getPubComp(i: PublishedItemInfo)     = s"${i.publisherSubsystem}.${i.publisherComponent}"
        def getSubPubComp(i: SubscribedItemInfo) = s"${i.publisherSubsystem}.${i.publisherComponent}"
        def getSubComp(i: SubscribedItemInfo)    = s"${i.subscriberSubsystem}.${i.subscriberComponent}"

        val compRefs =
            publishedEvents.map(getPubComp) ++
            publishedObserveEvents.map(getPubComp) ++
            publishedCurrentStates.map(getPubComp) ++
            subscribedEvents.map(getSubPubComp) ++ subscribedEvents.map(getSubComp) ++
            subscribedObserveEvents.map(getSubPubComp) ++ subscribedObserveEvents.map(getSubComp) ++
            subscribedCurrentStates.map(getSubPubComp) ++ subscribedCurrentStates.map(getSubComp) ++
            receivedCommands.map(getPubComp) ++
            sentCommands.map(getSubPubComp) ++ sentCommands.map(getSubComp)
        val compSet           = allComponents.toSet
        val badComponentNames = compRefs.filter(!compSet.contains(_)).toSet

        Items(
          publishedEvents,
          publishedObserveEvents,
          publishedCurrentStates,
          subscribedEvents,
          subscribedObserveEvents,
          subscribedCurrentStates,
          receivedCommands,
          sentCommands,
          badComponentNames
        )
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

    val publishedEvents           = items.flatMap(_.publishedEvents)
    val publishedEventsMap        = publishedEvents.map(e => e.key -> e).toMap
    val publishedObserveEvents    = items.flatMap(_.publishedObserveEvents)
    val publishedObserveEventsMap = publishedObserveEvents.map(e => e.key -> e).toMap
    val publishedCurrentStates    = items.flatMap(_.publishedCurrentStates)
    val publishedCurrentStatesMap = publishedCurrentStates.map(e => e.key -> e).toMap

    val subscribedEvents           = items.flatMap(_.subscribedEvents)
    val subscribedEventsMap        = subscribedEvents.map(e => e.key -> e).toMap
    val subscribedObserveEvents    = items.flatMap(_.subscribedObserveEvents)
    val subscribedObserveEventsMap = subscribedObserveEvents.map(e => e.key -> e).toMap
    val subscribedCurrentStates    = items.flatMap(_.subscribedCurrentStates)
    val subscribedCurrentStatesMap = subscribedCurrentStates.map(e => e.key -> e).toMap

    val receivedCommands   = items.flatMap(_.receivedCommands)
    val receivedCommandMap = receivedCommands.map(c => c.key -> c).toMap
    val sentCommands       = items.flatMap(_.sentCommands)
    val sentCommandsMap    = sentCommands.map(c => c.key -> c).toMap

    val publishedEventsWithNoSubscribers        = getPubNoSub(publishedEvents, subscribedEventsMap)
    val publishedObserveEventsWithNoSubscribers = getPubNoSub(publishedObserveEvents, subscribedObserveEventsMap)
    val publishedCurrentStatesWithNoSubscribers = getPubNoSub(publishedCurrentStates, subscribedCurrentStatesMap)

    val subscribedEventsWithNoPublisher        = getSubNoPub(subscribedEvents, publishedEventsMap)
    val subscribedObserveEventsWithNoPublisher = getSubNoPub(subscribedObserveEvents, publishedObserveEventsMap)
    val subscribedCurrentStatesWithNoPublisher = getSubNoPub(subscribedCurrentStates, publishedCurrentStatesMap)

    val receivedCommandsWithNoSenders = getPubNoSub(receivedCommands, sentCommandsMap)
    val sentCommandsWithNoReceivers   = getSubNoPub(sentCommands, receivedCommandMap)

    val badComponentNames = items.flatMap(_.badComponentNames).toSet

    Items(
      publishedEventsWithNoSubscribers,
      publishedObserveEventsWithNoSubscribers,
      publishedCurrentStatesWithNoSubscribers,
      subscribedEventsWithNoPublisher,
      subscribedObserveEventsWithNoPublisher,
      subscribedCurrentStatesWithNoPublisher,
      receivedCommandsWithNoSenders,
      sentCommandsWithNoReceivers,
      badComponentNames
    )
  }

  // Generates the HTML for the report
  private def makeReport(): String = {
    import scalatags.Text.all._

    def missingPubItemMarkup(title: String, info: List[PublishedItemInfo]): Text.TypedTag[String] = {
      if (info.isEmpty) div()
      else
        div(
          h3(title),
          div(
            table(
              thead(
                tr(
                  th("Subsystem"),
                  th("Component"),
                  th("Prefix"),
                  th("Name")
                )
              ),
              tbody(
                for {
                  item <- info
                } yield {
                  tr(td(p(item.publisherSubsystem)), td(p(item.publisherComponent)), td(p(item.prefix)), td(p(item.name)))
                }
              )
            )
          )
        )
    }

    def missingSubItemMarkup(
        title: String,
        info: List[SubscribedItemInfo],
        publisherTitle: String = "Publisher",
        subscriberTitle: String = "Subscriber"
    ): Text.TypedTag[String] = {
      if (info.isEmpty) div()
      else
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
                  th("Name")
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
                    td(p(item.name))
                  )
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
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
      ),
      body(
        h2("Missing Items"),
        missingPubItemMarkup("Published Events with no Subscribers", missingItems.publishedEvents),
        missingPubItemMarkup("Published Observe Events with no Subscribers", missingItems.publishedObserveEvents),
        missingPubItemMarkup("Published Current States with no Subscribers", missingItems.publishedCurrentStates),
        missingSubItemMarkup("Subscribed Events that are not Published Anywhere", missingItems.subscribedEvents),
        missingSubItemMarkup("Subscribed Observe Events that are not Published Anywhere", missingItems.subscribedObserveEvents),
        missingSubItemMarkup("Subscribed CurrentStates that are not Published Anywhere", missingItems.subscribedCurrentStates),
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

  // Generates the text/CSV formatted report
  private def makeCsvReport(dir: File): Unit = {
    import com.github.tototoshi.csv._

    implicit object MyFormat extends DefaultCSVFormat {
      override val lineTerminator = "\n"
    }

    def missingPubItemCsv(dir: File, title: String, info: List[PublishedItemInfo]): Unit = {
      if (info.nonEmpty) {
        val f      = new File(dir, title.replace(' ', '-'))
        val writer = CSVWriter.open(f)
        writer.writeRow(List("Subsystem", "Component", "Prefix", "Name"))
        info.foreach(i => writer.writeRow(List(i.publisherSubsystem, i.publisherComponent, i.prefix, i.name)))
        writer.close()
        println(s"Wrote $f")
      }
    }

    def missingSubItemCsv(
        dir: File,
        title: String,
        info: List[SubscribedItemInfo],
        publisherTitle: String = "Publisher",
        subscriberTitle: String = "Subscriber"
    ): Unit = {
      if (info.nonEmpty) {
        val f      = new File(dir, title.replace(' ', '-'))
        val writer = CSVWriter.open(f)
        writer.writeRow(
          List(
            s"$subscriberTitle Subsystem",
            s"$subscriberTitle Component",
            s"Declared $publisherTitle Subsystem",
            s"Declared $publisherTitle Component",
            "Prefix",
            "Name"
          )
        )
        info.foreach(
          i =>
            writer.writeRow(
              List(i.subscriberSubsystem, i.subscriberComponent, i.publisherSubsystem, i.publisherComponent, i.prefix, i.name)
            )
        )

        writer.close()
        println(s"Wrote $f")
      }
    }

    def badComponentNamesCsv(dir: File, title: String, info: List[String]): Unit = {
      if (info.nonEmpty) {
        val f      = new File(dir, title.replace(' ', '-'))
        val writer = CSVWriter.open(f)
        writer.writeRow(List("Name"))
        info.foreach(i => writer.writeRow(List(i)))
      }
    }

    val missingItems = getMissingItems
    if (missingItems.isEmpty) {
      println(s"No missing items were found.")
      System.exit(0)
    }
    if (dir.exists()) {
      if (dir.list().nonEmpty)
        println(s"Warning: Directory $dir already exists and contains files.")
    } else if (!dir.mkdirs()) {
      println(s"Could not create directory: $dir")
      System.exit(1)
    }

    missingPubItemCsv(dir, "Published Events with no Subscribers", missingItems.publishedEvents)
    missingPubItemCsv(dir, "Published Observe Events with no Subscribers", missingItems.publishedObserveEvents)
    missingPubItemCsv(dir, "Published Current States with no Subscribers", missingItems.publishedCurrentStates)

    missingSubItemCsv(dir, "Subscribed Events that are not Published Anywhere", missingItems.subscribedEvents)
    missingSubItemCsv(dir, "Subscribed Observe Events that are not Published Anywhere", missingItems.subscribedObserveEvents)
    missingSubItemCsv(dir, "Subscribed Current States that are not Published Anywhere", missingItems.subscribedCurrentStates)

    missingPubItemCsv(dir, "Received Commands with no Senders", missingItems.receivedCommands)
    missingSubItemCsv(dir, "Sent Commands that are not Defined Anywhere", missingItems.sentCommands, "Receiver", "Sender")

    badComponentNamesCsv(
      dir,
      "Component names that were Referenced but not Defined Anywhere",
      missingItems.badComponentNames.toList
    )
  }

  /**
   * Saves the report in HTML or PDF, depending on the file suffix
   */
  def saveToFile(file: File, pdfOptions: PdfOptions): Unit = {

    def saveAsHtml(html: String): Unit = {
      val out = new FileOutputStream(file)
      out.write(html.getBytes)
      out.close()
    }

    def saveAsPdf(html: String): Unit = IcdToPdf.saveAsPdf(file, html, showLogo = false, pdfOptions)

    file.getName.split('.').drop(1).lastOption match {
      case Some("html") => saveAsHtml(makeReport())
      case Some("pdf")  => saveAsPdf(makeReport())
      case _            => makeCsvReport(file)
    }
  }
}
