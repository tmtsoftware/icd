package csw.services.icd.codegen.scala

import csw.services.icd.db.{CachedIcdDbQuery, CachedIcdVersionManager, ComponentInfoHelper, IcdDb, IcdVersionManager}
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, SubsystemWithVersion}

import java.io.{File, PrintWriter}

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
          case "Boolean" =>
            warning(s"Replacing unsupported 'Boolean' with 'Byte'")
            "Byte"
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
            if (arrayType == "String") {
              warning("Replacing unsupported 'StringArray' type with 'String' (can still have multiple values!)")
              s"""val `${p.name}Key`: Key[String] = StringKey.make("${p.name}")"""
            }
            else {
              val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
              if (isMatrix)
                s"""${arrayType}MatrixKey.make("${p.name}")"""
              else
                s"""${arrayType}ArrayKey.make("${p.name}")"""
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

  private def paramsForEvent(e: EventInfo): String = {
    val params = e.eventModel.parameterList.map { p =>
      s"""
         |/**
         | * ${p.description}
         | */
         |${paramDef(p)}
         |""".stripMargin
    }
    params.mkString("\n")
  }

  private def eventsDefs(e: EventInfo): String = {
    s"""
       |/**
       | * ${e.eventModel.description}
       | */
       |object `${e.eventModel.name}Event` {
       |    val eventKey: EventKey = EventKey(prefix, EventName("${e.eventModel.name}"))
       |
       |    ${paramsForEvent(e)}
       |}
       |""".stripMargin
  }

  private def sourceForComponent(info: ComponentInfo): String = {
    val comment =
      s"/** API for ${info.componentModel.componentType}: ${info.componentModel.subsystem}.${info.componentModel.component} */"
    val prefix = s"""val prefix: Prefix = Prefix(Subsystem.${info.componentModel.subsystem
      .toUpperCase()}, "${info.componentModel.component}")"""
    val eventKeys = info.publishes.toList.flatMap { p =>
      val events = p.eventList.map(eventsDefs)
      //  val currentStates = p.currentStateList.map(eventsDefs)
      events
    }
    s"""
       |$comment
       |object `${info.componentModel.component}` {
       |$prefix
       |
       |${eventKeys.mkString("\n")}
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
