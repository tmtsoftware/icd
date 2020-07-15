package csw.services.icd.viz

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdDb}
import icd.web.shared.{
  ComponentInfo,
  DetailedSubscribeInfo,
  EventInfo,
  PdfOptions,
  ReceivedCommandInfo,
  SentCommandInfo,
  SubsystemWithVersion
}
import scalax.collection.Graph
import scalax.collection.io.dot._
import scalax.collection.io.dot.implicits._
import scalax.collection.GraphEdge.DiEdge

import language.implicitConversions
import scalax.collection.GraphEdge._
import scalax.collection.edge.LDiEdge
import scalax.collection.edge.Implicits._
import icd.web.shared.IcdModels.ComponentModel
import scalax.collection.config.CoreConfig

object IcdVizManager {
  implicit def toLDiEdge[N](diEdge: DiEdge[N]): LDiEdge[N] with EdgeCopy[LDiEdge] {
    type L1 = String
  } = LDiEdge(diEdge._1, diEdge._2)("")

  implicit val myConfig: CoreConfig = CoreConfig()

  // --- plotting defaults ---

  // XXX TODO: Allow configuration
  private def getSubsystemColor(subsystem: String): String = {
    subsystem match {
      case "NFIRAOS" => "green4"
      case "AOESW"   => "springgreen"
      case "TCS"     => "purple"
      case "IRIS"    => "blue"
      case _         => "grey"
    }
  }

  private val cmdcol   = "chocolate" // command colour
  private val nocmdcol = "red"       // missing command colour
  private val evcol    = "dimgrey"   // event colours
  private val noevcol  = "red"       // missing event colour

  private val possibleLayouts   = List("dot", "fdp", "sfdp", "twopi", "neato", "circo", "patchwork")
  private val layout            = "dot"
  private val ratio             = 0.5
  private val nodeFontsize      = 20
  private val edgeFontsize      = 10
  private val subsystemFontsize = 30
  private val possibleTypes     = List("HCD", "Assembly", "Sequencer", "Application")

  // suffixes for dummy nodes
  private val suffixNoCmd = ".cmd_no_sender"
  private val suffixNoEv  = ".ev_no_publisher"

  def componentNameFromPrefix(prefix: String): String = {
    val sv = SubsystemWithVersion(prefix)
    // Use full prefix for subsystems that don't have a specific color
    if (getSubsystemColor(sv.subsystem) == "grey")
      prefix
    else
      sv.maybeComponent.getOrElse(prefix)
  }

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
          .map(name => SubsystemWithVersion(sv.subsystem, Some(name), sv.maybeVersion))
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

    // Returns true if the component should not be omitted
    def omitFilter(componentModel: ComponentModel): Boolean = {
      !options.omitTypes.contains(componentModel.componentType)
    }

