package csw.services.icd.db

import java.io.File
import sys.process.*

object TestHelper {
  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }
}

class TestHelper(db: IcdDb) {
  // ESW is needed in order to process ObserveEvents in tests.
  // Get it from GitHub.
  def ingestESW(): Unit = {
    println("Ingesting https://github.com/tmt-icd/ESW-Model-Files (needed for ObserveEvent handling)")
    "git clone https://github.com/tmt-icd/ESW-Model-Files".!
    try {
      ingestDir(new File("ESW-Model-Files/observe-events"))
    } finally {
        "rm -rf ESW-Model-Files".!
    }
  }

  def ingestDir(dir: File): Unit = {
    val problems = db.ingestAndCleanup(dir)
    for (p <- problems) println(p)
    assert(!problems.exists(_.severity == "error"))
  }
}
