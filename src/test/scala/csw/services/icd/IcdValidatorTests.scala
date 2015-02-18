package csw.services.icd

import java.io.File

import com.typesafe.config.{ Config, ConfigResolveOptions, ConfigFactory }
import csw.services.icd.IcdValidator._
import org.scalatest.FunSuite

/**
 * Tests ICD schema validation
 */
class IcdValidatorTests extends FunSuite {

  val testDir = new File("src/test/resources")

  def getConfig(name: String): Config = {
    ConfigFactory.parseResources(name).resolve(ConfigResolveOptions.noSystem())
  }

  def printResult(result: List[Problem]): Unit = {
    for (problem ← result) {
      println(s"${problem.severity}: ${problem.message}\n")
    }
  }

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      printResult(result)
      fail("Validation failed")
    }
  }

  def runTest(good: Config, bad: Config, schema: Config): Unit = {
    checkResult(validate(good, schema))
    val problems1 = validate(bad, schema)
    assert(problems1.length != 0)
  }

  def runTest(good: String, bad: String, schema: String): Unit = {
    runTest(getConfig(good), getConfig(bad), getConfig(schema))
  }

  // ---

  test("Test ICD validation") {
    runTest("icd-model.conf", "icd-model-bad1.conf", "icd-schema.conf")
    runTest("publish-model.conf", "publish-model-bad1.conf", "publish-schema.conf")
    runTest("subscribe-model.conf", "subscribe-model-bad1.conf", "subscribe-schema.conf")
    runTest("command-model.conf", "command-model-bad1.conf", "command-schema.conf")
    runTest("component-model.conf", "component-model-bad1.conf", "component-schema.conf")
  }

  test("Test validation of directory containing standard file names") {
    checkResult(validate(testDir))
  }

  test("Test the parser") {
    val parser = IcdParser(testDir)
    val icdModel = parser.icdModel.get
    assert(icdModel.modelVersion == "1.1")
    assert(icdModel.name == "WFOS-ESW")
    assert(icdModel.version == 20141121)
    assert(icdModel.wbsId == "TMT.INS.INST.WFOS.SWE")
  }
}
