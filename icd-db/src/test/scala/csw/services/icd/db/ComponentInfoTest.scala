package csw.services.icd.db

import java.io.File

import csw.services.icd.IcdValidator
import icd.web.shared.{ComponentInfo, SubsystemWithVersion}
import org.scalatest.funsuite.AnyFunSuite

class ComponentInfoTest extends AnyFunSuite {
  private val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  private val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  private def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  private def checkInfo(info: ComponentInfo, clientApi: Boolean): Unit = {
    assert(info.componentModel.component == "lgsWfs")
    assert(info.publishes.nonEmpty)
    val eventList = info.publishes.get.eventList
    assert(eventList.nonEmpty)
//    eventList.foreach { pubInfo =>
//      println(s"lgsWfs publishes event: ${pubInfo.eventModel.name}")
//      pubInfo.subscribers.foreach { subInfo =>
//        println(
//          s"${subInfo.subscribeModelInfo.component} from ${subInfo.subscribeModelInfo.subsystem} subscribes to ${subInfo.subscribeModelInfo.name}"
//        )
//      }
//    }
    assert(eventList.exists(_.eventModel.name == "engMode"))
    assert(eventList.exists(_.eventModel.name == "contRead"))
    assert(eventList.exists(_.eventModel.name == "intTime"))
    assert(eventList.exists(_.eventModel.name == "state"))
    assert(eventList.exists(_.eventModel.name == "heartbeat"))
    assert(info.subscribes.nonEmpty == clientApi)
    if (clientApi) {
      val subscribeInfo = info.subscribes.get.subscribeInfo
      assert(subscribeInfo.nonEmpty)
//      subscribeInfo.foreach { subInfo =>
//        println(s"lgsWfs subscribes to ${subInfo.subscribeModelInfo.name} from ${subInfo.subscribeModelInfo.subsystem}")
//      }
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "zenithAngle" && d.subscribeModelInfo.subsystem == "TEST2"))
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "parallacticAngle" && d.subscribeModelInfo.subsystem == "TEST2"))
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "visWfsPos" && d.subscribeModelInfo.subsystem == "TEST2"))
    }
  }

  test("Get pub/sub info from database") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val query          = IcdDbQuery(db.db, db.admin, None)
    val versionManager = IcdVersionManager(query)

    // ingest examples/TEST into the DB
    val problems = db.ingestAndCleanup(getTestDir(s"$examplesDir/TEST"))
    for (p <- problems) println(p)
    assert(problems.isEmpty)
    db.query.afterIngestFiles(problems, dbName)

    val problems2 = db.ingestAndCleanup(getTestDir(s"$examplesDir/TEST2"))
    for (p <- problems2) println(p)
    assert(problems2.isEmpty)
    db.query.afterIngestFiles(problems2, dbName)

    new ComponentInfoHelper(displayWarnings = false, clientApi = true)
      .getComponentInfo(versionManager, SubsystemWithVersion("TEST", None, Some("lgsWfs")), None)
      .foreach(checkInfo(_, clientApi = true))

    new ComponentInfoHelper(displayWarnings = false, clientApi = false)
      .getComponentInfo(versionManager, SubsystemWithVersion("TEST", None, Some("lgsWfs")), None)
      .foreach(checkInfo(_, clientApi = false))
  }
}
