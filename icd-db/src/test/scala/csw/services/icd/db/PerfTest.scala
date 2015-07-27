package csw.services.icd.db

// For performance test on already existing DB
object PerfTest extends App {
  val db = IcdDb("icds")

  val info = ComponentInfoHelper(db, "NFIRAOS", None, "envCtrl")
  assert(info.compName == "envCtrl")
  assert(info.publishInfo.nonEmpty)
  info.publishInfo.foreach { pubInfo ⇒
    println(s"envCtrl publishes ${pubInfo.name}")
    pubInfo.subscribers.foreach { subInfo ⇒
      println(s"${subInfo.compName} from ${subInfo.subsystem} subscribes to ${subInfo.name}")
    }
  }
  info.subscribeInfo.foreach { subInfo ⇒
    println(s"envCtrl subscribes to ${subInfo.name} from ${subInfo.subsystem}")
  }
}
