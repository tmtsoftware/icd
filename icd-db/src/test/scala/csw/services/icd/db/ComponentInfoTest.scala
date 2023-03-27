package csw.services.icd.db

import java.io.File
import csw.services.icd.IcdValidator
import csw.services.icd.html.OpenApiToHtml
import icd.web.shared.{ComponentInfo, SubsystemWithVersion}
import org.scalatest.funsuite.AnyFunSuite

class ComponentInfoTest extends AnyFunSuite {
  Resolver.loggingEnabled = false
  private val examplesDir = s"examples/${IcdValidator.currentSchemaVersion}"
  private val dbName      = "test"

  // The relative location of the the examples directory can change depending on how the test is run
  private def getTestDir(path: String): File = {
    val dir = new File(path)
    if (dir.exists()) dir else new File(s"../$path")
  }

  private def checkRefs(info: ComponentInfo): Unit = {
    val eventList = info.publishes.get.eventList
    assert(eventList.exists(_.eventModel.name == "engMode"))
    assert(eventList.exists(_.eventModel.name == "engMode2"))
    val engMode  = eventList.find(_.eventModel.name == "engMode").get.eventModel
    val engMode2 = eventList.find(_.eventModel.name == "engMode2").get.eventModel
    assert(!engMode.archive)
    assert(engMode2.archive)
    assert(engMode2.description == engMode.description)
    assert(engMode2.parameterList == engMode.parameterList)
    assert(engMode.parameterList.exists(_.name == "mode2Error"))
    val mode2Error = engMode.parameterList.find(_.name == "mode2Error").get
    assert(mode2Error.ref == "")
    assert(mode2Error.refError == "Error: Invalid parameter ref: modeXXX: Parameter modeXXX not found")

    assert(eventList.exists(_.eventModel.name == "engMode2Error"))
    val engMode2Error = eventList.find(_.eventModel.name == "engMode2Error").get.eventModel
    assert(engMode2Error.ref == "")
    assert(engMode2Error.refError == "Error: Invalid ref 'engModeXXX': Event engModeXXX not found in lgsWfs")

    val commands = info.commands.get.commandsReceived.map(_.receiveCommandModel)
    val cmd      = commands.find(_.name == "LGS_WFS_INITIALIZE").get
    val refCmd   = commands.find(_.name == "LGS_WFS_INITIALIZE_REF").get
    assert(cmd.parameters == refCmd.parameters)
    assert(cmd.completionType == refCmd.completionType)
    assert(cmd.requirements == refCmd.requirements)
    assert(cmd.requiredArgs == refCmd.requiredArgs)
    assert(cmd.description != refCmd.description)
    assert(cmd.completionConditions != refCmd.completionConditions)
    assert(refCmd.refError == "")
  }

