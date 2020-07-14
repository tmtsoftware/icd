package csw.services.icd.viz

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdComponentInfo, IcdDb}
import icd.web.shared.{ComponentInfo, DetailedSubscribeInfo, EventInfo, SentCommandInfo, SubsystemWithVersion}
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
    val componentInfoList   = components.flatMap(sv => componentInfoHelper.getComponentInfo(versionManager, sv, None))

    def omitFilter(componentModel: ComponentModel): Boolean = {
      !options.omitTypes.contains(componentModel.componentType)
    }

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

    val primaryComponents = componentInfoList.map(_.componentModel).filter(omitFilter)

    val allSubsystems = componentInfoList.flatMap { info =>
      // XXX TOFO FIXME: Reuse
      val (_, subscriberComponents) = getSubscriberInfo(info)
      val (_, publisherComponents)  = getPublisherInfo(info)
      val (_, receivierComponents)  = getReceiverInfo(info)
      (info.componentModel :: (subscriberComponents ++ publisherComponents ++ receivierComponents))
        .map(_.subsystem)
        .distinct
    }

    val subscribedEventEdges = componentInfoList.flatMap { info =>
      val (subscribedEvents, publishers) = getSubscriberInfo(info)
      // Map of publisher component name to list of published event names that info.component subscribes to
      var subscribedEventMap = Map[String, List[String]]()
      subscribedEvents.foreach { e =>
        val publisher = e.publisher.get.prefix
        val event     = e.subscribeModelInfo.name
        if (subscribedEventMap.contains(publisher))
          subscribedEventMap = subscribedEventMap + (publisher -> (event :: subscribedEventMap(publisher)))
        else
          subscribedEventMap = subscribedEventMap + (publisher -> List(event))
      }
      publishers.map(c => (c.prefix ~+> info.componentModel.prefix)(subscribedEventMap(c.prefix).mkString("\\n")))
    }

    val publishedEventEdges = componentInfoList.flatMap { info =>
      val (publishedEvents, subscribers) = getPublisherInfo(info)
      // Map of subscriber component name to list of subscribed event names that info.component publishes
      var publishedEventMap = Map[String, List[String]]()
      publishedEvents.foreach { e =>
        val event = e.eventModel.name
        e.subscribers.foreach { subscribeInfo =>
          val subscriber = subscribeInfo.componentModel.prefix
          if (publishedEventMap.contains(subscriber))
            publishedEventMap = publishedEventMap + (subscriber -> (event :: publishedEventMap(subscriber)))
          else
            publishedEventMap = publishedEventMap + (subscriber -> List(event))
        }
      }
      subscribers.map(c => (info.componentModel.prefix ~+> c.prefix)(publishedEventMap(c.prefix).mkString("\\n")))
    }

    val commandEdges = componentInfoList.flatMap { info =>
      val (sentCommands, receivierComponents) = getReceiverInfo(info)
      var cmdMap                              = Map[String, List[String]]()
      sentCommands.foreach { c =>
        val receiver = c.receiver.get.prefix
        val command  = c.name
        if (cmdMap.contains(receiver))
          cmdMap = cmdMap + (receiver -> (command :: cmdMap(receiver)))
        else
          cmdMap = cmdMap + (receiver -> List(command))
      }
      receivierComponents.map(c => (info.componentModel.prefix ~+> c.prefix)(cmdMap(c.prefix).mkString("\\n")))
    }

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

    val g = Graph.from(subscribedEventEdges ++ publishedEventEdges ++ commandEdges)

    val dot = g.toDot(
      dotRoot = root,
      edgeTransformer = edgeTransformer,
      cNodeTransformer = Some(nodeTransformer)
    )
    // XXX
    println(dot)
    options.dotFile.foreach(file => Files.write(file.toPath, dot.getBytes(StandardCharsets.UTF_8)))
    dot
  }

}