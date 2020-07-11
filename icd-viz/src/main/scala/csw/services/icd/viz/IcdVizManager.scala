package csw.services.icd.viz

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdDb}
import icd.web.shared.IcdModels.ComponentModel
import icd.web.shared.{ComponentInfo, Publishes, ReceivedCommandInfo, SentCommandInfo, Subscribes, SubsystemWithVersion}

object IcdVizManager {
  case class ComponentPair(comp1: ComponentModel, comp2: ComponentModel)
  case class ComponentRelationships(
      pair: ComponentPair,
      publishes: Option[Publishes],
      subscribes: Option[Subscribes],
      commandsReceived: List[ReceivedCommandInfo],
      commandsSent: List[SentCommandInfo]
  )

  def showRelationships(db: IcdDb, options: IcdVizOptions): Unit = {
    var infoMap = Map[ComponentPair, ComponentInfo]

    // Gets all required info about selected component's relationships
    def getComponentInfo: List[ComponentInfo] = {
      val query               = new CachedIcdDbQuery(db.db, db.admin, None, None)
      val versionManager      = new CachedIcdVersionManager(query)
      val componentInfoHelper = new ComponentInfoHelper(displayWarnings = false)
      // Add components from user-specified subsystems
      val subsystemComponents = options.subsystems.flatMap(
        sv =>
          db.versionManager
            .getComponentNames(sv)
            .map(name => SubsystemWithVersion(sv.subsystem, Some(name), sv.maybeVersion))
      )
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
      components.flatMap(sv => componentInfoHelper.getComponentInfo(versionManager, sv, None))
    }

    // ---
    val infoList     = getComponentInfo
    val primaryNodes = infoList.map(_.componentModel)
//    val commandsSent = for {
//      info <- infoList
//      cmds <- info.commands.toList
//      sentCmds <- cmds.commandsSent
//      receiver <- sentCmds.receiver.toList
//    } yield {
//      info.componentModel
////      receiver
//    }
  }

}
