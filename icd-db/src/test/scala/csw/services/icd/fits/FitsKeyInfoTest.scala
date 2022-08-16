package csw.services.icd.fits

import csw.services.icd.IcdValidator
import csw.services.icd.db.IcdDb
import icd.web.shared.{FitsSource, PdfOptions}
import org.scalatest.funsuite.AnyFunSuite

import java.io.File

class FitsKeyInfoTest extends AnyFunSuite {
  val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  test("XXX") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val icdFits = IcdFits(db)
    icdFits.ingest(new File("/shared/work/tmt/csw/icd/examples/3.0/FITS-Keywords.json"))
    icdFits.output(new File("xxx.pdf"), _ => true, PdfOptions())
    val fitsKeyMap = icdFits.getFitsKeyMap(None)
    val crpix1 = fitsKeyMap(FitsSource("TCS", "PointingKernelAssembly", "WCSFITSHeader", "CRPIX1", None, None))
    assert(crpix1.contains("CRPIX1"))
  }
}
