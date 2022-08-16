package csw.services.icd.db

import java.io.File
import csw.services.icd.IcdValidator
import icd.web.shared.ComponentInfo.CurrentStates
import icd.web.shared.SubsystemWithVersion
import org.scalatest.funsuite.AnyFunSuite

/**
 * Tests the IcdDb class (Note: Assumes mongod is running)
 */
//@DoNotDiscover
class IcdDbTests extends AnyFunSuite {
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Ingest example ICD into database, then query the DB") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    val testHelper = new TestHelper(db)
    // Need ESW for ObserveEvents
    testHelper.ingestESW()
    // ingest examples/TEST into the DB
    testHelper.ingestDir(getTestDir(s"$examplesDir/TEST"))

    // query the DB
    assert(db.query.getComponentNames(Some("TEST")) == List("env.ctrl", "jsonnet.example", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getAssemblyNames(Some("TEST")) == List("env.ctrl", "jsonnet.example", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getHcdNames(Some("TEST")) == List())
    assert(db.query.getSubsystemNames.toSet == Set("TEST"))

    val components = db.query.getComponents(None).filter(_.subsystem == "TEST")
    assert(components.size == 6)

    // Test getting items based on the component name
    val envCtrl = db.query.getComponentModel("TEST", "env.ctrl", None).get
    assert(envCtrl.component == "env.ctrl")
    assert(envCtrl.componentType == "Assembly")
    assert(envCtrl.prefix == "TEST.env.ctrl")

    val commands = db.query.getCommandModel(envCtrl, None).get
    assert(commands.receive.size == 4)

    assert(commands.receive.head.name == "ENVIRONMENTAL_CONTROL_INITIALIZE")
    assert(commands.receive.head.requirements.head == "INT-TEST-AOESW-0400")

    assert(commands.receive.tail.head.name == "ENVIRONMENTAL_CONTROL_STOP")
    assert(commands.receive.tail.head.requirements.head == "INT-TEST-AOESW-0405")

    val publish   = db.query.getPublishModel(envCtrl, None, Map.empty).get
    val eventList = publish.eventList
    assert(eventList.size == 2)
    val logging = eventList.head
    assert(logging.name == "logToFile")
    assert(logging.archive)

    val currentStateList = publish.currentStateList
    val sensors          = currentStateList.find(_.name == "sensors").get
    assert(sensors.name == "sensors")
    assert(!sensors.archive)
    val attrList = sensors.parameterList

    val temp_ngsWfs = attrList.find(_.name == "temp_ngsWfs").get
    assert(temp_ngsWfs.description == "<p>NGS WFS temperature</p>")
    assert(temp_ngsWfs.typeStr == "float (-inf < x < inf, or NaN)")
    assert(temp_ngsWfs.minimum.contains("-inf"))
    assert(temp_ngsWfs.maximum.contains("inf"))
    assert(temp_ngsWfs.allowNaN)
    assert(temp_ngsWfs.units == "<p>kelvin</p>")

    val temp_lgsWfs = attrList.find(_.name == "temp_lgsWfs").get
    assert(temp_lgsWfs.description == "<p>LGS WFS temperature</p>")
    assert(temp_lgsWfs.typeStr == "float (0 < x < 100, or NaN)")
    assert(temp_lgsWfs.minimum.contains("0"))
    assert(temp_lgsWfs.maximum.contains("100"))
    assert(temp_lgsWfs.allowNaN)
    assert(temp_lgsWfs.units == "<p>kelvin</p>")

    val temp_nacq = attrList.find(_.name == "temp_nacq").get
    assert(temp_nacq.description == "<p>NACQ camera temperature</p>")
    assert(temp_nacq.typeStr == "float")
    assert(temp_nacq.minimum.isEmpty)
    assert(temp_nacq.maximum.isEmpty)
    assert(!temp_nacq.allowNaN)
    assert(temp_nacq.units == "<p>kelvin</p>")

    // Test publish queries
    val published = db.query.getPublished(envCtrl, None, Map.empty).filter(p => p.name == "sensors" && p.publishType == CurrentStates)
    assert(published.size == 1)
    assert(published.head.publishType == CurrentStates)

    // Ingest TEST2 subsystem
    testHelper.ingestDir(getTestDir(s"$examplesDir/TEST2"))

    // Test icd-models
    val icdModels = db.versionManager.getIcdModels(SubsystemWithVersion("TEST"), SubsystemWithVersion("TEST2"), None)
    assert(icdModels.size == 2)
    assert(icdModels.head.subsystem == "TEST")
    assert(icdModels.head.targetSubsystem == "TEST2")
    assert(icdModels.tail.head.subsystem == "TEST2")
    assert(icdModels.tail.head.targetSubsystem == "TEST")
    assert(icdModels.head.description.startsWith("<p>This part contains extra information relevant to the TEST-TEST2 ICD"))
    assert(icdModels.tail.head.description == ("<p>Here is additional information about the ICD from the TEST2 API.</p>"))

    // Test accessing ICD models
    testModels(db)

    db.dropDatabase()
  }

  // XXX TODO: Turn this into a test
  def testModels(db: IcdDb): Unit = {
    val modelsList = db.query.getModels("TEST", None, None, Map.empty)
    val publishInfo = for (models <- modelsList) yield {
      models.publishModel.foreach { publishModel =>
        publishModel.eventList.foreach { eventModel =>
          // println(s"${publishModel.component} publishes event ${eventModel.name}: ${eventModel.description}")
        }
      }
      models.subscribeModel.foreach { subscribeModel =>
        subscribeModel.eventList.foreach { eventModel =>
          // println(s"${subscribeModel.component} subscribes to event ${eventModel.name} from ${eventModel.subsystem}")
        }
      }
    }

    modelsList.foreach { models =>
      models.commandModel.foreach { commandModel =>
        commandModel.receive.foreach { receiveCommandModel =>
          val opt = db.query.getCommand(commandModel.subsystem, commandModel.component, receiveCommandModel.name, None)
          assert(opt.get == receiveCommandModel)
          val senders = db.query
            .getCommandSenders(commandModel.subsystem, commandModel.component, receiveCommandModel.name, None)
            .map(_.component)
        }
      }
    }
  }
}
