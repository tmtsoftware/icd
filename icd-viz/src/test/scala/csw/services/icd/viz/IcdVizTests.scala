package csw.services.icd.viz
import java.io.File
import java.nio.file.Files

import csw.services.icd.IcdValidator
import csw.services.icd.db.{IcdDb, Resolver}
import icd.web.shared.{IcdVizOptions, SubsystemWithVersion}
import org.scalatest.funsuite.AnyFunSuite

import scala.io.Source

class IcdVizTests extends AnyFunSuite {
  Resolver.loggingEnabled = false
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("Text graph generation from examples/2.0/TEST subsystem") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    // ingest examples/TEST into the DB
    val problems = db.ingestAndCleanup(getTestDir(s"$examplesDir/TEST"))
    for (p <- problems) println(p)
    db.query.afterIngestFiles(problems, dbName)

    val subsystems = List(SubsystemWithVersion("TEST"))
    val dotPath    = Files.createTempFile("icdviz", ".dot")
    val options = IcdVizOptions(
      subsystems = subsystems,
      showPlot = false,
      dotFile = Some(dotPath.toFile),
      missingCommands = true,
      commandLabels = true
    )
    IcdVizManager.showRelationships(db, options)
    val okDotStr  = Source.fromResource("icdviz.dot").getLines().mkString("\n")
    // The order of the two subsystems is random, so need to check that...
    val okDotStr2  = Source.fromResource("icdviz2.dot").getLines().mkString("\n")
    val dotStr = new String(Files.readAllBytes(dotPath))
    println(s"Compare $dotPath with test/resources/icdviz.dot")
    assert(okDotStr == dotStr || okDotStr2 == dotStr)
//    dotPath.toFile.delete()
  }
}
