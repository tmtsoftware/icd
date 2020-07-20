package csw.services.icd.viz
import org.scalatest.funsuite.AnyFunSuite
import scalax.collection.Graph
import scalax.collection.io.dot._
import scalax.collection.io.dot.implicits._
import scalax.collection.GraphEdge.DiEdge
import language.implicitConversions
import scalax.collection.GraphPredef._
import scalax.collection.edge.LDiEdge,
scalax.collection.edge.Implicits._
import Indent._

// XXX TODO FIXME: Add icd-viz related tests
class IcdVizTests extends AnyFunSuite {
  private val multilineCompatibleSpacing = Spacing(
    indent = TwoSpaces,
    graphAttrSeparator = new AttrSeparator("""
                                             |""".stripMargin) {})

  test("Test API") {
    val g = Graph[Int, DiEdge](1)
    val root = DotRootGraph(
      directed = true,
      id = Some("structs")
    )
    val branchDOT = DotSubGraph(root, "cluster_branch", attrList = List(DotAttr("label", "branch")))
    val cSubGraph = DotSubGraph(branchDOT, "cluster_chained", attrList = List(DotAttr("label", "Chained")))
    val iSubGraph = DotSubGraph(branchDOT, "cluster_unchained", attrList = List(DotAttr("label", "UnChained")))
    val dot = g.toDot(
      dotRoot = root,
      edgeTransformer = _.edge match {
        case _ =>
          None
      },
      iNodeTransformer = Some({ _ =>
        Some((iSubGraph, DotNodeStmt("inode")))
      })
    )
    println(dot)
  }

  test("XXX test") {
    val root = DotRootGraph(
      directed = true,
      id = Some("MyDot"),
      attrStmts = List(DotAttrStmt(Elem.node, List(DotAttr("shape", "record")))),
      attrList = List(DotAttr("attr_1", """"one""""), DotAttr("attr_2", "<two>"))
    )
    val dot = Graph.empty[Int, DiEdge].toDot(root, _ => None)
    println(dot)

  }

  test("Wikipedia example") {
    implicit def toLDiEdge[N](diEdge: DiEdge[N]) = LDiEdge(diEdge._1, diEdge._2)("")
    val g = Graph[String, LDiEdge](
      ("A1" ~+> "A2")("f\\nf1\\nf2\\nf3"),
      ("A2" ~+> "A3")("g"),
      "A1" ~> "B1",
      "A1" ~> "B1",
      ("A2" ~+> "B2")("(g o f)'"),
      "A3" ~> "B3",
      "B1" ~> "B3",
      ("B2" ~+> "B3")("g'")
    )
    val root = DotRootGraph(directed = true, id = Some(Id("Wikipedia_Example")))
    val subA = DotSubGraph(ancestor = root, subgraphId = Id("cluster_A"), attrList = List(DotAttr(Id("rank"), Id("same"))))
    val subB = DotSubGraph(ancestor = root, subgraphId = Id("cluster_B"), attrList = List(DotAttr(Id("rank"), Id("same"))))
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
    def nodeTransformer(innerNode: Graph[String, LDiEdge]#NodeT): Option[(DotGraph, DotNodeStmt)] =
      Some((if (innerNode.value.head == 'A') subA else subB, DotNodeStmt(NodeId(innerNode.toString), Seq.empty[DotAttr])))
    val dot = g.toDot(
      dotRoot = root,
      edgeTransformer = edgeTransformer,
      cNodeTransformer = Some(nodeTransformer),
      spacing = multilineCompatibleSpacing
    )
    println(dot)
  }

}
