package csw.services.icd.db

import java.io.File

import org.scalatest.{DoNotDiscover, FunSuite}

/**
 * Tests the IcdDb class (Note: Assumes MongoDB is running)
 */
@DoNotDiscover
class IcdDbTests extends FunSuite {

  test("Ingest example ICD into database, then query the DB") {
    val db = IcdDb("test")
    db.dropDatabase() // start with a clean db for test

    // ingest examples/NFIRAOS into the DB
    val problems = db.ingest("NFIRAOS", new File("examples/NFIRAOS"))
    for (p ← problems) println(p)
    assert(problems.isEmpty)

    // query the DB
    assert(db.query.getComponentNames == List("NFIRAOS", "envCtrl", "lgsWfs", "nacqNhrwfs", "ndme"))
    assert(db.query.getAssemblyNames == List("envCtrl", "lgsWfs", "nacqNhrwfs", "ndme"))
    assert(db.query.getHcdNames == List())

    val components = db.query.getComponents
    assert(components.size == 5)

    // Test getting items based on the component name
    val envCtrl = db.query.getComponentModel("envCtrl").get
    assert(envCtrl.name == "envCtrl")
    assert(envCtrl.componentType == "Assembly")
    assert(envCtrl.prefix == "nfiraos.ncc.assembly.envCtrl")
    assert(envCtrl.usesConfigurations)
    assert(!envCtrl.usesEvents)

    val commands = db.query.getCommandModel(envCtrl.name).get
    assert(commands.items.size == 2)

    assert(commands.items.head.name == "ENVIRONMENTAL_CONTROL_INITIALIZE")
    assert(commands.items.head.requirements.head == "INT-NFIRAOS-AOESW-0400")

    assert(commands.items.last.name == "ENVIRONMENTAL_CONTROL_STOP")
    assert(commands.items.last.requirements.head == "INT-NFIRAOS-AOESW-0405")

    val publish = db.query.getPublishModel(envCtrl.name).get
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

    // Test saving document from the database
    IcdDbPrinter(db.query).saveToFile(envCtrl.name, new File("envCtrl.pdf"))
    IcdDbPrinter(db.query).saveToFile("NFIRAOS", new File("NFIRAOS.pdf"))

    // Test dropping a component
    db.query.dropComponent(envCtrl.name)
    assert(db.query.getComponentModel("envCtrl").isEmpty)

    db.query.dropComponent("NFIRAOS")
    assert(db.query.getComponentModel("NFIRAOS").isEmpty)
    assert(db.query.getComponentModel("ndme").isEmpty)

    db.dropDatabase()
  }

  test("Ingest and then update example ICD") {
    val db = IcdDb("test")
    db.dropDatabase() // start with a clean db for test

    testExample(db, "examples/example1", List("Tcs"))
    testExample(db, "examples/example2", List("NFIRAOS"))
    testExample(db, "examples/example3", List("NFIRAOS"))

    db.dropDatabase()
  }

  def testExample(db: IcdDb, path: String, componentNames: List[String]): Unit = {
    val problems = db.ingest("example", new File(path))
    for (p ← problems) println(p)
    assert(problems.isEmpty)

    assert(db.query.getComponentNames == componentNames)

    val components = db.query.getComponents
    assert(components.size == componentNames.size)

    // Test getting items based on the component name
    val compModel = db.query.getComponentModel(componentNames.head).get
    assert(compModel.name == componentNames.head)
  }

}
