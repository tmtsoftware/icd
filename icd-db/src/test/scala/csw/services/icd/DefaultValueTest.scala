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
  test("Ingest TEST example subsystem into database, then query the DB") {
    val dbName = "test"
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    val testHelper = new TestHelper(db)
    val dir = TestHelper.getTestDir("icd-db/src/test/resources/bad-test")
    val problems = db.ingestAndCleanup(dir)
    val s        = problems.map(_.toString).mkString("\n")
//    println(s)
    val expected =
      """error: 'In parameter pipeline, defaultValue badValue is invalid (Should be one of: readout-imager, readout-ifs, online-imager, online-ifs, full-imager, full-ifs, all)' in command 'TEST.testComp.STOP''
        |error: 'In parameter rawPosition, defaultValue -1 is invalid: Value -1 is out of declared range for type integer (0 ≤ x)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter switch, defaultValue -1.324 is invalid (Should be an integer value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter abortInProgress, defaultValue no is invalid (Should be one of: true, false)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter doubleParam2, defaultValue true is invalid (Should be a double value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter stringParam3, defaultValue abc is invalid (min length is 5)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter stringParam4, defaultValue abc is invalid (max length is 2)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter arrayParam2, defaultValue [-1, 0, 0, 0] is invalid: Value -1 is out of declared range for type array[4] of integer (0 ≤ x ≤ 100000)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter arrayParam4, defaultValue [[1, 0], [11, 0]] is invalid: Value 11 is out of declared range for type array[2,2] of integer (0 ≤ x ≤ 10)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter arrayParam5, defaultValue [[1, 0], [3, 0], [1, 2]] is invalid: Wrong top level array dimensions: 3, expected 2' in command 'TEST.testComp.testCommand''
        |error: 'In parameter byteParam2, defaultValue 128 is invalid (Should be a byte value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter shortParam2, defaultValue 99000 is invalid (Should be a short value)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter shortParam3, defaultValue 99 is invalid: Value 99 is out of declared range for type short (0 < x < 99)' in command 'TEST.testComp.testCommand''
        |error: 'In parameter longParam2, defaultValue 10000000000000000000000000000000000000000000000 is invalid (Should be a long value)' in command 'TEST.testComp.testCommand''""".stripMargin
    assert(expected == s)
  }
}
