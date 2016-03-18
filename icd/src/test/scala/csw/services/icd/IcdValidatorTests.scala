package csw.services.icd

import java.io.File

import com.typesafe.config.{Config, ConfigFactory, ConfigResolveOptions}
import csw.services.icd.IcdValidator._
import icd.web.shared.IcdModels
import org.scalatest.FunSuite

/**
 * Tests ICD schema validation
 */
class IcdValidatorTests extends FunSuite {

  val testDir = {
    val d1 = new File("src/test/resources")
    val d2 = new File("icd/src/test/resources")
    if (d1.exists) d1 else d2
  }

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
    checkResult(validate(good, schema, good.origin().filename()))
    val problems = validate(bad, schema, bad.origin().filename())
    if (problems.isEmpty) {
      fail(s"Test failed to find the problems in ${bad.origin().filename()}")
    }
  }

  def runTest(good: String, bad: String, schema: String): Unit = {
    runTest(getConfig(good), getConfig(bad), getConfig(schema))
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
    checkResult(validate(testDir))
  }

  test("Test the parser") {
    val parser = IcdParser(testDir)

    checkSubsystemModel(parser)
    checkComponentModel(parser)
    checkPublishModel(parser)
    checkCommandModel(parser)
  }

  def checkSubsystemModel(models: IcdModels): Unit = {
    val subsystemModel = models.subsystemModel.get
    assert(subsystemModel.modelVersion == "1.1")
    assert(subsystemModel.subsystem == "WFOS")
    assert(subsystemModel.title == "Wide-Field Optical Spectrometer (WFOS)")
    assert(subsystemModel.description.startsWith("<p>The Wide Field"))
  }

  def checkComponentModel(models: IcdModels): Unit = {
    val componentModel = models.componentModel.get
    assert(componentModel.component == "filter")
    assert(componentModel.prefix == "wfos.filter")
    assert(componentModel.subsystem == "WFOS")
    assert(componentModel.title == "WFOS Filter")
    assert(componentModel.componentType == "Assembly")
    assert(componentModel.description == "<p>This is the metadata description of the WFOS filter Assembly</p>")
    assert(componentModel.wbsId == "TMT.INS.INST.WFOS.SWE")
    assert(componentModel.modelVersion == "1.1")
  }

  def checkPublishModel(models: IcdModels): Unit = {
    val publishModel = models.publishModel.get
    val telemetryList = publishModel.telemetryList
    assert(telemetryList.size == 2)

    val status1 = telemetryList.head
    assert(status1.name == "status1")
    assert(status1.description == "<p>status1 description</p>")
    assert(status1.minRate == 0)
    assert(status1.maxRate == 100)
    assert(status1.archive)
    assert(status1.archiveRate == 10)

    val attr1 = status1.attributesList
    assert(attr1.size == 4)

    val a1 = attr1.head
    assert(a1.name == "a1")
    assert(a1.description == "<p>single value with min/max</p>")
    assert(a1.typeOpt.get == "integer")
    assert(a1.minimum.contains("-100"))
    assert(a1.maximum.contains("100"))
    assert(a1.units == "m")

    val a2 = attr1(1)
    assert(a2.name == "a2")
    assert(a2.description == "<p>array of float</p>")
    assert(a2.typeOpt.get == "array")
    assert(a2.minItems.contains("5"))
    assert(a2.maxItems.contains("5"))
    assert(a2.units == "mm")

    val a3 = attr1(2)
    assert(a3.name == "a3")
    assert(a3.description == "<p>enum choice</p>")
    assert(a3.enumOpt.get == List("red", "green", "blue"))
    assert(a3.defaultValue == "green")
    // ... XXX TODO continue
  }

  def checkCommandModel(parser: IcdModels): Unit = {
    val commandModel = parser.commandModel.get
    assert(commandModel.receive.size == 2)
    val item1 = commandModel.receive.head
    assert(item1.name == "cmd1")
    assert(item1.description == "<p>Description of cmd1</p>")
    assert(item1.requirements == List("First requirement for cmd1", "Second requirement for cmd1"))
    assert(item1.requiredArgs == List("a1"))
    // ... XXX TODO continue
  }

  // ... XXX TODO test other models

}
