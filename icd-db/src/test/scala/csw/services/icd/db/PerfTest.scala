package csw.services.icd.db

import icd.web.shared.SubsystemWithVersion

// For performance test on already existing DB
object PerfTest extends App {
  // XXX TODO FIXME
  val db       = IcdDb("icds2")
  val compName = "lgsWfs"
  ComponentInfoHelper.getComponentInfo(new CachedIcdDbQuery(db.db), SubsystemWithVersion("NFIRAOS", None, Some(compName))).foreach { info =>
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
    assert(info.subscribes.nonEmpty)
    assert(info.subscribes.get.subscribeInfo.nonEmpty)
    info.subscribes.get.subscribeInfo.foreach { subInfo =>
      println(s"$compName subscribes to ${subInfo.subscribeModelInfo.name} from ${subInfo.subscribeModelInfo.subsystem}")
    }
  }
}
