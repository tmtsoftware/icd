package csw.services.icd.db

import java.io.File

import org.scalatest.FunSuite

// XXX TODO: Add more detailed test, add IcdComponentInfo tests
class ComponentInfoTest extends FunSuite {

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Get pub/sub info from database") {
    val db = IcdDb("test")
    db.dropDatabase() // start with a clean db for test

    // ingest examples/NFIRAOS into the DB
    val problems = db.ingest(getTestDir("examples/NFIRAOS"))
    for (p ← problems) println(p)

    val problems2 = db.ingest(getTestDir("examples/TCS"))
    for (p ← problems2) println(p)

    ComponentInfoHelper.getComponentInfo(db.query, "NFIRAOS", None, "lgsWfs").foreach { info ⇒
      assert(info.componentModel.component == "lgsWfs")
      assert(info.publishes.nonEmpty)
      assert(info.publishes.get.telemetryList.nonEmpty)
      info.publishes.get.telemetryList.foreach { pubInfo ⇒
        println(s"envCtrl publishes telemetry: ${pubInfo.telemetryModel.name}")
        pubInfo.subscribers.foreach { subInfo ⇒
          println(s"${subInfo.subscribeModelInfo.component} from ${subInfo.subscribeModelInfo.subsystem} subscribes to ${subInfo.subscribeModelInfo.name}")
        }
      }
      assert(info.subscribes.nonEmpty)
      assert(info.subscribes.get.subscribeInfo.nonEmpty)
      info.subscribes.get.subscribeInfo.foreach { subInfo ⇒
        println(s"envCtrl subscribes to ${subInfo.subscribeModelInfo.name} from ${subInfo.subscribeModelInfo.subsystem}")
      }
    }
  }
}
