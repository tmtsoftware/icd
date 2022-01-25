package csw.services.icd.db

import java.io.File

import csw.services.icd.IcdValidator
import icd.web.shared.SubsystemWithVersion
import org.scalatest.funsuite.AnyFunSuite

class ArchivedItemsTest extends AnyFunSuite {
  Resolver.loggingEnabled = false
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Test event size calculations") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val query          = IcdDbQuery(db.db, db.admin, None)
    val versionManager = IcdVersionManager(query)

    // ingest examples/TEST into the DB
    val problems = db.ingestAndCleanup(getTestDir(s"$examplesDir/TEST"))
    for (p <- problems) println(p)
    db.query.afterIngestFiles(problems, dbName)

    new ComponentInfoHelper(displayWarnings = false, clientApi = false, maybeStaticHtml = None)
      .getComponentInfo(versionManager, SubsystemWithVersion("TEST", None, Some("lgsWfs")), None)
      .foreach { info =>
        assert(info.componentModel.component == "lgsWfs")
        assert(info.publishes.nonEmpty)
        assert(info.publishes.get.eventList.nonEmpty)
        info.publishes.get.eventList.foreach { pubInfo =>
          val m = pubInfo.eventModel
          println(
            s"XXX Event ${m.name} size = ${m.totalSizeInBytes}, archive = ${m.archive},  yearly: ${m.totalArchiveSpacePerYear}"
          )
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
            case "engMode3" =>
              assert(m.totalSizeInBytes == 348)
              assert(m.archive)
              assert(m.totalArchiveSpacePerYear == "5.1 GB")
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
            case x =>
          }
        }
      }
  }
}
