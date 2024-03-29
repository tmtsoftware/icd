package csw.services.icd.db

import java.io.File

import csw.services.icd.IcdValidator
import icd.web.shared.SubsystemWithVersion
import org.scalatest.funsuite.AnyFunSuite

class ArchivedItemsTest extends AnyFunSuite {
  Resolver.loggingEnabled = false
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName      = "test"

  test("Test event size calculations") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val testHelper = new TestHelper(db)
    // Need ESW for ObserveEvents
    testHelper.ingestESW()
    // ingest examples/TEST into the DB
    testHelper.ingestDir(TestHelper.getTestDir(s"$examplesDir/TEST"))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = false)
      .getComponentInfo(SubsystemWithVersion("TEST", None, Some("lgsWfs")), None, Map.empty)
      .foreach { info =>
        assert(info.componentModel.component == "lgsWfs")
        assert(info.publishes.nonEmpty)
        assert(info.publishes.get.eventList.nonEmpty)
        info.publishes.get.eventList.foreach { pubInfo =>
          val m = pubInfo.eventModel
//          println(
//            s"XXX Event ${m.name} size = ${m.totalSizeInBytes}, archive = ${m.archive},  yearly: ${m.totalArchiveSpacePerYear}"
//          )
          m.name match {
            case "engMode" =>
              assert(m.totalSizeInBytes == 339)
              assert(!m.archive)
              assert(m.totalArchiveSpacePerYear.isEmpty)
            case "engMode2" =>
              assert(m.totalSizeInBytes == 340)
              assert(m.archive)
              assert(m.totalArchiveSpacePerYear == "5.0 GB")
            case "engMode2Error" =>
              assert(m.totalSizeInBytes == 180)
//            case "engMode3" =>
//              assert(m.totalSizeInBytes == 348)
//              assert(m.archive)
//              assert(m.totalArchiveSpacePerYear == "5.1 GB")
            case "contRead" =>
              assert(m.totalSizeInBytes == 229)
              assert(!m.archive)
              assert(m.totalArchiveSpacePerYear.isEmpty)
            case "intTime" =>
              assert(m.totalSizeInBytes == 231)
              assert(m.archive)
              assert(m.totalArchiveSpacePerYear == "3.4 GB")
            case "state" =>
              assert(m.totalSizeInBytes == 233)
              assert(!m.archive)
              assert(m.totalArchiveSpacePerYear.isEmpty)
            case "heartbeat" =>
              assert(m.totalSizeInBytes == 237)
              assert(!m.archive)
              assert(m.totalArchiveSpacePerYear.isEmpty)
            case _ =>
          }
        }
      }
  }
}
