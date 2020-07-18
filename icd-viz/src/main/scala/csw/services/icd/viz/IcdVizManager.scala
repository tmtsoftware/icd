package csw.services.icd.viz

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdDb}
import csw.services.icd.viz.IcdVizManager.EdgeType.EdgeType
import csw.services.icd.viz.IcdVizManager.MissingType.MissingType
import icd.web.shared.{
  ComponentInfo,
  DetailedSubscribeInfo,
  EventInfo,
  PdfOptions,
  ReceivedCommandInfo,
  SentCommandInfo,
  SubscribeInfo,
  SubsystemWithVersion
}
import scalax.collection.Graph
import scalax.collection.io.dot._
import scalax.collection.io.dot.implicits._

import language.implicitConversions
import scalax.collection.edge.LkDiEdge
import scalax.collection.edge.Implicits._
import icd.web.shared.IcdModels.{ComponentModel, SubscribeModelInfo}
import scalax.collection.config.CoreConfig

object IcdVizManager {
  //noinspection TypeAnnotation
  object MissingType extends Enumeration {
    type MissingType = Value
    val noSubscribers    = Value("No Subscribers")
    val missingPublisher = Value("Missing Publisher")
    val noSenders        = Value("No Senders")
    val missingReceiver  = Value("Missing Receiver")
  }

  //noinspection TypeAnnotation
  // Event and Command edges have different colors
  object EdgeType extends Enumeration {
    type EdgeType = Value
    val events   = Value("Events")
    val commands = Value("Commands")
  }

  // Type of an edge label
  case class EdgeLabel(labels: List[String], edgeType: EdgeType, missing: Option[MissingType] = None) {
    lazy val label: String = labels.sorted.mkString("\\n")
  }

  // Holds both components of an edge
  case class ComponentPair(from: String, to: String)

  // Describes an Edge from one component to another
  case class EdgeModel(components: ComponentPair, label: EdgeLabel)

  implicit val myConfig: CoreConfig = CoreConfig()

  // --- plotting defaults ---

  // XXX TODO: Allow configuration
  private def getSubsystemColor(subsystem: String): String = {
    subsystem match {
      case "NFIRAOS" => "green4"
      case "AOESW"   => "springgreen"
      case "TCS"     => "purple"
      case "IRIS"    => "blue"
      case "?"       => "red"
      case _         => "grey"
    }
  }

  private val commandColor = "chocolate" // command color
  private val eventColor   = "dimgrey"   // event colors
  private val missingColor = "red"       // missing command or event color

//  private val possibleLayouts   = List("dot", "fdp", "sfdp", "twopi", "neato", "circo", "patchwork")
  private val nodeFontsize      = 20
  private val edgeFontsize      = 10
  private val subsystemFontsize = 30
//  private val possibleTypes     = List("HCD", "Assembly", "Sequencer", "Application")

