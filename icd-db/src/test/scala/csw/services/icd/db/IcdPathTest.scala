package csw.services.icd.db

import csw.services.icd.db.IcdDbQuery.IcdPath
import org.scalatest.funsuite.AnyFunSuite

// The IcdPath class is only used locally inside IcdDbQuery for collection names that are
// used to hold the data from model files or OpenApi JSON data
class IcdPathTest extends AnyFunSuite {
    test("Test parsing MongoDB collection names to get subsystem, component") {
      val p1 = IcdPath("NFIRAOS.rtc.subscribe")
      assert(p1.subsystem == "NFIRAOS")
      assert(p1.component == "NFIRAOS.rtc")
      assert(p1.parts == List("NFIRAOS", "rtc", "subscribe"))

      val p2 = IcdPath("IRIS.oiwfs.poa.component")
      assert(p2.subsystem == "IRIS")
      assert(p2.component == "IRIS.oiwfs.poa")
      assert(p2.parts == List("IRIS", "oiwfs.poa", "component"))

      val p3 = IcdPath("DMS.MetadataAccessService.service.Metadata Access Service")
      assert(p3.subsystem == "DMS")
      assert(p3.component == "DMS.MetadataAccessService")
      assert(p3.parts == List("DMS", "MetadataAccessService", "service", "Metadata Access Service"))

      val p4 = IcdPath("APS.subsystem")
      assert(p4.subsystem == "APS")
      assert(p4.component == "APS")
      assert(p4.parts == List("APS", "subsystem"))
    }
}
