package csw.services.icd

import java.io.File
import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.icd.IcdValidator.*
import csw.services.icd.db.{IcdDb, TestHelper}
import org.scalatest.funsuite.AnyFunSuite

/**
 * Tests ICD schema validation
 */
//noinspection TypeAnnotation
class IngestErrorTest extends AnyFunSuite {
  test("Verify that invalid model files with errors are not ingested (or are restored from backup after ingesting)") {
    val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
    val dbName = "test"
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    // Ingesting error when no previous subsystem exists: Subsystem TEST should not exist afterwards
    val testHelper = new TestHelper(db)
    testHelper.ingestESW()
    val testDir = TestHelper.getTestDir("icd-db/src/test/resources/bad-test")
    val problems1 = db.ingestAndCleanup(testDir)
    assert(problems1.nonEmpty)
    assert(!db.query.getSubsystemNames.contains("TEST"))

    // ingest examples/TEST into the DB (should succeed)
    testHelper.ingestDir(TestHelper.getTestDir(s"$examplesDir/TEST"))

    // Ingesting error when previous TEST subsystem exists: Should not change anything
    val problems1a = db.ingestAndCleanup(testDir)
    assert(problems1a.nonEmpty)
    assert(db.query.getSubsystemNames.contains("TEST"))
    assert(db.query.getComponentNames(Some("TEST")) == List("env.ctrl", "jsonnet.example", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))

    // Test with only error that is found in the first phase of validation
    val testDir2 = TestHelper.getTestDir("icd-db/src/test/resources/bad-test-2")
    val problems2 = db.ingestAndCleanup(testDir2)
    assert(problems2.nonEmpty)
    assert(db.query.getComponentNames(Some("TEST")) == List("env.ctrl", "jsonnet.example", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))

    // Test with errors found in both validation phases
    val testDir3 = TestHelper.getTestDir("icd-db/src/test/resources/bad-test-3")
    val problems3 = db.ingestAndCleanup(testDir3)
    assert(problems3.nonEmpty)
    assert(db.query.getComponentNames(Some("TEST")) == List("env.ctrl", "jsonnet.example", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
  }

  test("Verify that invalid model files with errors are not ingested (when no previous subsystem was there)") {
    val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
    val dbName = "test"
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    // Test with only error that is found in the first phase of validation
    val testDir2 = TestHelper.getTestDir("icd-db/src/test/resources/bad-test-2")
    val problems2 = db.ingestAndCleanup(testDir2)
    assert(problems2.nonEmpty)
    assert(!db.query.getSubsystemNames.contains("TEST"))

    // Test with errors found in both validation phases
    val testDir3 = TestHelper.getTestDir("icd-db/src/test/resources/bad-test-3")
    val problems3 = db.ingestAndCleanup(testDir3)
    assert(problems3.nonEmpty)
    assert(!db.query.getSubsystemNames.contains("TEST"))
  }

}
