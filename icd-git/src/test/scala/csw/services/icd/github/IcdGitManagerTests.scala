package csw.services.icd.github

import java.io.File
import java.nio.file.Files

import csw.services.icd.db.IcdDb
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import icd.web.shared.IcdVersion
import org.eclipse.jgit.api.Git
import org.scalatest.{BeforeAndAfter, FunSuite}

class IcdGitManagerTests extends FunSuite with BeforeAndAfter {

  var repoDir: File = _
  var git: Git = _

  val user = System.getProperty("user.name")
  val password = ""
  val comment = "test comment"
  val subsysList = List(SubsystemAndVersion("TEST", Some("1.0")), SubsystemAndVersion("TEST2", Some("1.0")))

  // Use a dummy repo for the icd and api versions
  before {
    repoDir = Files.createTempDirectory("test").toFile
    git = Git.init.setDirectory(repoDir).setBare(true).call
    System.setProperty("csw.services.icd.github.uri", repoDir.toURI.toString)
  }

  // Use a dummy repo for the icd and api versions
  after {
    git.close()
    IcdGitManager.deleteDirectoryRecursively(repoDir)
    System.clearProperty("csw.services.icd.github.uri")
  }

  test("Test publishing") {
    // List should return empty
    assert(IcdGitManager.list(subsysList).isEmpty)

    // Publish API for TEST subsystem
    val i1 = IcdGitManager.publish("TEST", majorVersion = false, user, password, comment)
    assert(i1.version == "1.0")
    assert(i1.comment == comment)
    assert(i1.user == user)
    assert(i1.subsystem == "TEST")

    val apiVersionOpt = IcdGitManager.getApiVersions(subsysList.head)
    assert(apiVersionOpt.isDefined)
    val apiVersion = apiVersionOpt.get
    assert(apiVersion.subsystem == "TEST")
    assert(apiVersion.apis.size == 1)
    val api = apiVersion.apis.head
    assert(api.version == "1.0")
    assert(api.user == user)
    assert(api.comment == comment)

    // Publish API for TEST2 subsystem
    val i2 = IcdGitManager.publish("TEST2", majorVersion = false, user, password, comment + " 2")
    assert(i2.version == "1.0")
    assert(i2.comment == comment + " 2")
    assert(i2.user == user)
    assert(i2.subsystem == "TEST2")

    val apiVersionOpt2 = IcdGitManager.getApiVersions(subsysList.tail.head)
    assert(apiVersionOpt2.isDefined)
    val apiVersion2 = apiVersionOpt2.get
    assert(apiVersion2.subsystem == "TEST2")
    assert(apiVersion2.apis.size == 1)
    val api2 = apiVersion2.apis.head
    assert(api2.version == "1.0")
    assert(api2.user == user)
    assert(api2.comment == comment + " 2")

    // Publish the ICD between TEST and TEST2
    val i3 = IcdGitManager.publish(subsysList, majorVersion = false, user, password, comment + " 3")
    assert(i3.icdVersion == IcdVersion("1.0", "TEST", "1.0", "TEST2", "1.0"))
    assert(i3.comment == comment + " 3")
    assert(i3.user == user)

    val icdVersionsOpt = IcdGitManager.list(subsysList)
    assert(icdVersionsOpt.isDefined)
    val icdVersions = icdVersionsOpt.get
    assert(icdVersions.subsystems == List("TEST", "TEST2"))
    assert(icdVersions.icds.size == 1)
    val icd = icdVersions.icds.head
    assert(icd.icdVersion == "1.0")
    assert(icd.versions == List("1.0", "1.0"))
    assert(icd.user == user)
    assert(icd.comment == comment + " 3")

    // Ingest the ICD into the database
    val db = IcdDb("test")
    try {
      db.dropDatabase()
    } catch {
      case ex: Exception => throw new RuntimeException("Unable to drop the existing ICD database", ex)
    }
    IcdGitManager.ingest(db, subsysList)
    val icdNames = db.versionManager.getIcdNames
    assert(icdNames.size == 1)
    assert(icdNames.head.subsystem == "TEST")
    assert(icdNames.head.target == "TEST2")

    val versions = db.versionManager.getVersions("TEST")
    assert(versions.size == 2) // head is unnamed version "*"
    assert(versions.head.versionOpt.isEmpty)
    assert(versions.tail.head.versionOpt.get == "1.0")
    assert(versions.tail.head.user == user)
    assert(versions.tail.head.comment == comment)
    assert(versions.tail.head.date.toString() == api.date)

    val versions2 = db.versionManager.getVersions("TEST2")
    assert(versions2.size == 2) // head is unnamed version "*"
    assert(versions2.head.versionOpt.isEmpty)
    assert(versions2.tail.head.versionOpt.get == "1.0")
    assert(versions2.tail.head.user == user)
    assert(versions2.tail.head.comment == comment + " 2")
    assert(versions2.tail.head.date.toString() == api2.date)

    val icdVersionInfoList = db.versionManager.getIcdVersions("TEST", "TEST2")
    assert(icdVersionInfoList.size == 1)
    val icdVersionInfo = icdVersionInfoList.head
    assert(icdVersionInfo.icdVersion.icdVersion == "1.0")
    assert(icdVersionInfo.icdVersion.subsystem == "TEST")
    assert(icdVersionInfo.icdVersion.target == "TEST2")
    assert(icdVersionInfo.icdVersion.subsystemVersion == "1.0")
    assert(icdVersionInfo.icdVersion.targetVersion == "1.0")
    assert(icdVersionInfo.comment == comment + " 3")
    assert(icdVersionInfo.user == user)
    assert(icdVersionInfo.date == icd.date)
  }
}