  private def checkInfo(info: ComponentInfo, clientApi: Boolean): Unit = {
    assert(info.componentModel.component == "lgsWfs")
    assert(info.publishes.nonEmpty)
    checkRefs(info)
    val eventList = info.publishes.get.eventList
    assert(eventList.nonEmpty)
    //    eventList.foreach { pubInfo =>
    //      println(s"lgsWfs publishes event: ${pubInfo.eventModel.name}")
    //      pubInfo.subscribers.foreach { subInfo =>
    //        println(
    //          s"${subInfo.subscribeModelInfo.component} from ${subInfo.subscribeModelInfo.subsystem} subscribes to ${subInfo.subscribeModelInfo.name}"
    //        )
    //      }
    //    }
    assert(eventList.exists(_.eventModel.name == "engMode"))
    assert(eventList.exists(_.eventModel.name == "contRead"))
    assert(eventList.exists(_.eventModel.name == "intTime"))
    assert(eventList.exists(_.eventModel.name == "state"))
    assert(eventList.exists(_.eventModel.name == "heartbeat"))
    assert(info.subscribes.nonEmpty == clientApi)
    if (clientApi) {
      val subscribeInfo = info.subscribes.get.subscribeInfo
      assert(subscribeInfo.nonEmpty)
      //      subscribeInfo.foreach { subInfo =>
      //        println(s"lgsWfs subscribes to ${subInfo.subscribeModelInfo.name} from ${subInfo.subscribeModelInfo.subsystem}")
      //      }
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "zenithAngle" && d.subscribeModelInfo.subsystem == "TEST2"))
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "parallacticAngle" && d.subscribeModelInfo.subsystem == "TEST2"))
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "visWfsPos" && d.subscribeModelInfo.subsystem == "TEST2"))
    }
  }

  private def checkInfo2(info: ComponentInfo, clientApi: Boolean): Unit = {
    // XXX Service client JSON filtering (temp)
    info.services.foreach { service =>
      service.servicesRequired.foreach { serviceRequiredInfo =>
        serviceRequiredInfo.maybeServiceModelProvider.map(_.openApi).foreach { openApiJson =>
          val paths = serviceRequiredInfo.serviceModelClient.paths
          val json  = OpenApiToHtml.filterOpenApiJson(openApiJson, paths)
//          println(s"XXX filtered OpenApiJson = \n$json")
        // XXX TODO
        }
      }
    }
  }

  private def checkInfo3(info: ComponentInfo, clientApi: Boolean): Unit = {
    assert(info.componentModel.component == "test2Pk")
    assert(info.publishes.nonEmpty)
    assert(info.commands.nonEmpty)
    val pubEventList = info.publishes.get.eventList
//    val recvCommands = info.commands.get.commandsReceived
//    assert(recvCommands.nonEmpty)

    assert(pubEventList.exists(_.eventModel.name == "zenithAngle"))
    assert(pubEventList.exists(_.eventModel.name == "parallacticAngle"))

    assert(info.subscribes.nonEmpty == clientApi)
    if (clientApi) {
      // Check that the client refs were resolved correctly
      val subscribeInfo = info.subscribes.get.subscribeInfo
      assert(subscribeInfo.nonEmpty)
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "engMode" && d.subscribeModelInfo.subsystem == "TEST"))
      assert(subscribeInfo.exists(d => d.eventModel.get.name == "engMode2" && d.subscribeModelInfo.subsystem == "TEST"))
      val engMode = subscribeInfo.find(_.eventModel.get.name == "engMode").get
      val engMode2 = subscribeInfo.find(_.eventModel.get.name == "engMode2").get
      assert(engMode.description == engMode2.description)
      assert(engMode.eventModel.get.description == engMode2.eventModel.get.description)

      val sentCommands = info.commands.get.commandsSent
      assert(sentCommands.nonEmpty)
      assert(sentCommands.exists(_.name == "LGS_WFS_INITIALIZE"))
      val cmd = sentCommands.find(_.name == "LGS_WFS_INITIALIZE").get
      assert(cmd.receiveCommandModel.nonEmpty)
      assert(cmd.receiveCommandModel.get.description.contains("LGS_WFS_INITIALIZE command will"))
      assert(cmd.receiveCommandModel.get.parameters.exists(_.name == "wfsUsed"))
      assert(cmd.receiveCommandModel.get.parameters.exists(_.name == "wfsUsed2"))
      assert(cmd.receiveCommandModel.get.parameters.find(_.name == "wfsUsed2").get.description == "<p>OIWFS used</p>")
    } else {
      assert(info.subscribes.isEmpty)
      assert(info.commands.get.commandsSent.isEmpty)
    }
  }


  test("Get pub/sub info from database") {
    val db = IcdDb(dbName)
    db.dropDatabase() // start with a clean db for test

    val testHelper = new TestHelper(db)
    // Need ESW for ObserveEvents
    testHelper.ingestESW()
    // ingest examples/TEST into the DB
    testHelper.ingestDir(getTestDir(s"$examplesDir/TEST"))
    testHelper.ingestDir(getTestDir(s"$examplesDir/TEST2"))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = true)
      .getComponentInfo(SubsystemWithVersion("TEST", None, Some("lgsWfs")), None, Map.empty)
      .foreach(checkInfo(_, clientApi = true))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = false)
      .getComponentInfo(SubsystemWithVersion("TEST", None, Some("lgsWfs")), None, Map.empty)
      .foreach(checkInfo(_, clientApi = false))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = true)
      .getComponentInfo(SubsystemWithVersion("TEST", None, None), None, Map.empty)
      .foreach(checkInfo2(_, clientApi = true))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = true)
      .getComponentInfo(SubsystemWithVersion("TEST2", None, Some("test2Pk")), None, Map.empty)
      .foreach(checkInfo3(_, clientApi = true))

    new ComponentInfoHelper(db.versionManager, displayWarnings = false, clientApi = false)
      .getComponentInfo(SubsystemWithVersion("TEST2", None, Some("test2Pk")), None, Map.empty)
      .foreach(checkInfo3(_, clientApi = false))
  }
}
