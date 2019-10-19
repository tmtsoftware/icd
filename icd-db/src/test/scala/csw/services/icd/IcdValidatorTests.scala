package csw.services.icd

import java.io.File

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.icd.IcdValidator._
import icd.web.shared.IcdModels
import org.scalatest.FunSuite

/**
 * Tests ICD schema validation
 */
//noinspection TypeAnnotation
class IcdValidatorTests extends FunSuite {

  val testDir = {
    val d1 = new File("src/test/resources")
    val d2 = new File("icd-db/src/test/resources")
    if (d1.exists) d1 else d2
  }

  def getConfig(name: String): Config = {
    ConfigFactory.parseResources(name).resolve(ConfigResolveOptions.noSystem())
  }

  def printResult(result: List[Problem]): Unit = {
    for (problem <- result) {
      println(problem.message + "\n")
    }
  }

  def checkResult(result: List[Problem]): Unit = {
    if (result.nonEmpty) {
      printResult(result)
      fail("Validation failed")
    }
  }

  def runTest(good: Config, bad: Config, schema: Config): Unit = {
    checkResult(validateConfig(good, schema, good.origin().filename()))
    val problems = validateConfig(bad, schema, bad.origin().filename())
    if (problems.isEmpty) {
      fail(s"Test failed to find the problems in ${bad.origin().filename()}")
    }
  }

  def runTest(good: String, bad: String, schema: String): Unit = {
    runTest(getConfig(good), getConfig(bad), getConfig(s"${IcdValidator.currentSchemaVersion}/$schema"))
  }

  // ---

  test("Test ICD validation") {
    runTest("subsystem-model.conf", "subsystem-model-bad1.conf", "subsystem-schema.conf")
    runTest("publish-model.conf", "publish-model-bad1.conf", "publish-schema.conf")
    runTest("subscribe-model.conf", "subscribe-model-bad1.conf", "subscribe-schema.conf")
    runTest("command-model.conf", "command-model-bad1.conf", "command-schema.conf")
    runTest("component-model.conf", "component-model-bad1.conf", "component-schema.conf")
  }

  test("Test validation of directory containing standard file names") {
    checkResult(validateOneDir(testDir))
  }

}
