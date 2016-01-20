package csw.services.icd.db

// For performance test on already existing DB
object PerfTest extends App {
  val db = IcdDb("icds")

  val info = ComponentInfoHelper.getComponentInfo(new CachedIcdDbQuery(db.db), "NFIRAOS", None, "envCtrl")
  assert(info.compName == "envCtrl")
  assert(info.publishes.nonEmpty)
  assert(info.publishes.get.telemetryList.nonEmpty)
  info.publishes.get.telemetryList.foreach { pubInfo ⇒
    println(s"envCtrl publishes ${pubInfo.name}")
    pubInfo.subscribers.foreach { subInfo ⇒
      println(s"${subInfo.compName} from ${subInfo.subsystem} subscribes to ${subInfo.name}")
    }
  }
  assert(info.subscribes.nonEmpty)
  assert(info.subscribes.get.subscribeInfo.nonEmpty)
  info.subscribes.get.subscribeInfo.foreach { subInfo ⇒
    println(s"envCtrl subscribes to ${subInfo.name} from ${subInfo.subsystem}")
  }
}
