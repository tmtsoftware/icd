package csw.services.icd.codegen

import com.typesafe.config.ConfigFactory
import csw.services.icd.IcdValidator
import csw.services.icd.db._
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, ReceivedCommandInfo, SubsystemWithVersion}

import java.io.{File, PrintWriter}

object TypescriptCodeGenerator {
  val allUnits: Set[String] = {
    import scala.jdk.CollectionConverters._
    val config = ConfigFactory.parseResources(s"${IcdValidator.currentSchemaVersion}/units.conf")
    config.getStringList("enum").asScala.toSet
  }
}

/**
 * Generates Scala code for a subsystem from the icd database.
 */
class TypescriptCodeGenerator(db: IcdDb) {
  import ScalaCodeGenerator._

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
        t match {
          case "number" =>
            warning(s"Replacing unsupported 'number' with 'long'")
            "long"
          case "object" =>
            warning("Replacing invalid type 'object' with 'string'")
            "string"
          case "integer"             => "int"
          case "taiDate" | "taiTime" => "taiTime"
          case "utcDate" | "utcTime" => "utcTime"
          case x                     => x
        }
      case None => "double" // XXX
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
          case "array" =>
            val arrayType = getParamType(p.maybeArrayType)
            arrayType match {
              case "string" =>
                warning("Replacing unsupported 'stringArray' type with 'string' (can still have multiple values!)")
                s"""export const $pNameKey = stringKey("${p.name}"$unitsArg)"""
              case "boolean" =>
                warning("Replacing unsupported 'BooleanArray' type with 'Boolean' (can still have multiple values!)")
                s"""export const $pNameKey = booleanKey("${p.name}"$unitsArg)"""
              case _ =>
                val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
                if (isMatrix)
                  s"""export const $pNameKey = ${arrayType}MatrixKey("${p.name}"$unitsArg)"""
                else
                  s"""export const $pNameKey = ${arrayType}ArrayKey("${p.name}"$unitsArg)"""
            }
          case t =>
            s"""export const $pNameKey = ${t}Key("${p.name}"$unitsArg)"""
        }
      case None =>
        p.maybeEnum match {
          case Some(e) =>
            val choices = e.map(choice => s"'$choice'").mkString(", ")
            s"""export const ${pNameKey}Choices = [$choices] as const
               |export type ${pNameKey}ChoicesT = typeof ${pNameKey}Choices[number]
               |export const $pNameKey = choiceKey<${pNameKey}ChoicesT>("${p.name}", ${pNameKey}Choices$unitsArg)""".stripMargin
          case None =>
            ""
        }
    }
  }

  private def makeComment(s: String): String = {
    val comment = if (s.startsWith("<p>") && s.endsWith("</p>")) s.drop(3).dropRight(4) else s
    if (comment.isEmpty) comment else s"/** $comment */"
  }

  private def getParams(paramList: List[ParameterModel]): String = {
    val params = paramList.map { p =>
      s"""
         |${makeComment(p.description)}
         |${paramDef(p)}
         |""".stripMargin
    }
    params.mkString("\n")
  }

  private def eventsDefs(e: EventInfo, eventType: String): String = {
    val className = makeIdent(e.eventModel.name.capitalize + eventType)
    s"""
       |${makeComment(e.eventModel.description)}
       |export namespace $className {
       |    export const eventKey: EventKey = new EventKey(prefix, new EventName("${e.eventModel.name}"))
       |
       |    ${getParams(e.eventModel.parameterList)}
       |}
       |""".stripMargin
  }

  private def commandDefs(c: ReceivedCommandInfo): String = {
    val className = makeIdent(c.receiveCommandModel.name.capitalize + "Command")
    s"""
       |${makeComment(c.receiveCommandModel.description)}
       |export namespace $className {
       |    export const commandName = "${c.receiveCommandModel.name}"
       |
       |    ${getParams(c.receiveCommandModel.parameters)}
       |}
       |""".stripMargin
  }

  private def sourceForComponent(info: ComponentInfo): String = {
    val comment =
      s"/** API for ${info.componentModel.componentType}: ${info.componentModel.subsystem}.${info.componentModel.component} */"
    val prefix = s"""export const prefix: Prefix = new Prefix('${info.componentModel.subsystem
      .toUpperCase()
      .replace("TEST2", "CSW")
      .replace("TEST", "CSW")}', "${info.componentModel.component}")"""
    val eventKeys = info.publishes.toList.flatMap { p =>
      val events        = p.eventList.map(e => eventsDefs(e, "Event"))
      val currentStates = p.currentStateList.map(e => eventsDefs(e, "CurrentState"))
      events ::: currentStates
    }
    val commandKeys = info.commands.toList.flatMap { commands =>
      commands.commandsReceived.map(c => commandDefs(c))
    }
    val className = makeIdent(info.componentModel.component.capitalize)
    s"""
       |$comment
       |export namespace $className {
       |$prefix
       |
       |${eventKeys.mkString("\n")}
       |${commandKeys.mkString("\n")}
       |}
       |""".stripMargin
  }

  private def makePrettierConf(): File = {
    val prettierConf = File.createTempFile("prettier", "conf")
    val f            = new PrintWriter(prettierConf)
    f.println("""
        |{
        |  "printWidth": 120,
        |  "tabWidth": 2,
        |  "useTabs": false,
        |  "semi": false,
        |  "singleQuote": true,
        |  "jsxSingleQuote": true,
        |  "trailingComma": "none",
        |  "bracketSpacing": true,
        |  "bracketSameLine": true,
        |  "proseWrap": "always"
        |}
        |""".stripMargin)
    f.close()
    prettierConf
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
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem)), None, Map.empty)
    val versionManager = new CachedIcdVersionManager(query)
    val subsystemModel = db.versionManager.getSubsystemModel(sv, None)
    val infoList = new ComponentInfoHelper(versionManager, false, false)
      .getComponentInfoList(sv, None, Map.empty)
    val defs       = infoList.map(sourceForComponent)
    val className  = sourceFile.getName.stripSuffix(".ts")
    val f          = new PrintWriter(maybeFile.getOrElse(sourceFile))
    f.println(s"""// This file contains an API for the $subsystemStr subsystem and was generated by the icd-db command.
                 |// DO NOT EDIT.
                 |// See https://github.com/tmtsoftware/icd for more information.
                 |// noinspection JSUnusedLocalSymbols,ES6UnusedImports,JSUnusedGlobalSymbols
                 |
                 |import {
                 |  AltAzCoord,
                 |  altAzCoordKey, Angle,
                 |  BaseKeyType,
                 |  booleanKey, byteArrayKey,
                 |  byteKey, byteMatrixKey, choiceKey, CometCoord, cometCoordKey, coordKey, doubleArrayKey,
                 |  doubleKey, doubleMatrixKey, EqCoord, eqCoordKey, floatArrayKey,
                 |  floatKey, floatMatrixKey, intArrayKey,
                 |  intKey, intMatrixKey,
                 |  Key, longArrayKey,
                 |  longKey, longMatrixKey, MinorPlanetCoord, minorPlanetCoordKey, shortArrayKey,
                 |  shortKey, shortMatrixKey, SolarSystemCoord, solarSystemCoordKey,
                 |  stringKey, SystemEvent, taiTimeKey, Units, utcTimeKey,
                 |  EventKey, EventName, Prefix
                 |} from '@tmtsoftware/esw-ts'
                 |
                 |/**
                 | * Top level API for subsystem: $subsystemStr
                 | *
                 | * ${subsystemModel.map(_.description).getOrElse("")}
                 | */
                 |export namespace $className {
                 |${defs.mkString("\n")}
                 |}
                 |""".stripMargin)
    f.close()
    val prettierConf = makePrettierConf()
    try {
      s"prettier -w --config $prettierConf ${maybeFile.getOrElse(sourceFile)}".!
    }
    catch {
      case _: Exception => println("Error: Typescript formatting failed: Make sure you have 'prettier' installed (npm install -g prettier).")
    }
    finally {
      prettierConf.delete()
    }
  }
}
