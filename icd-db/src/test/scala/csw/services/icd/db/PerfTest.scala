package csw.services.icd.db

import icd.web.shared.SubsystemWithVersion
import org.scalatest.Ignore

// For performance test on already existing DB
@Ignore
object PerfTest extends App {
  val db             = IcdDb("icds2")
  val compName       = "Corrections"
  val query          = IcdDbQuery(db.db, db.admin, Some(List("TEST")))
  val versionManager = IcdVersionManager(query)

  new ComponentInfoHelper(displayWarnings = false)
    .getComponentInfo(versionManager, SubsystemWithVersion("TEST", None, Some(compName)))
    .foreach { info =>
      assert(info.componentModel.component == compName)
      assert(info.publishes.nonEmpty)
      assert(info.publishes.get.eventList.nonEmpty)
      info.publishes.get.eventList.foreach { pubInfo =>
        println(s"lgsWfs publishes ${pubInfo.eventModel.name}")
        pubInfo.subscribers.foreach { subInfo =>
          println(
            s"${subInfo.subscribeModelInfo.component} from ${subInfo.subscribeModelInfo.subsystem} subscribes to ${subInfo.subscribeModelInfo.name}"
          )
        }
      }
      for {
        subscribes    <- info.subscribes
        subscribeInfo <- subscribes.subscribeInfo
      } {
        println(
          s"$compName subscribes to ${subscribeInfo.subscribeModelInfo.name} from ${subscribeInfo.subscribeModelInfo.subsystem}"
        )
      }
    }
  System.exit(0)
}
