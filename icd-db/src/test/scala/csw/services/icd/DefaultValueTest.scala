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
class DefaultValueTest extends AnyFunSuite {
  val dbName      = "test"

  val testDir = {
    val d1 = new File("src/test/resources/bad-test")
    val d2 = new File("icd-db/src/test/resources/bad-test")
    if (d1.exists) d1 else d2
  }

  test("Ingest TEST example subsystem into database, then query the DB") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    val problems = db.ingestAndCleanup(testDir)
    val s = problems.map(_.toString).mkString("\n")
//    println(s)
    assert(problems.length == 10)
    val expected =
      """error: 'In parameter pipeline, defaultValue badValue is invalid (Should be one of: readout-imager, readout-ifs, online-imager, online-ifs, full-imager, full-ifs, all)' in command 'TEST.testComp.STOP''
        |error: 'In parameter switch, defaultValue -1.324 is invalid (Should be an integer value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter abortInProgress, defaultValue no is invalid (Should be one of: true, false)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter doubleParam2, defaultValue true is invalid (Should be a double value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter stringParam3, defaultValue abc is invalid (min length is 5)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter stringParam4, defaultValue abc is invalid (max length is 2)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter byteParam, defaultValue 128 is invalid (Should be a byte value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter byteParam2, defaultValue 20000 is invalid (Should be a byte value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter shortParam2, defaultValue 99000 is invalid (Should be a short value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter longParam2, defaultValue 10000000000000000000000000000000000000000000000 is invalid (Should be a long value)' in command 'TEST.testComp.testCommand''""".stripMargin
    assert(expected == s)
  }
}
