package csw.services.icd.db

import java.io.*
import csw.services.icd.IcdToPdf
import csw.services.icd.html.{IcdToHtml, NumberedHeadings}
import icd.web.shared.IcdModels.*
import icd.web.shared.{Headings, PdfOptions, SubsystemWithVersion}
import scalatags.Text

/**
 * Defines classes used to generate the "missing items" report
 */
object MissingItemsReport {

  private case class PublishedItemInfo(publisherSubsystem: String, publisherComponent: String, prefix: String, name: String) {
    val key = s"$prefix.$name"
  }

  private case class SubscribedItemInfo(
      subscriberSubsystem: String,
      subscriberComponent: String,
      publisherSubsystem: String,
      publisherComponent: String,
      prefix: String,
      name: String
  ) {
    val key = s"$prefix.$name"
  }

  private case class Items(
      publishedEvents: List[PublishedItemInfo],
      publishedObserveEvents: List[PublishedItemInfo],
      publishedCurrentStates: List[PublishedItemInfo],
      publishedImages: List[PublishedItemInfo],
      subscribedEvents: List[SubscribedItemInfo],
      subscribedObserveEvents: List[SubscribedItemInfo],
      subscribedCurrentStates: List[SubscribedItemInfo],
      subscribedImages: List[SubscribedItemInfo],
      receivedCommands: List[PublishedItemInfo],
      sentCommands: List[SubscribedItemInfo],
      providedServices: List[PublishedItemInfo],
      requiredServices: List[SubscribedItemInfo],
      badComponentNames: Set[String]
  ) {
    def isEmpty: Boolean =
      publishedEvents.isEmpty &&
        publishedObserveEvents.isEmpty &&
        publishedCurrentStates.isEmpty &&
        publishedImages.isEmpty &&
        subscribedEvents.isEmpty &&
        subscribedObserveEvents.isEmpty &&
        subscribedCurrentStates.isEmpty &&
        subscribedImages.isEmpty &&
        receivedCommands.isEmpty &&
        sentCommands.isEmpty &&
        providedServices.isEmpty &&
        requiredServices.isEmpty &&
        badComponentNames.isEmpty
  }

}

/**
 * Supports generating a "Missing Items" report.
 *
 * @param db      the database to use
 * @param subsystems used to narrow the scope of the report to given subsystems and components
 * @param pdfOptions PDF generation options
 */