    // Get info about subscribed events and the components involved
    def getSubscriberInfo(info: ComponentInfo): (List[DetailedSubscribeInfo], List[ComponentModel]) = {
      val subscribes = info.subscribes.toList
        .flatMap(_.subscribeInfo)
        .filter(d => d.publisher.isDefined && omitFilter(d.publisher.get))
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
      val subscribes = info.subscribes.toList
        .flatMap(_.subscribeInfo)
        .filter(d => d.publisher.isEmpty)
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

    // Gets information about commands a component receives and the sender components involved
    def getReceiverInfo(info: ComponentInfo): (List[SentCommandInfo], List[ComponentModel]) = {
      val sentCommands = info.commands.toList.flatMap(_.commandsSent).filter(_.receiver.isDefined)
      (
        sentCommands,
        sentCommands
          .map(_.receiver.get)
          .distinct
          .filter(omitFilter)
          .filter(_.prefix != info.componentModel.prefix)
      )
    }

    // Gets information about commands sent and the components receiving the commands
    def getSenderInfo(info: ComponentInfo): (List[ReceivedCommandInfo], List[ComponentModel]) = {
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

    // Primary components are the ones specified in the options
    val primaryComponents = componentInfoList.map(_.componentModel).filter(omitFilter)

    // All subsystems related to the primary components
    val allSubsystems = componentInfoList.flatMap { info =>
      val (_, subscriberComponents)       = getSubscriberInfo(info)
      val (_, missingPublisherComponents) = getMissingPublisherInfo(info)
      val (_, publisherComponents)        = getPublisherInfo(info)
      val (_, receivierComponents)        = getReceiverInfo(info)
      (info.componentModel :: (subscriberComponents ++ missingPublisherComponents ++ publisherComponents ++ receivierComponents))
        .map(_.subsystem)
        .distinct
    }

    // Edges for events that the primary components subscribe to
    val subscribedEventEdges = componentInfoList.flatMap { info =>
      val (subscribedEvents, publishers) = getSubscriberInfo(info)
      // Map of publisher component name to list of published event names that info.component subscribes to
      val subscribedEventMap = subscribedEvents
        .map(e => List(e.publisher.get.prefix, e.subscribeModelInfo.name))
        .groupMap(_.head)(_.tail.head)
      publishers.map(
        c =>
          (c.prefix ~+> info.componentModel.prefix)(
            if (options.eventLabels) subscribedEventMap(c.prefix).mkString("\\n") else ""
          )
      )
    }

    // Edges for events that the primary components publish
    val publishedEventEdges = componentInfoList.flatMap { info =>
      val (publishedEvents, subscribers) = getPublisherInfo(info)
      // Map of subscriber component name to list of subscribed event names that info.component publishes
      val publishedEventMap = publishedEvents
        .flatMap(e => e.subscribers.map(subscribeInfo => List(subscribeInfo.componentModel.prefix, e.eventModel.name)))
        .groupMap(_.head)(_.tail.head)
      subscribers.map(
        c =>
          (info.componentModel.prefix ~+> c.prefix)(
            if (options.eventLabels) publishedEventMap(c.prefix).mkString("\\n") else ""
          )
      )
    }

    // Edges for commands that the primary components send
    val sentCommandEdges = componentInfoList.flatMap { info =>
      val (sentCommands, receivierComponents) = getReceiverInfo(info)
      // Map receiving component name to list of commands sent from info.component
      val sentCmdMap = sentCommands
        .map(c => List(c.receiver.get.prefix, c.name))
        .groupMap(_.head)(_.tail.head)
      receivierComponents.map(
        c =>
          (info.componentModel.prefix ~+> c.prefix)(
            if (options.commandLabels) sentCmdMap(c.prefix).mkString("\\n") else ""
          )
      )
    }

    // Edges for commands that the primary components receive
    val receivedCommandEdges = componentInfoList.flatMap { info =>
      val (receivedCommands, senderComponents) = getSenderInfo(info)
      // Map sending component name to list of commands received by info.component
      val recvCmdmdMap = receivedCommands
        .flatMap(c => c.senders.map(senderComponent => List(senderComponent.prefix, c.receiveCommandModel.name)))
        .groupMap(_.head)(_.tail.head)
      senderComponents.map(
        c =>
          (info.componentModel.prefix ~+> c.prefix)(
            if (options.commandLabels) recvCmdmdMap(c.prefix).mkString("\\n") else ""
          )
      )
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
    def edgeTransformer(innerEdge: Graph[String, LDiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
      val edge  = innerEdge.edge
      val label = edge.label.asInstanceOf[String]
      Some(
        root,
        DotEdgeStmt(
          NodeId(edge.from.toString),
          NodeId(edge.to.toString),
          if (label.nonEmpty) List(DotAttr(Id("label"), Id(label)))
          else Nil
        )
      )
    }

    // Creates a dot node
    def nodeTransformer(innerNode: Graph[String, LDiEdge]#NodeT): Option[(DotGraph, DotNodeStmt)] = {
      val component = innerNode.value
      val subsystem = component.split('.').head
      if (options.groupSubsystems && subgraphs.contains(subsystem)) {
        val style = if (primaryComponents.map(_.prefix).contains(component)) "bold" else "dashed"
        val color = getSubsystemColor(subsystem)
        Some(
          subgraphs(subsystem),
          DotNodeStmt(
            NodeId(component),
            attrList = List(
              DotAttr(Id("label"), Id(componentNameFromPrefix(component))),
              DotAttr(Id("color"), color),
              DotAttr(Id("fontColor"), color),
              DotAttr(Id("style"), style)
            )
          )
        )
      } else None
    }

    // Create the final graph
    val g = Graph.from(subscribedEventEdges ++ publishedEventEdges ++ sentCommandEdges ++ receivedCommandEdges)

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
