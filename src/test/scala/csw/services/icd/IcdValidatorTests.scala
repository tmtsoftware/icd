package csw.services.icd

import java.io.{ FileOutputStream, File }

import com.typesafe.config.{ Config, ConfigFactory, ConfigResolveOptions }
import csw.services.icd.IcdValidator._
import csw.services.icd.gfm.IcdToGfm
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

    checkIcdModel(parser)
    checkComponentModel(parser)
    checkPublishModel(parser)
    checkCommandModel(parser)

    saveToGfm(parser)
  }

  // Save a test GFM markdown file
  def saveToGfm(parser: IcdParser): Unit = {
    val f = new FileOutputStream("test.md")
    f.write(IcdToGfm(parser).gfm.getBytes)
    f.close()
  }

  def checkIcdModel(parser: IcdParser): Unit = {
    val icdModel = parser.icdModel.get
    assert(icdModel.modelVersion == "1.1")
    assert(icdModel.name == "WFOS-ESW")
    assert(icdModel.version == 20141121)
    assert(icdModel.wbsId == "TMT.INS.INST.WFOS.SWE")
  }

  def checkComponentModel(parser: IcdParser): Unit = {
    val componentModel = parser.componentModel.get
    assert(componentModel.name == "filter")
    assert(!componentModel.usesTime)
    assert(componentModel.usesEvents)
    assert(componentModel.usesConfigurations)
    assert(!componentModel.usesProperties)
    assert(componentModel.componentType == "Assembly")
    assert(componentModel.description == "This is the metadata description of the WFOS filter Assembly")
  }

  def checkPublishModel(parser: IcdParser): Unit = {
    val publishModel = parser.publishModel.get
    val telemetryList = publishModel.telemetryList
    assert(telemetryList.size == 2)

    val status1 = telemetryList.head
    assert(status1.name == "status1")
    assert(status1.description == "status1 description")
    assert(status1.rate == 0)
    assert(status1.maxRate == 100)
    assert(status1.archive)
    assert(status1.archiveRate == 10)

    val attr1 = status1.attributesList
    assert(attr1.size == 3)

    val a1 = attr1(0)
    assert(a1.name == "a1")
    assert(a1.description == "single value with min/max")
    assert(a1.typeOpt.get == "integer")
    val a1Conf = a1.config
    assert(a1Conf.getInt("minimum") == -2)
    assert(a1Conf.getInt("maximum") == 22)
    assert(a1Conf.getString("units") == "m")

    val a2 = attr1(1)
    assert(a2.name == "a2")
    assert(a2.description == "array of float")
    assert(a2.typeOpt.get == "array")
    val a2Conf = a2.config
    assert(a2Conf.getConfig("items").getString("type") == "number")
    assert(a2Conf.getInt("minItems") == 5)
    assert(a2Conf.getInt("maxItems") == 5)
    assert(a2Conf.getString("units") == "mm")

    val a3 = attr1(2)
    assert(a3.name == "a3")
    assert(a3.description == "enum choice")
    assert(a3.enumOpt.get == List("red", "green", "blue"))
    val a3Conf = a3.config
    assert(a3Conf.getString("default") == "green")
    // ... XXX TODO continue
  }

  def checkCommandModel(parser: IcdParser): Unit = {
    val commandModel = parser.commandModel.get
    assert(commandModel.items.size == 2)
    val item1 = commandModel.items(0)
    assert(item1.name == "cmd1")
    assert(item1.description == "Description of cmd1")
    assert(item1.requirements == List("First requirement for cmd1", "Second requirement for cmd1"))
    assert(item1.requiredArgs == List("a1"))
    // ... XXX TODO continue
  }

  // ... XXX TODO test other models

}
