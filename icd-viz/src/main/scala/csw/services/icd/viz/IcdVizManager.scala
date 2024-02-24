package csw.services.icd.viz

import java.awt.Desktop
import java.io.{File, FileOutputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import com.typesafe.config.{Config, ConfigFactory}
import csw.services.icd.IcdValidator
import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdComponentInfo, IcdDb, Subsystems}
import csw.services.icd.viz.IcdVizManager.EdgeType.EdgeType
import csw.services.icd.viz.IcdVizManager.MissingType.MissingType
import icd.web.shared.{
  ComponentInfo,
  DetailedSubscribeInfo,
  EventOrImageInfo,
  IcdVizOptions,
  PdfOptions,
  ReceivedCommandInfo,
  SentCommandInfo,
  SubscribeInfo,
  SubsystemWithVersion
}
import icd.web.shared.IcdModels.{ComponentModel, SubscribeModelInfo}
import net.sourceforge.plantuml.{FileFormat, FileFormatOption, SourceStringReader}
import scalax.collection.OneOrMore
import scalax.collection.config.CoreConfig
import scalax.collection.generic.{AbstractDiEdge, MultiEdge}
import scalax.collection.immutable.{Graph, TypedGraphFactory}
import scalax.collection.io.dot.*
import scalax.collection.io.dot.implicits.*

import language.implicitConversions

//noinspection SpellCheckingInspection
object IcdVizManager {
  //noinspection TypeAnnotation
  object MissingType extends Enumeration {
    type MissingType = Value
    val noEventSubscribers    = Value("No subscribers")
    val missingEventPublisher = Value("Missing in publisher")
    val noImageSubscribers    = Value("No image subscribers")
    val missingImagePublisher = Value("Image missing in publisher")
    val noSenders             = Value("No senders")
    val missingReceiver       = Value("Missing in receiver")
  }

  //noinspection TypeAnnotation
  // Event and Command edges have different colors
  object EdgeType extends Enumeration {
    type EdgeType = Value
    val events   = Value("Events")
    val images   = Value("Images")
    val commands = Value("Commands")
  }

  // Type of an edge label
  private case class EdgeLabel(labels: List[String], edgeType: EdgeType, missing: Option[MissingType] = None) {
    lazy val label: String = labels.sorted.mkString("\\n")
  }

  // Holds both components of an edge
  private case class ComponentPair(from: String, to: String)

  // Describes an Edge from one component to another
  private case class EdgeModel(components: ComponentPair, label: EdgeLabel)

  // Labeled directed edges
  private case class MyLDiEdge(model: EdgeModel)
      extends AbstractDiEdge(source = model.components.from, target = model.components.to)
      with MultiEdge {
    override def extendKeyBy: OneOrMore[Any] = OneOrMore.one(model)
  }

  private object MyGraph extends TypedGraphFactory[String, MyLDiEdge]

  // Configuration options for Graph
  implicit val myConfig: CoreConfig = CoreConfig()

  // Load settings from reference.conf
  private val conf: Config = ConfigFactory.load

  // Read subsystem color settings from reference.conf
  private val subsystemColorMap: Map[String, String] =
    Subsystems.allSubsystems.map(s => s -> conf.getString(s"icd.viz.color.$s")).toMap

  // --- plotting defaults ---

  private def getSubsystemColor(subsystem: String): String = {
    subsystem match {
      case "?" => "red"
      case _   => subsystemColorMap.getOrElse(subsystem, "grey")
    }
  }

  private val commandColor = "chocolate" // command color
  private val eventColor   = "dimgrey"   // event colors
  private val imageColor   = "purple"    // image colors
  private val missingColor = "red"       // missing command or event color

  private val nodeFontsize      = 20
  private val edgeFontsize      = 10
  private val subsystemFontsize = 30

