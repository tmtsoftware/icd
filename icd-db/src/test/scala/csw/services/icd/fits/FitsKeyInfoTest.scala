package csw.services.icd.fits

import csw.services.icd.IcdValidator
import csw.services.icd.db.{IcdDb, TestHelper}
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

  def containsList[A](list: List[A], sublist: List[A]): Boolean = {
    sublist.forall(list.contains)
  }

  test("Test FITS tag access") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val icdFits = IcdFits(db)
    val dir     = getTestDir(examplesDir)
    icdFits.ingestTags(new File(s"$dir/FITS-Tags.conf"))
    val fitsTags = icdFits.getFitsTags
    assert(fitsTags.tags.nonEmpty)
    assert(fitsTags.tags.contains("SL"))
    assert(fitsTags.tags.contains("DL"))
    assert(fitsTags.tags("SL").nonEmpty)
    assert(fitsTags.tags("DL").nonEmpty)
    val dlKeywords     = fitsTags.tags("DL")
    val slKeywords     = fitsTags.tags("SL")
    val wfosKeywords   = fitsTags.tags("WFOS")
    val irisKeywords   = fitsTags.tags("IRIS")
    val modhisKeywords = fitsTags.tags("MODHIS")
    assert(containsList(irisKeywords, dlKeywords))
    assert(containsList(modhisKeywords, dlKeywords))
    assert(!containsList(irisKeywords, slKeywords))
    assert(containsList(wfosKeywords, slKeywords))
  }

  test("Test FITS keyword access") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val icdFits = IcdFits(db)
    val dir     = getTestDir(examplesDir)
    icdFits.ingest(new File(s"$dir/FITS-Dictionary.json"))
    icdFits.output(new File("XXX.pdf"), None, None, None, PdfOptions())
    val fitsKeyMap = icdFits.getFitsKeyMap(None)
    val crpix1     = fitsKeyMap(FitsSource("TCS", "PointingKernelAssembly", "WCSFITSHeader", "CRPIX1", None, None))
    assert(crpix1.contains("CRPIX1"))
  }

  test("Test FITS available channels per subsystem") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val icdFits = IcdFits(db)
    val dir     = getTestDir(examplesDir)
    icdFits.ingestChannels(new File(s"$dir/FITS-Channels.conf"))
    val map = icdFits.getFitsChannelMap
    assert(map("IRIS") == Set("IRIS", "IFS", "IMAGER"));
    assert(map("MODHIS") == Set("MODHIS"));
  }

  // XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
  test("Test FITS keywords in model files") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test
    val icdFits = IcdFits(db)
    val dir     = getTestDir(examplesDir)
    icdFits.ingest(new File(s"$dir/FITS-Dictionary.json"))
    icdFits.ingestChannels(new File(s"$dir/FITS-Channels.conf"))
    val testHelper = new TestHelper(db)
    // Need ESW for ObserveEvents
    testHelper.ingestESW()
    // ingest examples/TEST into the DB
    testHelper.ingestDir(getTestDir(s"$examplesDir/TEST2"))
  }
}
