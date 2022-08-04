package csw.services.icd.codegen

import com.typesafe.config.ConfigFactory
import csw.services.icd.IcdValidator
import csw.services.icd.db._
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, ReceivedCommandInfo, SubsystemWithVersion}
import org.apache.commons.text.WordUtils

import java.io.{File, PrintWriter}

object PythonCodeGenerator {
  val allUnits: Set[String] = {
    import scala.jdk.CollectionConverters._
    val config = ConfigFactory.parseResources(s"${IcdValidator.currentSchemaVersion}/units.conf")
    config.getStringList("enum").asScala.toSet
  }
}

/**
 * Generates Scala code for a subsystem from the icd database.
 */
class PythonCodeGenerator(db: IcdDb) {
  import ScalaCodeGenerator._
  private val indent  = "    "
  private val indent2 = indent * 2
  private val indent3 = indent * 3

  private def warning(s: String): Unit = {
    println(s"Warning: $s")
  }

  private def getUnits(units: String): Option[String] = {
    val unit = units.replace("<p>", "").replace("</p>", "")
    if (allUnits.contains(unit))
      Some(s"Units.$unit")
    else None
  }

  private def getParamType(maybeType: Option[String]): String = {
    maybeType match {
      case Some(t) =>
        t.capitalize match {
          case "Number" =>
            warning(s"Replacing unsupported 'number' with 'int'")
            "Int"
          case "Object" =>
            warning("Replacing invalid type 'object' with 'str'")
            "String"
          case "Integer"             => "Int"
          case "Float"               => "Float"
          case "Double"              => "Float"
          case "TaiDate" | "TaiTime" => "TAITime"
          case "UtcDate" | "UtcTime" => "UTCTime"
          case x                     => x
        }
      case None => "Float" // XXX
    }
  }

  private val identChars0 = (('a' to 'z') ++ ('A' to 'Z') :+ '$' :+ '_').toSet
  private val identChars  = (identChars0.toList ++ ('0' to '9') :+ '$' :+ '_').toSet

  // Make sure s is a valid identifier, or replace any invalid chars with '_'
  private def makeIdent(s: String): String = {
    val x = s.map(c => if (identChars.contains(c)) c else '_')
    if (identChars0.contains(x.head)) x else s"_${x.drop(1)}"
  }

  private def paramDef(p: ParameterModel): String = {
    val unitsArg = getUnits(p.units).map(u => s", $u").getOrElse("")
    val pNameKey = makeIdent(p.name + "Key")
    p.maybeType match {
      case Some(t) =>
        val paramType = getParamType(Some(t))
        paramType match {
          case "Array" =>
            val arrayType = getParamType(p.maybeArrayType)
            arrayType match {
              case "String" =>
                warning("Replacing unsupported 'StringArray' type with 'String' (can still have multiple values!)")
                s"""$indent3$pNameKey = StringKey.make("${p.name}"$unitsArg)"""
              case "Boolean" =>
                warning("Replacing unsupported 'BooleanArray' type with 'Boolean' (can still have multiple values!)")
                s"""$indent3$pNameKey = BooleanKey.make('${p.name}'$unitsArg)"""
              case _ =>
                val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
                if (isMatrix)
                  s"""$indent3$pNameKey = ${arrayType}MatrixKey.make('${p.name}'$unitsArg)"""
                else
                  s"""$indent3$pNameKey = ${arrayType}ArrayKey.make('${p.name}'$unitsArg)"""
            }
          case t =>
            s"""$indent3$pNameKey = ${t}Key.make('${p.name}'$unitsArg)"""
        }
      case None =>
        p.maybeEnum match {
          case Some(e) =>
            val choices = e.map(choice => s"'$choice'").mkString(", ")
            s"""$indent3$pNameKey = ChoiceKey.make('${p.name}', [$choices])"""
          case None =>
            ""
        }
    }
  }

  private def makeComment(s: String, indent: String): String = {
    val comment = if (!s.contains("\n") && s.startsWith("<p>") && s.endsWith("</p>")) s.drop(3).dropRight(4) else s
    if (comment.isEmpty)
      comment
    else if (comment.contains("<img src=")) ""
    else
      WordUtils.wrap(comment, 100).split("\n").map(c => s"$indent# $c").mkString("\n")
  }

  private def getParams(paramList: List[ParameterModel]): String = {
    val params = paramList.map { p =>
      s"""${makeComment(p.description, indent3)}
         |${paramDef(p)}
         |""".stripMargin
    }
    params.mkString("\n")
  }