  /**
   * Returns a string in Graphviz/dot format showing the relationships between
   * the selected subsystems/components according to the given options.
   * @param db the icd database
   * @param options the options
   * @param maybeOut optional output stream to hold the generated image
   */
  def showRelationships(db: IcdDb, options: IcdVizOptions, maybeOut: Option[OutputStream] = None): Unit = {
    val query          = new CachedIcdDbQuery(db.db, db.admin, None, None, Map.empty)
    val versionManager = new CachedIcdVersionManager(query)

    // Add components from user-specified subsystems
    val subsystemComponents = options.subsystems.flatMap(sv =>
      db.versionManager
        .getComponentNames(sv)
        .map(name => SubsystemWithVersion(sv.subsystem, sv.maybeVersion, Some(name)))
    )

    // Add selected components and remove any omitted component types
    val components = (subsystemComponents ++ options.components)
      .flatMap { sv =>
        val maybeComponentModel = db.versionManager.getComponentModel(sv, None) match {
          case Some(componentModel) =>
            if (options.omitTypes.contains(componentModel.componentType))
              None
            else Some(componentModel)
          case None => None
        }
        maybeComponentModel.map(_ => sv)
      }

    val noMarkdownOpt = Some(PdfOptions(processMarkdown = false))

    val subsystems = components.map(c => SubsystemWithVersion(c.subsystem, c.maybeVersion, None)).distinct

    // For ICDs, only display the info related to the two subsystems/components
    val isIcd = options.subsystems.length + options.components.length == 2

    val componentInfoList = if (isIcd) {
      val svList  = options.subsystems ::: options.components
      val sv1     = svList.head
      val sv2     = svList.tail.head
      val sv1List = components.filter(_.subsystem == sv1.subsystem)
      sv1List.flatMap(sv => IcdComponentInfo.getComponentInfo(versionManager, sv, sv2, noMarkdownOpt))
    }
    else {
      val componentInfoHelper =
        new ComponentInfoHelper(versionManager, displayWarnings = false, clientApi = true, subsystems)
      components.flatMap(sv => componentInfoHelper.getComponentInfo(sv, noMarkdownOpt, Map.empty))
    }

    def getSubsystemFromPrefix(prefix: String): String = {
      prefix.indexOf(".") match {
        case -1 => prefix
        case i  => prefix.substring(0, i)
      }
    }

    def makeComponentPair(from: String, to: String): ComponentPair = {
      if (options.onlySubsystems)
        ComponentPair(getSubsystemFromPrefix(from), getSubsystemFromPrefix(to))
      else
        ComponentPair(from, to)
    }

    def componentNameFromPrefix(prefix: String): String = {
      val sv = SubsystemWithVersion(prefix)
      // Use full prefix for subsystems that don't have a specific color
      if (getSubsystemColor(sv.subsystem) == "grey" && !options.groupSubsystems)
        prefix
      else
        sv.maybeComponent.getOrElse(prefix)
    }

    // Returns true if the component should not be omitted
    def omitFilter(componentModel: ComponentModel): Boolean = {
      !options.omitTypes.contains(componentModel.componentType)
    }

    // Get info about subscribed events and the components involved
    def getSubscriberInfo(info: ComponentInfo, images: Boolean): (List[DetailedSubscribeInfo], List[ComponentModel]) = {
      val subscribes = info.subscribes.toList
        .flatMap(_.subscribeInfo)
        .filter(d =>
          (if (images) d.imageModel.isDefined else d.eventModel.isDefined) && d.publisher.isDefined && omitFilter(d.publisher.get)
        )
      (
        subscribes,
        subscribes
          .map(_.publisher.get)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets the component model for the given subsystem/component, making sure to use
    // the selected subsystem version, if subsystem is one of the primary subsystems and
    // a version was specified.
    def getComponentModel(subsystem: String, componentName: String): Option[ComponentModel] = {
      components.find(c => c.subsystem == subsystem && c.maybeVersion.nonEmpty) match {
        case Some(sv) =>
          val compSv = sv.copy(maybeComponent = Some(componentName))
          db.versionManager.getComponentModel(compSv, noMarkdownOpt)
        case None =>
          db.query.getComponentModel(subsystem, componentName, noMarkdownOpt)
      }
    }

    // Get info about subscribed events or images where the publisher doesn't publish the event
    def getMissingPublisherInfo(info: ComponentInfo, images: Boolean): (List[DetailedSubscribeInfo], List[ComponentModel]) = {
      if (options.missingEvents) {
        val subscribes = info.subscribes.toList
          .flatMap(_.subscribeInfo)
          .filter(d => images == (d.itemType == ComponentInfo.Images) && d.imageModel.isEmpty && d.eventModel.isEmpty)
        (
          subscribes,
          subscribes
            .map(d =>
              getComponentModel(d.subscribeModelInfo.subsystem, d.subscribeModelInfo.component)
                .getOrElse(
                  ComponentModel(
                    "?",
                    d.subscribeModelInfo.subsystem,
                    d.subscribeModelInfo.component,
                    d.subscribeModelInfo.component,
                    d.subscribeModelInfo.component,
                    IcdValidator.currentSchemaVersion,
                    ""
                  )
                )
            )
            .distinct
            .filter(omitFilter)
            .filter(_.prefix != info.componentModel.prefix)
        )
      }
      else (Nil, Nil)
    }

    // Gets info about published events and the components involved
    def getPublisherInfo(info: ComponentInfo, images: Boolean): (List[EventOrImageInfo], List[ComponentModel]) = {
      val infoList =
        if (images)
          info.publishes.toList
            .flatMap(p => p.imageList)
            .filter(_.subscribers.nonEmpty)
            .map(e => EventOrImageInfo(e.imageModel, e.subscribers.filter(s => omitFilter(s.componentModel))))
        else
          info.publishes.toList
            .flatMap(p => p.currentStateList ++ p.eventList ++ p.observeEventList)
            .filter(_.subscribers.nonEmpty)
            .map(e => EventOrImageInfo(e.eventModel, e.subscribers.filter(s => omitFilter(s.componentModel))))
      (
        infoList,
        infoList
          .flatMap(_.subscribers)
          .map(_.componentModel)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets info about published events with no subscribers
    def getMissingSubscriberInfo(info: ComponentInfo, images: Boolean): (List[EventOrImageInfo], List[ComponentModel]) = {
      if (options.missingEvents) {
        val missingComponentModel = ComponentModel("?", info.componentModel.subsystem, "?", "?", "?", "?", "")
        val infoList =
          if (images)
            info.publishes.toList
              .flatMap(p => p.imageList)
              .filter(_.subscribers.isEmpty)
              .map(e =>
                // Create dummy subscriber component
                EventOrImageInfo(
                  e.imageModel,
                  List(
                    SubscribeInfo(
                      missingComponentModel,
                      ComponentInfo.Images,
                      SubscribeModelInfo(
                        info.componentModel.subsystem,
                        info.componentModel.component,
                        e.imageModel.name,
                        "",
                        1.0,
                        None
                      )
                    )
                  )
                )
              )
          else
            info.publishes.toList
              .flatMap(p => p.currentStateList ++ p.eventList ++ p.observeEventList)
              .filter(_.subscribers.isEmpty)
              .map(e =>
                // Create dummy subscriber component
                EventOrImageInfo(
                  e.eventModel,
                  List(
                    SubscribeInfo(
                      missingComponentModel,
                      ComponentInfo.Events,
                      SubscribeModelInfo(
                        info.componentModel.subsystem,
                        info.componentModel.component,
                        e.eventModel.name,
                        "",
                        1.0,
                        None
                      )
                    )
                  )
                )
              )
        (
          infoList,
          infoList.map(_ => missingComponentModel)
        )
      }
      else (Nil, Nil)
    }

    // Gets information about commands a component sends and the receiver components involved
    def getSentCommandInfo(info: ComponentInfo): (List[SentCommandInfo], List[ComponentModel]) = {
      val sentCommands = info.commands.toList.flatMap(_.commandsSent).filter(_.receiveCommandModel.isDefined)
      (
        sentCommands,
        sentCommands
          .map(_.receiver.get)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets information about commands a component sends where no receiver was found
    def getMissingReceiverInfo(info: ComponentInfo): (List[SentCommandInfo], List[ComponentModel]) = {
      if (options.missingCommands) {
        val sentCommands = info.commands.toList
          .flatMap(_.commandsSent)
          .filter(_.receiveCommandModel.isEmpty)
        (
          sentCommands,
          sentCommands
            .map(s =>
              getComponentModel(s.subsystem, s.component)
                .getOrElse(
                  ComponentModel(
                    "?",
                    s.subsystem,
                    s.component,
                    s.component,
                    s.component,
                    IcdValidator.currentSchemaVersion,
                    ""
                  )
                )
            )
            .distinct
            .filter(omitFilter)
            .filter(_.prefix != info.componentModel.prefix)
        )
      }
      else (Nil, Nil)
    }

    // Gets information about commands received and the components sending the commands
    def getReceivedCommandInfo(info: ComponentInfo): (List[ReceivedCommandInfo], List[ComponentModel]) = {
      val receivedCommands = info.commands.toList.flatMap(_.commandsReceived).filter(_.senders.nonEmpty)
      (
        receivedCommands,
        receivedCommands
          .flatMap(_.senders)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets information about commands received where there are no senders
    def getMissingSenderCommandInfo(info: ComponentInfo): (List[ReceivedCommandInfo], List[ComponentModel]) = {
      if (options.missingCommands) {
        val missingComponentModel = ComponentModel("?", info.componentModel.subsystem, "?", "?", "?", "?", "")
        val receivedCommands = info.commands.toList
          .flatMap(_.commandsReceived)
          .filter(_.senders.isEmpty)
          .map(r => ReceivedCommandInfo(r.receiveCommandModel, List(missingComponentModel)))
        (
          receivedCommands,
          receivedCommands.map(_ => missingComponentModel)
        )
      }
      else (Nil, Nil)
    }

    // Primary components are the ones specified in the options
    val primaryComponents = componentInfoList.map(_.componentModel).filter(omitFilter)

    // All subsystems related to the primary components
    val allSubsystems = componentInfoList.flatMap { info =>
      val (_, eventSubscriberComponents)       = getSubscriberInfo(info, images = false)
      val (_, missingEventPublisherComponents) = getMissingPublisherInfo(info, images = false)

      val (_, eventPublisherComponents)         = getPublisherInfo(info, images = false)
      val (_, missingEventSubscriberComponents) = getMissingSubscriberInfo(info, images = false)

      val (_, imageSubscriberComponents)       = getSubscriberInfo(info, images = true)
      val (_, missingImagePublisherComponents) = getMissingPublisherInfo(info, images = true)

      val (_, imagePublisherComponents)         = getPublisherInfo(info, images = true)
      val (_, missingImageSubscriberComponents) = getMissingSubscriberInfo(info, images = true)

      val (_, receivierComponents)        = getSentCommandInfo(info)
      val (_, missingReceivierComponents) = getMissingReceiverInfo(info)

      val (_, senderComponents)        = getReceivedCommandInfo(info)
      val (_, missingSenderComponents) = getMissingSenderCommandInfo(info)

      (info.componentModel :: (eventSubscriberComponents ++
        missingEventPublisherComponents ++
        eventPublisherComponents ++
        missingEventSubscriberComponents ++
        imageSubscriberComponents ++
        missingImagePublisherComponents ++
        imagePublisherComponents ++
        missingImageSubscriberComponents ++
        receivierComponents ++
        missingReceivierComponents ++
        senderComponents ++
        missingSenderComponents))
        .map(_.subsystem)
        .distinct
    }

    // Edges for events that the primary components subscribe to.
    // If missing is true, only those with missing publishers, otherwise only those not missing a publisher.
    // If images is true, get subscribed images, otherwise subscribed events.
    def getSubscribedEventEdges(missing: Boolean, images: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (subscribedEvents, publishers) =
          if (missing) getMissingPublisherInfo(info, images) else getSubscriberInfo(info, images)
        // Map of publisher component name to list of published event names that info.component subscribes to
        val subscribedEventMap = subscribedEvents
          .map(e => List(s"${e.subscribeModelInfo.subsystem}.${e.subscribeModelInfo.component}", e.subscribeModelInfo.name))
          .groupMap(_.head)(_.tail.head)
        val missingType =
          if (missing) Some(if (images) MissingType.missingImagePublisher else MissingType.missingEventPublisher) else None
        publishers.map(c =>
          EdgeModel(
            makeComponentPair(c.prefix, info.componentModel.prefix),
            EdgeLabel(subscribedEventMap(c.prefix), if (images) EdgeType.images else EdgeType.events, missingType)
          )
        )
      }
    }

    // Edges for events that the primary components publish
    // If missing is true, only those with no subscribers, otherwise only those with subscribers.
    // If images is true, get subscribed images, otherwise subscribed events.
    def getPublishedEventEdges(missing: Boolean, images: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (publishedEvents, subscribers) =
          if (missing) getMissingSubscriberInfo(info, images) else getPublisherInfo(info, images)
        // Map of subscriber component name to list of subscribed event names that info.component publishes
        val publishedEventMap = publishedEvents
          .flatMap(e => e.subscribers.map(subscribeInfo => List(subscribeInfo.componentModel.prefix, e.model.name)))
          .groupMap(_.head)(_.tail.head)
        val missingType =
          if (missing) Some(if (images) MissingType.noImageSubscribers else MissingType.noEventSubscribers) else None
        subscribers.map(c =>
          EdgeModel(
            makeComponentPair(info.componentModel.prefix, c.prefix),
            EdgeLabel(publishedEventMap(c.prefix), if (images) EdgeType.images else EdgeType.events, missingType)
          )
        )
      }
    }

    // Edges for commands that the primary components sends
    // If missing is true, only those with no receivers, otherwise only those with receivers.
    def getSentCommandEdges(missing: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (sentCommands, receivierComponents) = if (missing) getMissingReceiverInfo(info) else getSentCommandInfo(info)
        // Map receiving component name to list of commands sent from info.component
        val sentCmdMap = sentCommands
          .map(c => List(s"${c.subsystem}.${c.component}", c.name))
          .groupMap(_.head)(_.tail.head)
        val missingType = if (missing) Some(MissingType.missingReceiver) else None
        receivierComponents.map(c =>
          EdgeModel(
            makeComponentPair(info.componentModel.prefix, c.prefix),
            EdgeLabel(sentCmdMap(c.prefix), EdgeType.commands, missingType)
          )
        )
      }
    }

    // Edges for commands that the primary components receive
    def getReceivedCommandEdges(missing: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (receivedCommands, senderComponents) =
          if (missing) getMissingSenderCommandInfo(info) else getReceivedCommandInfo(info)
        // Map sending component name to list of commands received by info.component
        val recvCmdmdMap = receivedCommands
          .flatMap(c => c.senders.map(senderComponent => List(senderComponent.prefix, c.receiveCommandModel.name)))
          .groupMap(_.head)(_.tail.head)
        val missingType = if (missing) Some(MissingType.noSenders) else None
        senderComponents.map(c =>
          EdgeModel(
            makeComponentPair(c.prefix, info.componentModel.prefix),
            EdgeLabel(recvCmdmdMap(c.prefix), EdgeType.commands, missingType)
          )
        )
      }
    }

    // Make the root graph
    val root = DotRootGraph(
      directed = true,
      id = Some("icdviz"),
      attrStmts = List(
        DotAttrStmt(
          Elem.graph,
          List(
            DotAttr("layout", options.layout),
            DotAttr("splines", options.splines.toString),
            DotAttr("overlap", options.overlap),
            DotAttr("ratio", options.ratio)
          )
        ),
        DotAttrStmt(
          Elem.node,
          List(
            DotAttr("fontsize", nodeFontsize)
          )
        ),
        DotAttrStmt(
          Elem.edge,
          List(
            DotAttr("fontsize", edgeFontsize)
          )
        )
      )
    )

    // Make a subgraph for each subsystem
    val subgraphs = if (options.groupSubsystems) {
      val pairs = allSubsystems.map { subsystem =>
        val color = getSubsystemColor(subsystem)
        subsystem -> DotSubGraph(
          root,
          s"cluster_$subsystem",
          attrList = List(
            DotAttr("label", subsystem),
            DotAttr("color", color),
            DotAttr("fontcolor", color),
            DotAttr("fontsize", subsystemFontsize),
            DotAttr("style", "rounded"),
            DotAttr("penwidth", "3"),
            DotAttr("labelloc", "b")
          )
        )
      }
      pairs.toMap
    }
    else Map.empty[String, DotSubGraph]

    // Creates a dot edge
    def edgeTransformer(innerEdge: Graph[String, MyLDiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
      val edge      = innerEdge.outer
      val edgeLabel = edge.model.label
      val showLabel = options.eventLabels &&
        (edgeLabel.edgeType == EdgeType.events || edgeLabel.edgeType == EdgeType.images) ||
        options.commandLabels && edgeLabel.edgeType == EdgeType.commands
      val color = edgeLabel.edgeType match {
        case EdgeType.events => eventColor
        case EdgeType.images => imageColor
        case _               => commandColor
      }
      val styleAttr =
        if (edgeLabel.missing.isDefined)
          List(
            DotAttr("color", missingColor),
            DotAttr("fontcolor", missingColor),
            DotAttr("style", "dashed")
          )
        else
          List(
            DotAttr("color", color),
            DotAttr("fontcolor", color)
          )
      val labelAttr = if (showLabel) {
        val label = if (edgeLabel.missing.isDefined) {
          s"${edgeLabel.missing.get}:\\n${edgeLabel.label}"
        }
        else s"${edgeLabel.edgeType}:\\n${edgeLabel.label}"
        if (edgeLabel.label.nonEmpty) List(DotAttr("label", label)) else Nil
      }
      else Nil
      Some(
        root,
        DotEdgeStmt(
          NodeId(edge.source),
          NodeId(edge.target),
          styleAttr ++ labelAttr
        )
      )
    }

    // Creates a dot node
    def nodeTransformer(innerNode: Graph[String, MyLDiEdge]#NodeT): Option[(DotGraph, DotNodeStmt)] = {
      val component = innerNode.outer
      val subsystem = component.split('.').head
      if (options.groupSubsystems && subgraphs.contains(subsystem)) {
        val style = if (primaryComponents.map(_.prefix).contains(component)) "bold" else "dashed"
        val color = if (component.endsWith(".?")) "red" else getSubsystemColor(subsystem)
        Some(
          subgraphs(subsystem),
          DotNodeStmt(
            NodeId(component),
            attrList = List(
              DotAttr("label", componentNameFromPrefix(component)),
              DotAttr("color", color),
              DotAttr("fontcolor", color),
              DotAttr("style", style)
            )
          )
        )
      }
      else None
    }

    // Combine duplicate edges, for example, from the publish section on one component and
    // the subscribe section on another that describe the same cononection.
    def combineEdgeModels(l: List[EdgeModel]): List[EdgeModel] = {
      if (l.isEmpty)
        l
      else {
        val edgeType = l.head.label.edgeType
        val missing  = l.head.label.missing
        l.groupBy(_.components)
          .view
          .mapValues(l => l.flatMap(_.label.labels).distinct)
          .toList
          .map(p => EdgeModel(p._1, EdgeLabel(p._2, edgeType, missing)))
      }
    }

    @scala.annotation.tailrec
    def getImageFileFormat(file: File): FileFormat = {
      file.toString.split("\\.").last.toLowerCase() match {
        case "png" => FileFormat.PNG
        case "svg" => FileFormat.SVG
        case "pdf" => FileFormat.PDF
        case "eps" => FileFormat.EPS
        case _ =>
          val imageFormat = options.imageFormat
          val s =
            if (IcdVizOptions.imageFormats.contains(imageFormat.toUpperCase()))
              imageFormat
            else
              IcdVizOptions.defaultImageFormat
          getImageFileFormat(new File(s"x.$s"))
      }
    }

    def saveImageFile(dot: String, file: File): Unit = {
      val data   = s"@startdot\n$dot\n@enddot"
      val reader = new SourceStringReader(data)
      val option = new FileFormatOption(getImageFileFormat(file))
      val f      = new FileOutputStream(file)
      val desc   = reader.outputImage(f, 0, option)
      f.close()
      Option(desc) match {
        case Some(_) =>
          println(s"Generated image file $file")
          if (options.showPlot)
            viewImageFile(file)
        case None =>
          println(s"Failed to generate image $file")
      }
    }

    def saveImageToStream(dot: String, f: OutputStream, format: FileFormat): Unit = {
      val data   = s"@startdot\n$dot\n@enddot"
      val reader = new SourceStringReader(data)
      val option = new FileFormatOption(format)
      reader.outputImage(f, 0, option)
      f.close()
    }

    def viewImageFile(file: File): Unit = {
      if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop.browse(file.toURI)
      }
    }

    // Combine edges where publish/subscribe or send/receive could cause duplicates (not needed if the other side is missing)
    val eventEdgeModels = combineEdgeModels(
      getSubscribedEventEdges(missing = false, images = false) ++ getPublishedEventEdges(missing = false, images = false)
    )
    val imageEdgeModels = combineEdgeModels(
      getSubscribedEventEdges(missing = false, images = true) ++ getPublishedEventEdges(missing = false, images = true)
    )
    val missingEventEdgeModels =
      getSubscribedEventEdges(missing = true, images = false) ++ getPublishedEventEdges(missing = true, images = false)
    val missingImageEdgeModels =
      getSubscribedEventEdges(missing = true, images = true) ++ getPublishedEventEdges(missing = true, images = true)
    val commandEdgeModels        = combineEdgeModels(getSentCommandEdges(missing = false) ++ getReceivedCommandEdges(missing = false))
    val missingCommandEdgeModels = getSentCommandEdges(missing = true) ++ getReceivedCommandEdges(missing = true)

    val allEdgeModels: List[EdgeModel] = (
      eventEdgeModels ++ imageEdgeModels ++ missingEventEdgeModels ++ missingImageEdgeModels ++ commandEdgeModels ++ missingCommandEdgeModels
    ).distinct

//    // Note: Since scala-graph 2.0, it seems that the component pairs need to be unique, so call combineEdgeModels
//    // to merge image and event edges
//    val allEdgeModels = (
//      combineEdgeModels(eventEdgeModels ++ imageEdgeModels)
//        ++ combineEdgeModels(missingEventEdgeModels ++ missingImageEdgeModels)
//        ++ commandEdgeModels
//        ++ missingCommandEdgeModels
//    ).distinct

    // Create the final graph
    val g = MyGraph.from(
      allEdgeModels.map(MyLDiEdge)
    )

    // Convert to dot
    val dot = g.toDot(
      dotRoot = root,
      edgeTransformer = edgeTransformer,
      cNodeTransformer = Some(nodeTransformer)
    )

    // Save dot to file if requested
    options.dotFile.foreach(file => Files.write(file.toPath, dot.getBytes(StandardCharsets.UTF_8)))
    options.imageFile match {
      case Some(file) => saveImageFile(dot, file)
      case None       => if (options.showPlot) saveImageFile(dot, new File("icd-viz.png"))
    }
    maybeOut.foreach { out =>
      val fileFormat = getImageFileFormat(new File(s"x.x"))
      saveImageToStream(dot, out, fileFormat)
    }
  }
}
