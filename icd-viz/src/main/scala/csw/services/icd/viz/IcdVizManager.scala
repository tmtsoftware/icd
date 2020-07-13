package csw.services.icd.viz

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, IcdComponentInfo, IcdDb}
import guru.nidi.graphviz.attribute.Rank.RankDir
import guru.nidi.graphviz.attribute.{Color, Rank, Style}
import guru.nidi.graphviz.engine.{Format, Graphviz}
import icd.web.shared.{ComponentInfo, SubsystemWithVersion}
import guru.nidi.graphviz.model.Factory._

object IcdVizManager {
  case class ComponentPairInfo(sv1: SubsystemWithVersion, sv2: SubsystemWithVersion, info: ComponentInfo)

  def showRelationships(db: IcdDb, options: IcdVizOptions): Unit = {

    def getComponentPairInfo: List[ComponentPairInfo] = {
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
      // Get the relationships between each unique pair of components
      components.distinct
        .combinations(2)
        .map { case Seq(x, y) => (x, y) }
        .toList
        .flatMap { p =>
          IcdComponentInfo
            .getComponentInfoList(versionManager, p._1, p._2, None)
            .headOption
            .map(ComponentPairInfo(p._1, p._2, _))
        }
    }

    val g = graph("example1").directed.graphAttr
      .`with`(Rank.dir(RankDir.LEFT_TO_RIGHT))
      .`with`(node("a").`with`(Color.RED).link(node("b")), node("b").link(to(node("c")).`with`(Style.DASHED)))
    Graphviz.fromGraph(g).height(100).render(Format.PNG).toFile(new Nothing("example/ex1.png"))

    // ---
    getComponentPairInfo.map { p =>
//      p.

    }
  }

}
