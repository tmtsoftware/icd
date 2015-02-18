package csw.services.icd

import java.io.File

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
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

    val componentModel = parser.componentModel.get
    assert(componentModel.name == "filter")
    assert(!componentModel.usesTime)
    assert(componentModel.usesEvents)
    assert(componentModel.usesConfigurations)
    assert(!componentModel.usesProperties)
    assert(componentModel.componentType == "Assembly")
    assert(componentModel.description == "This is the metadata description of the WFOS filter Assembly")

    val publishModel = parser.publishModel.get
    val telemetryList = publishModel.telemetryList
    assert(telemetryList.size == 2)

    /*
      telemetry = [
    {
      name = "status1"
      description = "status1 description"
      rate = 0
      maxRate = 100
      archive = Yes
      archiveRate = 10
      attributes = [
        {
          name = a1
          description = "single value with min/max"
          type = integer
          minimum = -2
          maximum = 22
          units = m
        }
        {
          name = a2
          description = "array of float"
          type = array
          items = {
            type = number
          }
          minItems = 5
          maxItems = 5
          units = mm
        }
        {
          name = a3
          description = "enum choice"
          enum: [red, green, blue]
          default = green
        }
      ]
    }

     */
    val status1 = telemetryList.head
    assert(status1.name == "status1")
    assert(status1.description == "status1 description")
    assert(status1.rate == 0)
    assert(status1.maxRate == 100)
    assert(status1.archive)
    assert(status1.archiveRate == 10)

    val attr1 = status1.attributesList
    assert(attr1.size == 3)

    val a1 = attr1(0).config
    assert(a1.getString("name") == "a1")
    assert(a1.getString("description") == "single value with min/max")
    assert(a1.getString("type") == "integer")
    assert(a1.getInt("minimum") == -2)
    assert(a1.getInt("maximum") == 22)
    assert(a1.getString("units") == "m")

    // ... XXX TODO
  }
}
