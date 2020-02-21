package csw.services.icd.github

import java.io.File
import java.nio.file.Files

import csw.services.icd.db.IcdDb
import csw.services.icd.db.IcdVersionManager.SubsystemAndVersion
import icd.web.shared.IcdVersion
import org.eclipse.jgit.api.Git
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite

class IcdGitManagerTests extends AnyFunSuite with BeforeAndAfter {

  private var repoDir: File = _
  private var git: Git      = _

  private val user       = System.getProperty("user.name")
  private val password   = ""
  private val comment    = "test comment"
  private val subsysList = List(SubsystemAndVersion("TEST", Some("1.0")), SubsystemAndVersion("TEST2", Some("1.0")))

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
    // Note: Normally the return value from IcdGitManager.getAllVersions could be cached and reused for a while,
    // but not here, since we are modifying the test repository with new versions.

    // List should return empty
    assert(IcdGitManager.list(subsysList, IcdGitManager.getAllVersions._2).isEmpty)

    // Publish API for TEST subsystem
    val i1 = IcdGitManager.publish("TEST", majorVersion = false, user, password, comment)
    assert(i1.version == "1.0")
    assert(i1.comment == comment)
    assert(i1.user == user)
    assert(i1.subsystem == "TEST")

    val maybeApiVersion = IcdGitManager.getApiVersions(subsysList.head, IcdGitManager.getAllVersions._1)
    assert(maybeApiVersion.isDefined)
    val apiVersion = maybeApiVersion.get
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

    val maybeApiVersion2 = IcdGitManager.getApiVersions(subsysList.tail.head, IcdGitManager.getAllVersions._1)
    assert(maybeApiVersion2.isDefined)
    val apiVersion2 = maybeApiVersion2.get
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

    val maybeIcdVersions = IcdGitManager.list(subsysList, IcdGitManager.getAllVersions._2)
    assert(maybeIcdVersions.isDefined)
    val icdVersions = maybeIcdVersions.get
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
    val (apis, icds) = IcdGitManager.getAllVersions
    IcdGitManager.ingest(db, subsysList, (s: String) => println(s), apis, icds)
    val icdNames = db.versionManager.getIcdNames
    assert(icdNames.size == 1)
    assert(icdNames.head.subsystem == "TEST")
    assert(icdNames.head.target == "TEST2")

    val versions = db.versionManager.getVersions("TEST")
    assert(versions.size == 2) // head is unnamed version "*"
    assert(versions.head.maybeVersion.isEmpty)
    assert(versions.tail.head.maybeVersion.get == "1.0")
    assert(versions.tail.head.user == user)
    assert(versions.tail.head.comment == comment)
    assert(versions.tail.head.date.toString() == api.date)

    val versions2 = db.versionManager.getVersions("TEST2")
    assert(versions2.size == 2) // head is unnamed version "*"
    assert(versions2.head.maybeVersion.isEmpty)
    assert(versions2.tail.head.maybeVersion.get == "1.0")
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

    val (allApiVersions, allIcdVersions) = IcdGitManager.getAllVersions
    assert(allApiVersions.size == 2)
    assert(allIcdVersions.size == 1)
    assert(allApiVersions.head.subsystem == "TEST")
    assert(allApiVersions.tail.head.subsystem == "TEST2")
    assert(allIcdVersions.head.subsystems == List("TEST", "TEST2"))
    assert(allIcdVersions.head.icds.size == 1)
    assert(allIcdVersions.head.icds.head.icdVersion == "1.0")
  }
}