//noinspection DuplicatedCode
case class MissingItemsReport(db: IcdDb, subsystems: List[SubsystemWithVersion], pdfOptions: PdfOptions) {
  import MissingItemsReport.*

  private val selectedSubsystemNames = subsystems.map(_.subsystem)

  // Note: If one subsystem was specified, search entire database in order to find the missing items,
  // otherwise only search the given subsystems
  private val maybeSubsystems = if (subsystems.size <= 1) None else Some(selectedSubsystemNames)
  private val query           = new CachedIcdDbQuery(db.db, db.admin, maybeSubsystems, None, Map.empty)
  private val versionManager  = new CachedIcdVersionManager(db)
  private val otherSv =
    if (subsystems.size <= 1)
      query.getSubsystemNames.filter(s => !selectedSubsystemNames.contains(s)).map(SubsystemWithVersion(_, None, None))
    else Nil

  // "subsystem.component" for all components in all subsystems (latest versions or specified version)
  private val allComponents = {
    val currentComps = query
      .getComponents(None)
      .filter(cm => !subsystems.exists(s => s.subsystem == cm.subsystem))
      .map(c => s"${c.subsystem}.${c.component}")
    val specifiedVersionComps = subsystems
      .flatMap(sv => versionManager.getComponentNames(sv).map(c => s"${sv.subsystem}.$c"))
    specifiedVersionComps ::: currentComps
  }

  private val introText = if (subsystems.isEmpty) {
    s"""
         |This report takes the list of published and subscribed events, received and sent commands for
         |all subsystems and looks for matches in the latest versions of all the other subsystems.
         |The tables below list the names of the events and commands for which no matches were found.
         |Note that this could be due to changes in the latest versions of the other subsystems.
         |To avoid this issue, specify a list of subsystems with versions (if using the icd-db -m command line
         |option), or select two subsystems with versions or a published ICD in the icd web app.
         |""".stripMargin
  }
  else if (subsystems.size == 1) {
    s"""
         |This report takes the list of published and subscribed events, received and sent commands for
         |${subsystems.head.toStringWithVersion} and looks for matches in the latest versions of
         |all the other subsystems.
         |The tables below list the names of the events and commands for which no matches were found.
         |Note that this could be due to changes in the latest versions of the other subsystems.
         |To avoid this issue, specify a list of subsystems with versions (if using the icd-db -m command line
         |option), or select two subsystems with versions or a published ICD in the icd web app.
         |""".stripMargin
  }
  else
    s"""
       |This report takes the list of published and subscribed events, received and sent commands defined for
       |${subsystems.map(_.toStringWithVersion).mkString(" and ")} and looks for matches
       |in the same group of subsystems.
       |The tables below list the names of the events and commands for which no matches were found.
       |""".stripMargin

  // Returns a list of items missing a publisher, subscriber, sender or receiver
  private def getMissingItems: Items = {

    def getPublishedItems(c: ComponentModel, names: List[String]): List[PublishedItemInfo] = {
      names.map(name => PublishedItemInfo(c.subsystem, c.component, c.prefix, name))
    }

    def getSubscribedItems(c: ComponentModel, list: List[SubsystemComponentName]): List[SubscribedItemInfo] = {
      list.map(p =>
        SubscribedItemInfo(
          c.subsystem,
          c.component,
          p.subsystem,
          p.component,
          s"${p.subsystem}.${p.component}",
          p.name
        )
      )
    }

    def getItems: List[Items] = {
      for {
        sv        <- subsystems ::: otherSv
        models    <- versionManager.getModels(sv.copy(maybeComponent = None), Some(pdfOptions))
        component <- models.componentModel
      } yield {
        val publishModel           = models.publishModel
        val subscribeModel         = models.subscribeModel
        val commandModel           = models.commandModel
        val serviceModel           = models.serviceModel
        val publishedEvents        = getPublishedItems(component, publishModel.map(_.eventList.map(_.name)).getOrElse(Nil))
        val publishedObserveEvents = getPublishedItems(component, publishModel.map(_.observeEventList.map(_.name)).getOrElse(Nil))
        val publishedCurrentStates = getPublishedItems(component, publishModel.map(_.currentStateList.map(_.name)).getOrElse(Nil))
        val publishedImages        = getPublishedItems(component, publishModel.map(_.imageList.map(_.name)).getOrElse(Nil))

        val subscribedEvents        = getSubscribedItems(component, subscribeModel.map(_.eventList).getOrElse(Nil))
        val subscribedObserveEvents = getSubscribedItems(component, subscribeModel.map(_.observeEventList).getOrElse(Nil))
        val subscribedCurrentStates = getSubscribedItems(component, subscribeModel.map(_.currentStateList).getOrElse(Nil))
        val subscribedImages        = getSubscribedItems(component, subscribeModel.map(_.imageList).getOrElse(Nil))

        val receivedCommands = getPublishedItems(component, commandModel.map(_.receive.map(_.name)).getOrElse(Nil))
        val sentCommands     = getSubscribedItems(component, commandModel.map(_.send).getOrElse(Nil))

        val providedServices = getPublishedItems(component, serviceModel.map(_.provides.map(_.name)).getOrElse(Nil))
        val requiredServices = getSubscribedItems(component, serviceModel.toList.flatMap(_.requires))

        // "$subsystem.$component" for referenced components
        def getPubComp(i: PublishedItemInfo)     = s"${i.publisherSubsystem}.${i.publisherComponent}"
        def getSubPubComp(i: SubscribedItemInfo) = s"${i.publisherSubsystem}.${i.publisherComponent}"
        def getSubComp(i: SubscribedItemInfo)    = s"${i.subscriberSubsystem}.${i.subscriberComponent}"

        val compRefs =
          publishedEvents.filter(pubFilter).map(getPubComp) ++
            publishedObserveEvents.filter(pubFilter).map(getPubComp) ++
            publishedCurrentStates.filter(pubFilter).map(getPubComp) ++
            publishedImages.filter(pubFilter).map(getPubComp) ++
            subscribedEvents.filter(subFilter).map(getSubPubComp) ++ subscribedEvents.map(getSubComp) ++
            subscribedObserveEvents.filter(subFilter).map(getSubPubComp) ++ subscribedObserveEvents.map(getSubComp) ++
            subscribedCurrentStates.filter(subFilter).map(getSubPubComp) ++ subscribedCurrentStates.map(getSubComp) ++
            subscribedImages.filter(subFilter).map(getSubPubComp) ++ subscribedImages.map(getSubComp) ++
            receivedCommands.filter(pubFilter).map(getPubComp) ++
            sentCommands.filter(subFilter).map(getSubPubComp) ++ sentCommands.map(getSubComp) ++
            providedServices.filter(pubFilter).map(getPubComp) ++
            requiredServices.filter(subFilter).map(getSubPubComp) ++ requiredServices.map(getSubComp)
        val compSet           = allComponents.toSet
        val badComponentNames = compRefs.toSet.filter(!compSet.contains(_))

        Items(
          publishedEvents = publishedEvents,
          publishedObserveEvents = publishedObserveEvents,
          publishedCurrentStates = publishedCurrentStates,
          publishedImages = publishedImages,
          subscribedEvents = subscribedEvents,
          subscribedObserveEvents = subscribedObserveEvents,
          subscribedCurrentStates = subscribedCurrentStates,
          subscribedImages = subscribedImages,
          receivedCommands = receivedCommands,
          sentCommands = sentCommands,
          providedServices = providedServices,
          requiredServices = requiredServices,
          badComponentNames = badComponentNames
        )
      }
    }

    def pubFilter(p: PublishedItemInfo): Boolean = {
      subsystems.size match {
        case 0 => true
        case _ =>
          selectedSubsystemNames.contains(p.publisherSubsystem) &&
            subsystems.exists(s =>
              s.subsystem == p.publisherSubsystem && (s.maybeComponent.isEmpty || s.maybeComponent.contains(p.publisherComponent))
            )
      }
    }

    def subFilter(p: SubscribedItemInfo): Boolean = {
      subsystems.size match {
        case 0 => true
        case 1 =>
          (selectedSubsystemNames.contains(p.subscriberSubsystem) || selectedSubsystemNames.contains(p.publisherSubsystem)) &&
            subsystems.exists(s =>
              (s.subsystem == p.subscriberSubsystem && (s.maybeComponent.isEmpty || s.maybeComponent
                .contains(p.subscriberComponent))) ||
                (s.subsystem == p.publisherSubsystem && (s.maybeComponent.isEmpty || s.maybeComponent
                  .contains(p.publisherComponent)))
            )
        case 2 =>
          // DEOPSICDDB-172: Limit missing items to items that both subsystems in ICD are involved in
          p.subscriberSubsystem != p.publisherSubsystem &&
            selectedSubsystemNames.contains(p.subscriberSubsystem) && selectedSubsystemNames.contains(p.publisherSubsystem) &&
            subsystems.exists(s =>
              s.subsystem == p.subscriberSubsystem && (s.maybeComponent.isEmpty || s.maybeComponent
                .contains(p.subscriberComponent))
            )
        case _ =>
          selectedSubsystemNames.contains(p.subscriberSubsystem) && selectedSubsystemNames.contains(p.publisherSubsystem) &&
            subsystems.exists(s =>
              s.subsystem == p.subscriberSubsystem && (s.maybeComponent.isEmpty || s.maybeComponent
                .contains(p.subscriberComponent))
            )
      }
    }

    // Return list of published items with no subscribers (Only if searching all subsystems)
    def getPubNoSub(published: List[PublishedItemInfo], subscribed: Map[String, SubscribedItemInfo]): List[PublishedItemInfo] = {
//      if (subsystems.size <= 1)
//        published.filter(p => pubFilter(p) && !subscribed.contains(p.key))
//       else
      // XXX For now, don't show this, since it seems less important than subscribed items that are not defined anywhere
      Nil
    }

    // Return list of subscribed items with no publisher
    def getSubNoPub(subscribes: List[SubscribedItemInfo], publishes: Map[String, PublishedItemInfo]): List[SubscribedItemInfo] = {
      subscribes.filter(p => subFilter(p) && !publishes.contains(p.key))
    }

    val items = getItems

    val publishedEvents           = items.flatMap(_.publishedEvents)
    val publishedEventsMap        = publishedEvents.map(e => e.key -> e).toMap
    val publishedObserveEvents    = items.flatMap(_.publishedObserveEvents)
    val publishedObserveEventsMap = publishedObserveEvents.map(e => e.key -> e).toMap
    val publishedCurrentStates    = items.flatMap(_.publishedCurrentStates)
    val publishedCurrentStatesMap = publishedCurrentStates.map(e => e.key -> e).toMap
    val publishedImages           = items.flatMap(_.publishedImages)
    val publishedImagesMap        = publishedImages.map(e => e.key -> e).toMap

    val subscribedEvents           = items.flatMap(_.subscribedEvents)
    val subscribedEventsMap        = subscribedEvents.map(e => e.key -> e).toMap
    val subscribedObserveEvents    = items.flatMap(_.subscribedObserveEvents)
    val subscribedObserveEventsMap = subscribedObserveEvents.map(e => e.key -> e).toMap
    val subscribedCurrentStates    = items.flatMap(_.subscribedCurrentStates)
    val subscribedCurrentStatesMap = subscribedCurrentStates.map(e => e.key -> e).toMap
    val subscribedImages           = items.flatMap(_.subscribedImages)
    val subscribedImagesMap        = subscribedImages.map(e => e.key -> e).toMap

    val receivedCommands   = items.flatMap(_.receivedCommands)
    val receivedCommandMap = receivedCommands.map(c => c.key -> c).toMap
    val sentCommands       = items.flatMap(_.sentCommands)
    val sentCommandsMap    = sentCommands.map(c => c.key -> c).toMap

    val providedServices    = items.flatMap(_.providedServices)
    val providedServicesMap = providedServices.map(c => c.key -> c).toMap
    val requiredServices    = items.flatMap(_.requiredServices)
    val requiredServicesMap = requiredServices.map(c => c.key -> c).toMap

    val publishedEventsWithNoSubscribers        = getPubNoSub(publishedEvents, subscribedEventsMap)
    val publishedObserveEventsWithNoSubscribers = getPubNoSub(publishedObserveEvents, subscribedObserveEventsMap)
    val publishedCurrentStatesWithNoSubscribers = getPubNoSub(publishedCurrentStates, subscribedCurrentStatesMap)
    val publishedImagesWithNoSubscribers        = getPubNoSub(publishedImages, subscribedImagesMap)

    val subscribedEventsWithNoPublisher        = getSubNoPub(subscribedEvents, publishedEventsMap)
    val subscribedObserveEventsWithNoPublisher = getSubNoPub(subscribedObserveEvents, publishedObserveEventsMap)
    val subscribedCurrentStatesWithNoPublisher = getSubNoPub(subscribedCurrentStates, publishedCurrentStatesMap)
    val subscribedImagesWithNoPublisher        = getSubNoPub(subscribedImages, publishedImagesMap)

    val receivedCommandsWithNoSenders = getPubNoSub(receivedCommands, sentCommandsMap)
    val sentCommandsWithNoReceivers   = getSubNoPub(sentCommands, receivedCommandMap)

    val providedServicesWithNoUsers    = getPubNoSub(providedServices, requiredServicesMap)
    val requiredServicesWithNoProvider = getSubNoPub(requiredServices, providedServicesMap)

    val badComponentNames = items.flatMap(_.badComponentNames).toSet

    Items(
      publishedEvents = publishedEventsWithNoSubscribers,
      publishedObserveEvents = publishedObserveEventsWithNoSubscribers,
      publishedCurrentStates = publishedCurrentStatesWithNoSubscribers,
      publishedImages = publishedImagesWithNoSubscribers,
      subscribedEvents = subscribedEventsWithNoPublisher,
      subscribedObserveEvents = subscribedObserveEventsWithNoPublisher,
      subscribedCurrentStates = subscribedCurrentStatesWithNoPublisher,
      subscribedImages = subscribedImagesWithNoPublisher,
      receivedCommands = receivedCommandsWithNoSenders,
      sentCommands = sentCommandsWithNoReceivers,
      providedServices = providedServicesWithNoUsers,
      requiredServices = requiredServicesWithNoProvider,
      badComponentNames = badComponentNames
    )
  }

  // Generates the HTML markup for the body of the report
  def makeReportMarkup(headings: Headings): Text.TypedTag[String] = {
    import scalatags.Text.all.*

    def missingPubItemMarkup(title: String, info: List[PublishedItemInfo]): Text.TypedTag[String] = {
      if (info.isEmpty) div()
      else
        div(
          headings.H3(title),
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
          headings.H3(title),
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
    if (missingItems.isEmpty) {
      div()
    }
    else {
      div(
        headings.H2("Missing Items"),
        missingPubItemMarkup("Published Events with no Subscribers", missingItems.publishedEvents),
        missingPubItemMarkup("Published Observe Events with no Subscribers", missingItems.publishedObserveEvents),
        missingPubItemMarkup("Published Current States with no Subscribers", missingItems.publishedCurrentStates),
        missingPubItemMarkup("Published Images with no Subscribers", missingItems.publishedImages),
        missingSubItemMarkup("Subscribed Events that are not Published Anywhere", missingItems.subscribedEvents),
        missingSubItemMarkup("Subscribed Observe Events that are not Published Anywhere", missingItems.subscribedObserveEvents),
        missingSubItemMarkup("Subscribed CurrentStates that are not Published Anywhere", missingItems.subscribedCurrentStates),
        missingSubItemMarkup("Subscribed Images that are not Published Anywhere", missingItems.subscribedImages),
        missingPubItemMarkup("Received Commands with no Senders", missingItems.receivedCommands),
        missingSubItemMarkup("Sent Commands that are not Defined Anywhere", missingItems.sentCommands, "Receiver", "Sender"),
        missingPubItemMarkup("Provided Services with no Users", missingItems.providedServices),
        missingSubItemMarkup("Required Services with no Providers", missingItems.requiredServices, "Provider", "User"),
        if (missingItems.badComponentNames.isEmpty) div()
        else
          div(
            headings.H3("Component names that were Referenced but not Defined Anywhere"),
            ul(
              missingItems.badComponentNames.toList.map(s => li(s))
            )
          )
      )
    }
  }

  // Generates the HTML for the report
  def makeReport(): String = {
    import scalatags.Text.all.*
    val nh            = new NumberedHeadings
    val forSubsystems = if (subsystems.isEmpty) "" else s" for ${subsystems.map(_.toStringWithVersion).mkString(", ")}"
    val titleStr      = s"Missing Items Report$forSubsystems"
    val markup = html(
      head(
        scalatags.Text.tags2.title(titleStr),
        scalatags.Text.tags2.style(scalatags.Text.RawFrag(IcdToHtml.getCss(pdfOptions)))
      ),
      body(
        div(
          h3(cls := "page-header", titleStr),
          p(introText),
          h2("Table of Contents"),
          nh.mkToc()
        ),
        makeReportMarkup(nh)
      )
    )
    markup.render
  }

  // Generates the text/CSV formatted report
  private def makeCsvReport(dir: File): Unit = {
    import com.github.tototoshi.csv.*

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
        info.foreach(i =>
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
    }
    else if (!dir.mkdirs()) {
      println(s"Could not create directory: $dir")
      System.exit(1)
    }

    missingPubItemCsv(dir, "Published Events with no Subscribers", missingItems.publishedEvents)
    missingPubItemCsv(dir, "Published Observe Events with no Subscribers", missingItems.publishedObserveEvents)
    missingPubItemCsv(dir, "Published Current States with no Subscribers", missingItems.publishedCurrentStates)
    missingPubItemCsv(dir, "Published Images with no Subscribers", missingItems.publishedImages)

    missingSubItemCsv(dir, "Subscribed Events that are not Published Anywhere", missingItems.subscribedEvents)
    missingSubItemCsv(dir, "Subscribed Observe Events that are not Published Anywhere", missingItems.subscribedObserveEvents)
    missingSubItemCsv(dir, "Subscribed Current States that are not Published Anywhere", missingItems.subscribedCurrentStates)
    missingSubItemCsv(dir, "Subscribed Images that are not Published Anywhere", missingItems.subscribedImages)

    missingPubItemCsv(dir, "Received Commands with no Senders", missingItems.receivedCommands)
    missingSubItemCsv(dir, "Sent Commands that are not Defined Anywhere", missingItems.sentCommands, "Receiver", "Sender")

    missingPubItemCsv(dir, "Provided Services with no Users", missingItems.providedServices)
    missingSubItemCsv(dir, "Required Services with no Providers", missingItems.requiredServices, "Provider", "User")

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
