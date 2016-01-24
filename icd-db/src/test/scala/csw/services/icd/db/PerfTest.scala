package csw.services.icd.db

// For performance test on already existing DB
object PerfTest extends App {
  val db = IcdDb("icds")
  val compName = "lgsWfs"
  val info = ComponentInfoHelper.getComponentInfo(new CachedIcdDbQuery(db.db), "NFIRAOS", None, compName)
  assert(info.compName == compName)
  assert(info.publishes.nonEmpty)
  assert(info.publishes.get.telemetryList.nonEmpty)
  info.publishes.get.telemetryList.foreach { pubInfo ⇒
    println(s"lgsWfs publishes ${pubInfo.name}")
    pubInfo.subscribers.foreach { subInfo ⇒
      println(s"${subInfo.compName} from ${subInfo.subsystem} subscribes to ${subInfo.name}")
    }
  }
  assert(info.subscribes.nonEmpty)
  assert(info.subscribes.get.subscribeInfo.nonEmpty)
  info.subscribes.get.subscribeInfo.foreach { subInfo ⇒
    println(s"$compName subscribes to ${subInfo.name} from ${subInfo.subsystem}")
  }
}