  /**
   * Returns a string in GraphViz/dot format showing the relationships between
   * the selected subsystems/components according to the given options.
   * @param db the icd database
   * @param options the options
   * @return a string in dot format that can be displayed as a graph
   */
  def showRelationships(db: IcdDb, options: IcdVizOptions): String = {
    val query          = new CachedIcdDbQuery(db.db, db.admin, None, None)
    val versionManager = new CachedIcdVersionManager(query)

    // Add components from user-specified subsystems
    val subsystemComponents = options.subsystems.flatMap(
      sv =>
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

    val componentInfoHelper = new ComponentInfoHelper(displayWarnings = false)
    val noMarkdownOpt       = Some(PdfOptions(processMarkdown = false))
    val componentInfoList   = components.flatMap(sv => componentInfoHelper.getComponentInfo(versionManager, sv, noMarkdownOpt))

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
    def getSubscriberInfo(info: ComponentInfo): (List[DetailedSubscribeInfo], List[ComponentModel]) = {
      val subscribes = info.subscribes.toList
        .flatMap(_.subscribeInfo)
        .filter(d => d.eventModel.isDefined && d.publisher.isDefined && omitFilter(d.publisher.get))
      (
        subscribes,
        subscribes
          .map(_.publisher.get)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Get info about subscribed events where the publisher doesn't publish the event
    def getMissingPublisherInfo(info: ComponentInfo): (List[DetailedSubscribeInfo], List[ComponentModel]) = {
      if (options.missingEvents) {
        val subscribes = info.subscribes.toList
          .flatMap(_.subscribeInfo)
          .filter(d => d.eventModel.isEmpty)
        (
          subscribes,
          subscribes
            .map(
              d =>
                db.query
                  .getComponentModel(d.subscribeModelInfo.subsystem, d.subscribeModelInfo.component, noMarkdownOpt)
                  .getOrElse(
                    ComponentModel(
                      "?",
                      d.subscribeModelInfo.subsystem,
                      d.subscribeModelInfo.component,
                      d.subscribeModelInfo.component,
                      d.subscribeModelInfo.component,
                      "2.0",
                      ""
                    )
                  )
            )
            .distinct
            .filter(omitFilter)
            .filter(_.prefix != info.componentModel.prefix)
        )
      } else (Nil, Nil)
    }

    // Gets info about published events and the components involved
    def getPublisherInfo(info: ComponentInfo): (List[EventInfo], List[ComponentModel]) = {
      val eventInfoList =
        info.publishes.toList
          .flatMap(p => p.currentStateList ++ p.eventList ++ p.observeEventList)
          .filter(_.subscribers.nonEmpty)
          .map(e => EventInfo(e.eventModel, e.subscribers.filter(s => omitFilter(s.componentModel))))
      (
        eventInfoList,
        eventInfoList
          .flatMap(_.subscribers)
          .map(_.componentModel)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets info about published events with no subscribers
    def getMissingSubscriberInfo(info: ComponentInfo): (List[EventInfo], List[ComponentModel]) = {
      if (options.missingEvents) {
        val missingComponentModel = ComponentModel("?", info.componentModel.subsystem, "?", "?", "?", "?", "")
        val eventInfoList =
          info.publishes.toList
            .flatMap(p => p.currentStateList ++ p.eventList ++ p.observeEventList)
            .filter(_.subscribers.isEmpty)
            .map(
              e =>
                // Create dummy subscriber component
                EventInfo(
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
          eventInfoList,
          eventInfoList.map(_ => missingComponentModel)
        )
      } else (Nil, Nil)
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
            .map(
              s =>
                db.query
                  .getComponentModel(s.subsystem, s.component, noMarkdownOpt)
                  .getOrElse(
                    ComponentModel(
                      "?",
                      s.subsystem,
                      s.component,
                      s.component,
                      s.component,
                      "2.0",
                      ""
                    )
                  )
            )
            .distinct
            .filter(omitFilter)
            .filter(_.prefix != info.componentModel.prefix)
        )
      } else (Nil, Nil)
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
      } else (Nil, Nil)
    }

    // Primary components are the ones specified in the options
    val primaryComponents = componentInfoList.map(_.componentModel).filter(omitFilter)

    // All subsystems related to the primary components
    val allSubsystems = componentInfoList.flatMap { info =>
      val (_, subscriberComponents)       = getSubscriberInfo(info)
      val (_, missingPublisherComponents) = getMissingPublisherInfo(info)

      val (_, publisherComponents)         = getPublisherInfo(info)
      val (_, missingSubscriberComponents) = getMissingSubscriberInfo(info)

      val (_, receivierComponents)        = getSentCommandInfo(info)
      val (_, missingReceivierComponents) = getMissingReceiverInfo(info)

      val (_, senderComponents)        = getReceivedCommandInfo(info)
      val (_, missingSenderComponents) = getMissingSenderCommandInfo(info)

      (info.componentModel :: (subscriberComponents ++
        missingPublisherComponents ++
        publisherComponents ++
        missingSubscriberComponents ++
        receivierComponents ++
        missingReceivierComponents ++
        senderComponents ++
        missingSenderComponents))
        .map(_.subsystem)
        .distinct
    }

    // Edges for events that the primary components subscribe to.
    // If missing is true, only those with missing publishers, otherwise only those not missing a publisher.
    def getSubscribedEventEdges(missing: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (subscribedEvents, publishers) = if (missing) getMissingPublisherInfo(info) else getSubscriberInfo(info)
        // Map of publisher component name to list of published event names that info.component subscribes to
        val subscribedEventMap = subscribedEvents
          .map(e => List(s"${e.subscribeModelInfo.subsystem}.${e.subscribeModelInfo.component}", e.subscribeModelInfo.name))
          .groupMap(_.head)(_.tail.head)
        val missingType = if (missing) Some(MissingType.missingPublisher) else None
        publishers.map(
          c =>
            EdgeModel(
              ComponentPair(c.prefix, info.componentModel.prefix),
              EdgeLabel(subscribedEventMap(c.prefix), EdgeType.events, missingType)
            )
        )
      }
    }

    // Edges for events that the primary components publish
    // If missing is true, only those with no subscribers, otherwise only those with subscribers.
    def getPublishedEventEdges(missing: Boolean): List[EdgeModel] = {
      componentInfoList.flatMap { info =>
        val (publishedEvents, subscribers) = if (missing) getMissingSubscriberInfo(info) else getPublisherInfo(info)
        // Map of subscriber component name to list of subscribed event names that info.component publishes
        val publishedEventMap = publishedEvents
          .flatMap(e => e.subscribers.map(subscribeInfo => List(subscribeInfo.componentModel.prefix, e.eventModel.name)))
          .groupMap(_.head)(_.tail.head)
        val missingType = if (missing) Some(MissingType.noSubscribers) else None
        subscribers.map(
          c =>
            EdgeModel(
              ComponentPair(info.componentModel.prefix, c.prefix),
              EdgeLabel(publishedEventMap(c.prefix), EdgeType.events, missingType)
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
        receivierComponents.map(
          c =>
            EdgeModel(
              ComponentPair(info.componentModel.prefix, c.prefix),
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
        senderComponents.map(
          c =>
            EdgeModel(
              ComponentPair(c.prefix, info.componentModel.prefix),
              EdgeLabel(recvCmdmdMap(c.prefix), EdgeType.commands, missingType)
            )
        )
      }
    }

    // Make the root graph
    val root = DotRootGraph(
      directed = true,
      id = None,
      attrStmts = List(
        DotAttrStmt(
          Elem.graph,
          List(
            DotAttr("layout", "dot"),
            DotAttr("splines", options.splines.toString.capitalize),
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
    } else Map.empty[String, DotSubGraph]

    // Creates a dot edge
    def edgeTransformer(innerEdge: Graph[String, LkDiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
      val edge      = innerEdge.edge
      val edgeLabel = edge.label.asInstanceOf[EdgeLabel]
      val showLabel = options.eventLabels && edgeLabel.edgeType == EdgeType.events ||
        options.commandLabels && edgeLabel.edgeType == EdgeType.commands
      val color = if (edgeLabel.edgeType == EdgeType.events) eventColor else commandColor
      val styleAttr =
        if (edgeLabel.missing.isDefined)
          List(
            DotAttr("color", missingColor),
            DotAttr(Id("fontcolor"), missingColor),
            DotAttr(Id("style"), "dashed")
          )
        else
          List(
            DotAttr("color", color),
            DotAttr(Id("fontcolor"), color)
          )
      val labelAttr = if (showLabel) {
        val label = if (edgeLabel.missing.isDefined) {
          s"${edgeLabel.missing.get}:\\n${edgeLabel.label}"
        } else s"${edgeLabel.edgeType}:\\n${edgeLabel.label}"
        if (edgeLabel.label.nonEmpty) List(DotAttr(Id("label"), Id(label))) else Nil
      } else Nil
      Some(
        root,
        DotEdgeStmt(
          NodeId(edge.from.toString),
          NodeId(edge.to.toString),
          styleAttr ++ labelAttr
        )
      )
    }

    // Creates a dot node
    def nodeTransformer(innerNode: Graph[String, LkDiEdge]#NodeT): Option[(DotGraph, DotNodeStmt)] = {
      val component = innerNode.value
      val subsystem = component.split('.').head
      if (options.groupSubsystems && subgraphs.contains(subsystem)) {
        val style = if (primaryComponents.map(_.prefix).contains(component)) "bold" else "dashed"
        val color = if (component.endsWith(".?")) "red" else getSubsystemColor(subsystem)
        Some(
          subgraphs(subsystem),
          DotNodeStmt(
            NodeId(component),
            attrList = List(
              DotAttr(Id("label"), Id(componentNameFromPrefix(component))),
              DotAttr(Id("color"), color),
              DotAttr(Id("fontcolor"), color),
              DotAttr(Id("style"), style)
            )
          )
        )
      } else None
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

    // Combine edges where publish/subscribe or send/receive could cause duplicates (not needed if the other side is missing)
    val eventEdgeModels          = combineEdgeModels(getSubscribedEventEdges(missing = false) ++ getPublishedEventEdges(missing = false))
    val missingEventEdgeModels   = getSubscribedEventEdges(missing = true) ++ getPublishedEventEdges(missing = true)
    val commandEdgeModels        = combineEdgeModels(getSentCommandEdges(missing = false) ++ getReceivedCommandEdges(missing = false))
    val missingCommandEdgeModels = getSentCommandEdges(missing = true) ++ getReceivedCommandEdges(missing = true)
    val allEdgeModels            = eventEdgeModels ++ missingEventEdgeModels ++ commandEdgeModels ++ missingCommandEdgeModels

    // Create the final graph
    val g = Graph.from(
      allEdgeModels.map(e => (e.components.from ~+#> e.components.to)(e.label))
    )

    // Convert to dot
    val dot = g.toDot(
      dotRoot = root,
      edgeTransformer = edgeTransformer,
      cNodeTransformer = Some(nodeTransformer)
    )

    // XXX
    println(dot)

    // Save dot to file if requested
    options.dotFile.foreach(file => Files.write(file.toPath, dot.getBytes(StandardCharsets.UTF_8)))
    dot
  }

}
