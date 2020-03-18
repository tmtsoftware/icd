package csw.services.icd.db

import java.io.File

import csw.services.icd.IcdValidator
import icd.web.shared.ComponentInfo.CurrentStates
import org.scalatest.funsuite.AnyFunSuite

/**
 * Tests the IcdDb class (Note: Assumes mongod is running)
 */
//@DoNotDiscover
class IcdDbTests extends AnyFunSuite {
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Ingest example ICD into database, then query the DB") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    // ingest examples/TEST into the DB
    val problems = db.ingestAndCleanup(getTestDir(s"$examplesDir/TEST"))
    for (p <- problems) println(p)
    assert(problems.isEmpty)
    db.query.afterIngestFiles(problems, dbName)

    // query the DB
    assert(db.query.getComponentNames == List("env.ctrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getComponentNames("TEST") == List("env.ctrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getAssemblyNames == List("env.ctrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getHcdNames == List())
    assert(db.query.getSubsystemNames == List("TEST"))

    val components = db.query.getComponents
    assert(components.size == 5)

    // Test getting items based on the component name
    val envCtrl = db.query.getComponentModel("TEST", "env.ctrl").get
    assert(envCtrl.component == "env.ctrl")
    assert(envCtrl.componentType == "Assembly")
    assert(envCtrl.prefix == "TEST.env.ctrl")

    val commands = db.query.getCommandModel(envCtrl).get
    assert(commands.receive.size == 2)

    assert(commands.receive.head.name == "ENVIRONMENTAL_CONTROL_INITIALIZE")
    assert(commands.receive.head.requirements.head == "INT-TEST-AOESW-0400")

    assert(commands.receive.last.name == "ENVIRONMENTAL_CONTROL_STOP")
    assert(commands.receive.last.requirements.head == "INT-TEST-AOESW-0405")

    val publish   = db.query.getPublishModel(envCtrl).get
    val eventList = publish.eventList
    assert(eventList.size == 3)
    val logging = eventList.head
    assert(logging.name == "logToFile")
    assert(!logging.archive)

    val currentStateList = publish.currentStateList
    val sensors = currentStateList.find(_.name == "sensors").get
    assert(sensors.name == "sensors")
    assert(!sensors.archive)
    val attrList = sensors.attributesList

    val temp_ngsWfs = attrList.head
    assert(temp_ngsWfs.name == "temp_ngsWfs")
    assert(temp_ngsWfs.description == "<p>NGS WFS temperature</p>")
    assert(temp_ngsWfs.typeStr == "float")
    assert(temp_ngsWfs.units == "<p>degC</p>")

    // Test publish queries
    val published = db.query.getPublished(envCtrl).filter(p => p.name == "sensors" && p.publishType == CurrentStates)
    assert(published.size == 1)
    assert(published.head.publishType == CurrentStates)

    //    val sensorList = db.query.publishes("test.ncc.env.ctrl.sensors", "TEST", Events)
    //    assert(sensorList.size == 1)
    //    assert(sensorList.head.componentName == "env.ctrl")
    //    assert(sensorList.head.item.publishType == Events)
    //    assert(sensorList.head.prefix == "test.ncc.env.ctrl")
    //    assert(sensorList.head.item.name == "sensors")

    // Test accessing ICD models
    testModels(db)

    //    // Test saving document from the database
    //    IcdDbPrinter(db).saveToFile(envCtrl.subsystem, Some(envCtrl.component), new File("envCtrl.pdf"))
    //    IcdDbPrinter(db).saveToFile("TEST", None, new File("TEST.pdf"))

    // Test dropping a component
//    db.query.dropComponent(envCtrl.subsystem, envCtrl.component)
//    assert(db.query.getComponentModel("TEST", "envCtrl").isEmpty)

    //    db.query.dropComponent("TEST")
    //    assert(db.query.getComponentModel("TEST").isEmpty)
    //    assert(db.query.getComponentModel("ndme").isEmpty)

    db.dropDatabase()
  }

  // XXX TODO: Restore test after changes
  //  test("Ingest and then update example ICD") {
  //    val db = IcdDb("test")
  //    db.dropDatabase() // start with a clean db for test
  //
  //    // These three different directories are ingested under the same name (example), to test versioning
  //    testExample(db, "examples/example1", List("Test2"), "Comment for example1", majorVersion = false)
  //    testExample(db, "examples/example2", List("TEST"), "Comment for example2", majorVersion = true)
  //    testExample(db, "examples/example3", List("TEST"), "Comment for example3", majorVersion = false)
  //
  //    // Test Publish/Subscribe queries
  //    val subscribeInfoList = db.query.subscribes("test2.parallacticAngle")
  //    assert(subscribeInfoList.size == 1)
  //    assert(subscribeInfoList.head.componentName == "TEST")
  //    assert(subscribeInfoList.head.subscribeType == Events)
  //    assert(subscribeInfoList.head.name == "test2.parallacticAngle")
  //    assert(subscribeInfoList.head.subsystem == "TEST2")
  //
  //    val publishList = db.query.publishes("test.initialized")
  //    assert(publishList.size == 1)
  //    assert(publishList.head.componentName == "TEST")
  //    assert(publishList.head.item.publishType == Events)
  //    assert(publishList.head.prefix == "test")
  //    assert(publishList.head.item.name == "initialized")
  //
  //    // Test versions
  //    val versions = db.manager.getIcdVersions("example")
  //    assert(versions.size == 3)
  //    assert(versions.head.version == "2.1")
  //    assert(versions.head.comment == "Comment for example3")
  //    assert(versions(1).version == "2.0")
  //    assert(versions(1).comment == "Comment for example2")
  //    assert(versions(2).version == "1.0")
  //    assert(versions(2).comment == "Comment for example1")
  //
  //    // Test diff
  //    println("\nDiff example 2.0 2.1")
  //    for (diff <- db.manager.diff("example", "2.0", "2.1")) {
  //      // XXX TODO: add automatic test?
  //      //      println(s"\n${diff.path}:\n${diff.patch.toString()}")
  //    }
  //
  //    db.dropDatabase()
  //  }

  // XXX TODO: Turn this into a test
  def testModels(db: IcdDb): Unit = {
    val modelsList = db.query.getModels("TEST")
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
          val opt = db.query.getCommand(commandModel.subsystem, commandModel.component, receiveCommandModel.name)
          assert(opt.get == receiveCommandModel)
          val senders = db.query
            .getCommandSenders(commandModel.subsystem, commandModel.component, receiveCommandModel.name)
            .map(_.component)
        }
      }
    }
  }

  //  // Ingests the given dir under the name "example" (any previous version is saved in the history)
  //  def testExample(db: IcdDb, path: String, componentNames: List[String], comment: String, majorVersion: Boolean): Unit = {
  //    val problems = db.ingest(getTestDir(path), Some("example"), comment, majorVersion)
  //    for (p <- problems) println(p)
  //    assert(problems.isEmpty)
  //
  //    assert(db.query.getComponentNames == componentNames)
  //
  //    val components = db.query.getComponents
  //    assert(components.size == componentNames.size)
  //
  //    // Test getting items based on the component name
  //    val compModel = db.query.getComponentModel(componentNames.head).get
  //    assert(compModel.name == componentNames.head)
  //  }

}
