package csw.services.icd.db

import java.io.File

import csw.services.icd.db.IcdDbQuery.Telemetry
import org.scalatest.{ DoNotDiscover, FunSuite }

/**
 * Tests the IcdDb class (Note: Assumes MongoDB is running)
 */
//@DoNotDiscover
class IcdDbTests extends FunSuite {
  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Ingest example ICD into database, then query the DB") {
    val db = IcdDb("test")
    db.dropDatabase() // start with a clean db for test

    // ingest examples/NFIRAOS into the DB
    val problems = db.ingest(getTestDir("examples/NFIRAOS"))
    for (p ← problems) println(p)
    assert(problems.isEmpty)

    // query the DB
    assert(db.query.getComponentNames == List("envCtrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getComponentNames("NFIRAOS") == List("envCtrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getAssemblyNames == List("envCtrl", "lgsWfs", "nacqNhrwfs", "ndme", "rtc"))
    assert(db.query.getHcdNames == List())
    assert(db.query.getSubsystemNames == List("NFIRAOS"))

    val components = db.query.getComponents
    assert(components.size == 5)

    // Test getting items based on the component name
    val envCtrl = db.query.getComponentModel("NFIRAOS", "envCtrl").get
    assert(envCtrl.component == "envCtrl")
    assert(envCtrl.componentType == "Assembly")
    assert(envCtrl.prefix == "nfiraos.ncc.envCtrl")

    val commands = db.query.getCommandModel(envCtrl.subsystem, envCtrl.component).get
    assert(commands.receive.size == 2)

    assert(commands.receive.head.name == "ENVIRONMENTAL_CONTROL_INITIALIZE")
    assert(commands.receive.head.requirements.head == "INT-NFIRAOS-AOESW-0400")

    assert(commands.receive.last.name == "ENVIRONMENTAL_CONTROL_STOP")
    assert(commands.receive.last.requirements.head == "INT-NFIRAOS-AOESW-0405")

    val publish = db.query.getPublishModel(envCtrl).get
    val telemetryList = publish.telemetryList
    assert(telemetryList.size == 2)
    val logging = telemetryList.head
    assert(logging.name == "logging")
    assert(!logging.archive)

    val sensors = telemetryList.last
    assert(sensors.name == "sensors")
    assert(sensors.archive)
    val attrList = sensors.attributesList

    val temp_ngsWfs = attrList.head
    assert(temp_ngsWfs.name == "temp_ngsWfs")
    assert(temp_ngsWfs.description == "NGS WFS temperature")
    assert(temp_ngsWfs.typeStr == "number")
    assert(temp_ngsWfs.units == "degC")

    // Test publish queries
    val published = db.query.getPublished(envCtrl).filter(p ⇒
      p.name == "sensors" && p.publishType == Telemetry)
    assert(published.size == 1)
    assert(published.head.publishType == Telemetry)

    val sensorList = db.query.publishes("nfiraos.ncc.envCtrl.sensors", "NFIRAOS", Telemetry)
    assert(sensorList.size == 1)
    assert(sensorList.head.componentName == "envCtrl")
    assert(sensorList.head.item.publishType == Telemetry)
    assert(sensorList.head.prefix == "nfiraos.ncc.envCtrl")
    assert(sensorList.head.item.name == "sensors")

    // Test accessing ICD models
    testModels(db)

    //    // Test saving document from the database
    //    IcdDbPrinter(db).saveToFile(envCtrl.subsystem, Some(envCtrl.component), new File("envCtrl.pdf"))
    //    IcdDbPrinter(db).saveToFile("NFIRAOS", None, new File("NFIRAOS.pdf"))

    // Test dropping a component
    db.query.dropComponent(envCtrl.subsystem, envCtrl.component)
    assert(db.query.getComponentModel("NFIRAOS", "envCtrl").isEmpty)

    //    db.query.dropComponent("NFIRAOS")
    //    assert(db.query.getComponentModel("NFIRAOS").isEmpty)
    //    assert(db.query.getComponentModel("ndme").isEmpty)

    db.dropDatabase()
  }

  // XXX TODO: Restore test after changes
  //  test("Ingest and then update example ICD") {
  //    val db = IcdDb("test")
  //    db.dropDatabase() // start with a clean db for test
  //
  //    // These three different directories are ingested under the same name (example), to test versioning
  //    testExample(db, "examples/example1", List("Tcs"), "Comment for example1", majorVersion = false)
  //    testExample(db, "examples/example2", List("NFIRAOS"), "Comment for example2", majorVersion = true)
  //    testExample(db, "examples/example3", List("NFIRAOS"), "Comment for example3", majorVersion = false)
  //
  //    // Test Publish/Subscribe queries
  //    val subscribeInfoList = db.query.subscribes("tcs.parallacticAngle")
  //    assert(subscribeInfoList.size == 1)
  //    assert(subscribeInfoList.head.componentName == "NFIRAOS")
  //    assert(subscribeInfoList.head.subscribeType == Telemetry)
  //    assert(subscribeInfoList.head.name == "tcs.parallacticAngle")
  //    assert(subscribeInfoList.head.subsystem == "TCS")
  //
  //    val publishList = db.query.publishes("nfiraos.initialized")
  //    assert(publishList.size == 1)
  //    assert(publishList.head.componentName == "NFIRAOS")
  //    assert(publishList.head.item.publishType == Events)
  //    assert(publishList.head.prefix == "nfiraos")
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
  //    for (diff ← db.manager.diff("example", "2.0", "2.1")) {
  //      // XXX TODO: add automatic test?
  //      //      println(s"\n${diff.path}:\n${diff.patch.toString()}")
  //    }
  //
  //    db.dropDatabase()
  //  }

  // XXX TODO: Turn this into a test
  def testModels(db: IcdDb): Unit = {
    val modelsList = db.query.getModels("NFIRAOS")
    val publishInfo = for (models ← modelsList) yield {
      models.publishModel.foreach { publishModel ⇒
        publishModel.telemetryList.foreach { telemetryModel ⇒
          // println(s"${publishModel.component} publishes telemetry ${telemetryModel.name}: ${telemetryModel.description}")
        }
      }
      models.subscribeModel.foreach { subscribeModel ⇒
        subscribeModel.telemetryList.foreach { telemetryModel ⇒
          // println(s"${subscribeModel.component} subscribes to telemetry ${telemetryModel.name} from ${telemetryModel.subsystem}")
        }
      }
    }

    modelsList.foreach { models ⇒
      models.commandModel.foreach { commandModel ⇒
        commandModel.receive.foreach { receiveCommandModel ⇒
          val opt = db.query.getCommand(commandModel.subsystem, commandModel.component, receiveCommandModel.name)
          assert(opt.get == receiveCommandModel)
          val senders = db.query.getCommandSenders(commandModel.subsystem, commandModel.component, receiveCommandModel.name)
            .map(_.component)
        }
      }
    }
  }

  //  // Ingests the given dir under the name "example" (any previous version is saved in the history)
  //  def testExample(db: IcdDb, path: String, componentNames: List[String], comment: String, majorVersion: Boolean): Unit = {
  //    val problems = db.ingest(getTestDir(path), Some("example"), comment, majorVersion)
  //    for (p ← problems) println(p)
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
