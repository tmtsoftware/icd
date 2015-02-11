package csw.services.icd

import com.typesafe.config.{Config, ConfigResolveOptions, ConfigFactory}
import csw.services.icd.IcdValidator._
import org.scalatest.FunSuite

/**
 * Tests ICD schema validation
 */
class IcdValidatorTests extends FunSuite {

  def getConfig(name: String): Config = {
    ConfigFactory.parseResources(name).resolve(ConfigResolveOptions.noSystem())
  }

  def printResult(result: List[Problem]): Unit = {
    for (problem ← result) {
      println(s"${problem.severity}: ${problem.message}\n")
      println(problem.json)
    }
  }

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      printResult(result)
      fail("Validation failed")
    }
  }

  def runTest(good: Config, bad: Config, schema: Config):Unit = {
    checkResult(validate(good, schema))
    val problems1 = validate(bad, schema)
    assert(problems1.length != 0)
  }

  def runTest(good: String, bad: String, schema: String): Unit = {
    runTest(getConfig(good), getConfig(bad), getConfig(schema))
  }

  test("Test ICD validation") {
    runTest("icd-good1.conf", "icd-bad1.conf", "icd-schema.conf")
    runTest("publish-good1.conf", "publish-bad1.conf", "publish-schema.conf")
    runTest("command-good1.conf", "command-bad1.conf", "command-schema.conf")
  }
}
