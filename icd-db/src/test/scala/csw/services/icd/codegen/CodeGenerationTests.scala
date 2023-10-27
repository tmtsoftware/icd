package csw.services.icd.codegen

import csw.services.icd.IcdValidator
import csw.services.icd.db.{IcdDb, TestHelper}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.Files
import sys.process.*

class CodeGenerationTests extends AnyFunSuite with BeforeAndAfterAll with BeforeAndAfterEach {
  private val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  private val dbName      = "test"
  private val db          = IcdDb(dbName)
  private val testHelper  = new TestHelper(db)
  private val tempDir     = Files.createTempDirectory("icd").toFile
  private val testDir     = new File(tempDir, "test")
  private val testCodegenDir = {
    val d1 = new File("src/test/codegen")
    val d2 = new File("icd-db/src/test/codegen")
    if (d1.isDirectory) d1 else d2
  }
  private val cswVersion = "5.0.1"

  db.dropDatabase() // start with a clean db for test
  // Need ESW for ObserveEvents
  testHelper.ingestESW()
  // ingest examples/TEST into the DB
  testHelper.ingestDir(TestHelper.getTestDir(s"$examplesDir/TEST"))

  override protected def afterAll(): Unit = {
    if (tempDir.isDirectory) s"rm -rf $tempDir".!
  }

  override protected def afterEach(): Unit = {
    if (testDir.isDirectory) s"rm -rf $testDir".!
  }

  test("Generate Scala code from the example/TEST subsystem and compile and test it") {
    val testFile = new File(tempDir, "Test.scala")
    new ScalaCodeGenerator(db).generate("TEST", None, testFile, None, Some("test"))
    println(s"generated $testFile")
    val result = s"bash $testCodegenDir/testScala.sh $tempDir".!
    assert(result == 0)
  }

  test("Generate Java code from the example/TEST subsystem and compile and test it") {
    val testFile = new File(tempDir, "Test.java")
    new JavaCodeGenerator(db).generate("TEST", None, testFile, None, Some("test"))
    println(s"generated $testFile")
    val result = s"bash $testCodegenDir/testJava.sh $tempDir".!
    assert(result == 0)
  }

  test("Generate Python code from the example/TEST subsystem and test it") {
    val testFile = new File(tempDir, "Test.py")
    new PythonCodeGenerator(db).generate("TEST", None, testFile, None, Some("test"))
    println(s"generated $testFile")
    val result = s"bash $testCodegenDir/testPython.sh $tempDir".!
    assert(result == 0)
  }
}
