package csw.services.icd.db

import java.io.File
import sys.process._

class TestHelper(db: IcdDb) {
  // ESW is needed in order to process ObserveEvents in tests.
  // Get it from GitHub.
  def ingestESW(): Unit = {
    println("Ingesting https://github.com/tmt-icd/ESW-Model-Files (needed for ObserveEvent handling)")
    "git clone https://github.com/tmt-icd/ESW-Model-Files".!
    ingestDir(new File("ESW-Model-Files"))
    "rm -rf ESW-Model-Files".!
  }

  def ingestDir(dir: File): Unit = {
    val problems = db.ingestAndCleanup(dir)
    for (p <- problems) println(p)
    db.query.afterIngestFiles(problems, db.dbName)
  }
}
