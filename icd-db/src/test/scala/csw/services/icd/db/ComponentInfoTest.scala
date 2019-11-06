package csw.services.icd.db

import java.io.File

import csw.services.icd.IcdValidator
import icd.web.shared.SubsystemWithVersion
import org.scalatest.FunSuite

// XXX TODO: Add more detailed test, add IcdComponentInfo tests
class ComponentInfoTest extends FunSuite {
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Get pub/sub info from database") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val query          = IcdDbQuery(db.db, db.admin, None)
    val versionManager = IcdVersionManager(query)

    // ingest examples/TEST into the DB
    val problems = db.ingest(getTestDir(s"$examplesDir/TEST"))
    for (p <- problems) println(p)
    db.query.afterIngestFiles(problems, dbName)

    val problems2 = db.ingest(getTestDir(s"$examplesDir/TEST2"))
    for (p <- problems2) println(p)
    db.query.afterIngestFiles(problems2, dbName)

    new ComponentInfoHelper(displayWarnings = false)
      .getComponentInfo(versionManager, SubsystemWithVersion("TEST", None, Some("lgsWfs")))
      .foreach { info =>
        assert(info.componentModel.component == "lgsWfs")
        assert(info.publishes.nonEmpty)
        assert(info.publishes.get.eventList.nonEmpty)
        info.publishes.get.eventList.foreach { pubInfo =>
          println(s"envCtrl publishes event: ${pubInfo.eventModel.name}")
          pubInfo.subscribers.foreach { subInfo =>
            println(
              s"${subInfo.subscribeModelInfo.component} from ${subInfo.subscribeModelInfo.subsystem} subscribes to ${subInfo.subscribeModelInfo.name}"
            )
          }
        }
        assert(info.subscribes.nonEmpty)
        assert(info.subscribes.get.subscribeInfo.nonEmpty)
        info.subscribes.get.subscribeInfo.foreach { subInfo =>
          println(s"envCtrl subscribes to ${subInfo.subscribeModelInfo.name} from ${subInfo.subscribeModelInfo.subsystem}")
        }
      }
  }
}
