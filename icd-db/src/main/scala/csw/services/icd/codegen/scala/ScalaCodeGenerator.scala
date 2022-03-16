package csw.services.icd.codegen.scala

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdDb, IcdVersionManager}
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, ReceivedCommandInfo, SubsystemWithVersion}

import java.io.{File, PrintWriter}

/**
 * Generates Scala code for a subsystem from the icd database.
 */
class ScalaCodeGenerator(db: IcdDb) {

  // XXX TODO
//  private def getUnits(units: String): String = {
//
//  }

  private def warning(s: String): Unit = {
    println(s"Warning: $s")
  }

  private def getParamType(maybeType: Option[String]): String = {
    maybeType match {
      case Some(t) =>
        t.capitalize match {
          case "Number" =>
            warning(s"Replacing unsupported 'Number' with 'Long'")
            "Long"
          case "Object" =>
            warning("Replacing invalid type 'object' with 'string'")
            "String"
          case "Integer" => "Int"
          case "TaiDate" => "TAITime"
          case "UtcDate" => "UTCTime"
          case x         => x
        }
      case None => "Double" // XXX
    }
  }

  private def paramDef(p: ParameterModel): String = {
    p.maybeType match {
      case Some(t) =>
        val paramType = getParamType(Some(t))
        paramType match {
          case "Array" =>
            val arrayType = getParamType(p.maybeArrayType)
            arrayType match {
              case "String" =>
                warning("Replacing unsupported 'StringArray' type with 'String' (can still have multiple values!)")
                s"""val `${p.name}Key`: Key[String] = StringKey.make("${p.name}")"""
              case "Boolean" =>
                warning("Replacing unsupported 'BooleanArray' type with 'Boolean' (can still have multiple values!)")
                s"""val `${p.name}Key`: Key[Boolean] = BooleanKey.make("${p.name}")"""
              case _ =>
                val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
                if (isMatrix)
                  s"""val `${p.name}Key`: Key[MatrixData[$arrayType]] = ${arrayType}MatrixKey.make("${p.name}")"""
                else
                  s"""val `${p.name}Key`: Key[ArrayData[$arrayType]] = ${arrayType}ArrayKey.make("${p.name}")"""
            }
          case t =>
            s"""val `${p.name}Key`: Key[$t] = ${t}Key.make("${p.name}")"""
        }
      case None =>
        p.maybeEnum match {
          case Some(e) =>
            val choices = e.map(choice => s""""$choice"""").mkString(", ")
            s"""val `${p.name}Key`: GChoiceKey = ChoiceKey.make("${p.name}", $choices)"""
          case None =>
            ""
        }
    }
  }

  private def getParams(paramList: List[ParameterModel]): String = {
    val params = paramList.map { p =>
      s"""
         |/**
         | * ${p.description}
         | */
         |${paramDef(p)}
         |""".stripMargin
    }
    params.mkString("\n")
  }

  private def eventsDefs(e: EventInfo, eventType: String): String = {
    s"""
       |/**
       | * ${e.eventModel.description}
       | */
       |object `${e.eventModel.name}$eventType` {
       |    val eventKey: EventKey = EventKey(prefix, EventName("${e.eventModel.name}"))
       |
       |    ${getParams(e.eventModel.parameterList)}
       |}
       |""".stripMargin
  }

  private def commandDefs(c: ReceivedCommandInfo): String = {
    s"""
       |/**
       | * ${c.receiveCommandModel.description}
       | */
       |object `${c.receiveCommandModel.name}Command` {
       |    ${getParams(c.receiveCommandModel.parameters)}
       |}
       |""".stripMargin
  }

  private def sourceForComponent(info: ComponentInfo): String = {
    val comment =
      s"/** API for ${info.componentModel.componentType}: ${info.componentModel.subsystem}.${info.componentModel.component} */"
    val prefix = s"""val prefix: Prefix = Prefix(Subsystem.${info.componentModel.subsystem
      .toUpperCase()
      .replace("TEST2", "CSW")
      .replace("TEST", "CSW")}, "${info.componentModel.component}")"""
    val eventKeys = info.publishes.toList.flatMap { p =>
      val events        = p.eventList.map(e => eventsDefs(e, "Event"))
      val currentStates = p.currentStateList.map(e => eventsDefs(e, "CurrentState"))
      events ::: currentStates
    }
    val commandKeys = info.commands.toList.flatMap { commands =>
      commands.commandsReceived.map(c => commandDefs(c))
    }
    s"""
       |$comment
       |object `${info.componentModel.component}` {
       |$prefix
       |
       |${eventKeys.mkString("\n")}
       |${commandKeys.mkString("\n")}
       |}
       |""".stripMargin
  }

  /**
   * Generates a source file with the API for the given subsystem / component (or all components)
   * @param subsystemStr a string containing the subsystem, possibly followed by a ':' and the version
   * @param maybeComponent optional component name (default: all subsystem components)
   * @param sourceFile generate code in this file
   * @param maybePackage optional package name for Scala and Java files
   */
  def generate(subsystemStr: String, maybeComponent: Option[String], sourceFile: File, maybePackage: Option[String]): Unit = {
    import sys.process._
    val s              = IcdVersionManager.SubsystemAndVersion(subsystemStr)
    val sv             = SubsystemWithVersion(s.subsystem, s.maybeVersion, maybeComponent)
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem)), None)
    val versionManager = new CachedIcdVersionManager(query)
    val subsystemModel = db.versionManager.getSubsystemModel(sv, None)
    val infoList = new ComponentInfoHelper(false, false, maybeStaticHtml = Some(true))
      .getComponentInfoList(versionManager, sv, None)
    val defs       = infoList.map(sourceForComponent)
    val className  = sourceFile.getName.stripSuffix(".scala")
    val packageDef = maybePackage.map(p => s"package $p").getOrElse("")
    val f          = new PrintWriter(sourceFile)
    f.println(s"""$packageDef
        |// This file contains an API for the $subsystemStr subsystem and was generated by the icd-db command.
        |// DO NOT EDIT.
        |// See https://github.com/tmtsoftware/icd for more information.
        |
        |import csw.params.core.generics.KeyType._
        |import csw.params.events._
        |import csw.prefix.models._
        |import csw.params.core.generics._
        |import csw.params.core.models._
        |import csw.params.core.models.Coords._
        |import csw.time.core.models._
        |
        |/**
        | * Top level API for subsystem: $subsystemStr
        | *
        | * ${subsystemModel.map(_.description).getOrElse("")}
        | */
        |object $className {
        |${defs.mkString("\n")}
        |}
        |""".stripMargin)
    f.close()
    try {
      s"scalafmt $sourceFile".!
    }
  }
}