  private def eventsDefs(e: EventInfo, eventType: String, prefixArg: String): String = {
    val className = makeIdent(e.eventModel.name.capitalize + eventType)
    s"""${makeComment(e.eventModel.description, indent2)}
       |${indent2}class $className:
       |${indent3}eventKey = EventKey($prefixArg, EventName('${e.eventModel.name}'))
       |
       |${getParams(e.eventModel.parameterList)}
       |""".stripMargin
  }

  private def commandDefs(c: ReceivedCommandInfo): String = {
    val className = makeIdent(c.receiveCommandModel.name.capitalize + "Command")
    s"""${makeComment(c.receiveCommandModel.description, indent2)}
       |${indent2}class $className:
       |${indent3}commandName = '${c.receiveCommandModel.name}'
       |${getParams(c.receiveCommandModel.parameters)}
       |""".stripMargin
  }

  private def sourceForComponent(info: ComponentInfo): String = {
    val indent2 = indent * 2
    val comment =
      s"$indent# API for ${info.componentModel.componentType}: ${info.componentModel.subsystem}.${info.componentModel.component}"
    val prefixArg = s"""Prefix(Subsystems.${info.componentModel.subsystem
      .toUpperCase()
      .replace("TEST2", "CSW")
      .replace("TEST", "CSW")},
      '${info.componentModel.component.replace("-", "_")}')"""
    val prefix = s"${indent2}prefix = ${prefixArg}"
    val eventKeys = info.publishes.toList.flatMap { p =>
      val events        = p.eventList.map(e => eventsDefs(e, "Event", prefixArg))
      val currentStates = p.currentStateList.map(e => eventsDefs(e, "CurrentState", prefixArg))
      events ::: currentStates
    }
    val commandKeys = info.commands.toList.flatMap { commands =>
      commands.commandsReceived.map(c => commandDefs(c))
    }
    val className = makeIdent(info.componentModel.component.capitalize)
    s"""$comment
       |${indent}class $className:
       |$prefix
       |
       |${eventKeys.mkString("\n")}
       |${commandKeys.mkString("\n")}
       |""".stripMargin
  }

  /**
   * Generates a source file with the API for the given subsystem / component (or all components)
   * @param subsystemStr a string containing the subsystem, possibly followed by a ':' and the version
   * @param maybeComponent optional component name (default: all subsystem components)
   * @param sourceFile generate code in this file
   * @param maybeFile write to this file if given and use sourceFile for the name and suffix (used for temp files)
   * @param maybePackage optional package name for Scala and Java files
   */
  def generate(
      subsystemStr: String,
      maybeComponent: Option[String],
      sourceFile: File,
      maybeFile: Option[File],
      maybePackage: Option[String]
  ): Unit = {
    import sys.process._
    val s              = IcdVersionManager.SubsystemAndVersion(subsystemStr)
    val sv             = SubsystemWithVersion(s.subsystem, s.maybeVersion, maybeComponent)
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem)), None)
    val versionManager = new CachedIcdVersionManager(query)
    val subsystemModel = db.versionManager.getSubsystemModel(sv, None)
    val infoList = new ComponentInfoHelper(false, false, maybeStaticHtml = Some(true))
      .getComponentInfoList(versionManager, sv, None)
    val defs      = infoList.map(sourceForComponent)
    val className = sourceFile.getName.stripSuffix(".py")
    val comment   = makeComment(subsystemModel.map(_.description).getOrElse(""), "")
    val f         = new PrintWriter(maybeFile.getOrElse(sourceFile))
    f.println(s"""# This file contains an API for the $subsystemStr subsystem and was generated by the icd-db command.
                 |# DO NOT EDIT.
                 |# See https://github.com/tmtsoftware/icd for more information.
                 |# noinspection JSUnusedLocalSymbols,ES6UnusedImports,JSUnusedGlobalSymbols
                 |
                 |from csw.ParameterSetType import *
                 |from csw.Prefix import Prefix
                 |from csw.Parameter import *
                 |from csw.Event import EventName
                 |from csw.Subsystem import Subsystems
                 |from csw.EventKey import EventKey
                 |from csw.Units import Units
                 |from csw.Coords import *
                 |
                 |# --- Top level API for subsystem: $subsystemStr ---
                 |
                 |$comment
                 |class $className:
                 |${defs.mkString("\n")}
                 |""".stripMargin)
    f.close()
    try {
      s"black ${maybeFile.getOrElse(sourceFile)}".!
    }
    catch {
      case ex: Exception => println("Warning: Python formatting failed: Make sure you have 'black' installed.")
    }

  }
}
