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
    val problems = db.ingest(getTestDir("../examples/NFIRAOS"))
    for (p ← problems) println(p)

    val problems2 = db.ingest(getTestDir("../examples/TCS"))
    for (p ← problems2) println(p)

    val info = ComponentInfo(db, "NFIRAOS", None, "envCtrl")
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
}
