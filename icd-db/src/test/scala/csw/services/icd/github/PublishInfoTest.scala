package csw.services.icd.github

import csw.services.icd.db.IcdDb
import org.scalatest.funsuite.AnyFunSuite

// XXX TEMP
class PublishInfoTest extends AnyFunSuite {

  test("Test publishing") {
    val db = IcdDb()
    val icdGitManager = IcdGitManager(db.versionManager)

    val publishInfoList = icdGitManager.getPublishInfo(Some("TEST2"))
    publishInfoList.foreach{ p =>
      println(s"\n${p.subsystem}: readyToPublish = ${p.readyToPublish}")
      p.apiVersions.foreach(a => println(s"${a.version}: ${a.date}, ${a.commit}"))
    }
  }
}

