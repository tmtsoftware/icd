package csw.services.icd.github

import csw.services.icd.db.IcdDb
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import org.scalatest.{BeforeAndAfter, Ignore}
import org.scalatest.funsuite.AnyFunSuite

// Test issue https://tmt-project.atlassian.net/browse/DEOPSICDDB-163
// problem when component is renamed
// Ignore until master branch of ESW is published, then use version number
@Ignore
class IcdGitTestRenamedComponents extends AnyFunSuite with BeforeAndAfter {

  test("Renamed component") {
    val db = IcdDb("test")
    db.dropDatabase() // start with a clean db for test
    val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions

    val comps1_4 = List(
      "alarm-monitor-ui",
      "master-sequencer",
    )

    val compsMaster = List(
      "acquisitionObsEventLib",
      "alarm_monitor_ui",
      "irInstDetLib",
      "master_sequencer",
      "opticalCcdInstDetLib",
      "opticalWfsOrGuiderDetLib"
    )

    IcdGitManager.ingest(db, SubsystemAndVersion("ESW", Some("1.4")), (s) => println(s), allApiVersions)
    assert(db.query.getComponentNames(Some("ESW")) == comps1_4)
    IcdGitManager.ingest(db, SubsystemAndVersion("ESW", Some("master")), (s) => println(s), allApiVersions)
    assert(db.query.getComponentNames(Some("ESW")) == compsMaster)
  }
}
