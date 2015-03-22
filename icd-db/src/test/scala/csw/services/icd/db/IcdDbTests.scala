package csw.services.icd.db

import java.io.File

import com.typesafe.config.{ ConfigRenderOptions, ConfigFactory }
import org.scalatest.{ DoNotDiscover, FunSuite }

/**
 * Tests the IcdDb class (Note: Assumes MongoDB is running)
 */
@DoNotDiscover
class IcdDbTests extends FunSuite {

  test("Ingest example ICD into database") {
    val db = IcdDb("test")
    db.dropDatabase()
    val problems = db.ingest("NFIRAOS", new File("examples/NFIRAOS"))
    for (p ← problems) println(p)
    assert(problems.isEmpty)

    assert(db.query.getComponentNames == List("NFIRAOS", "envCtrl", "lgsWfs", "nacqNhrwfs", "ndme"))
    assert(db.query.getAssemblyNames == List("envCtrl", "lgsWfs", "nacqNhrwfs", "ndme"))
    assert(db.query.getHcdNames == List())

    val components = db.query.getComponents
    assert(components.size == 5)

    val envCtrl = db.query.getComponent("envCtrl").get
    assert(envCtrl.name == "envCtrl")
    assert(envCtrl.componentType == "Assembly")
    assert(envCtrl.prefix == "nfiraos.ncc.assembly.envCtrl")
    assert(envCtrl.usesConfigurations)
    assert(!envCtrl.usesEvents)

    val commands = db.query.getCommands(envCtrl.name).get
    assert(commands.items.size == 2)
    assert(commands.items.head.name == "ENVIRONMENTAL_CONTROL_INITIALIZE")
    assert(commands.items.head.requirements.head == "INT-NFIRAOS-AOESW-0400")

    assert(commands.items.last.name == "ENVIRONMENTAL_CONTROL_STOP")
    assert(commands.items.last.requirements.head == "INT-NFIRAOS-AOESW-0405")
  }
}
