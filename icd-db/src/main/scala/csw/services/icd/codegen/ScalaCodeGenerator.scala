package csw.services.icd.codegen

import com.typesafe.config.ConfigFactory
import csw.services.icd.IcdValidator
import csw.services.icd.db._
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, ReceivedCommandInfo, SubsystemWithVersion}

import java.io.{File, PrintWriter}

object ScalaCodeGenerator {
  val allUnits: Set[String] = {
    import scala.jdk.CollectionConverters._
    val config = ConfigFactory.parseResources(s"${IcdValidator.currentSchemaVersion}/units.conf")
    config.getStringList("enum").asScala.toSet
  }
}

/**
 * Generates Scala code for a subsystem from the icd database.
 */
class ScalaCodeGenerator(db: IcdDb) {
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
        t.capitalize match {
          case "Number" =>
            warning(s"Replacing unsupported 'Number' with 'Long'")
            "Long"
          case "Object" =>
            warning("Replacing invalid type 'object' with 'string'")
            "String"
          case "Integer"             => "Int"
          case "TaiDate" | "TaiTime" => "TAITime"
          case "UtcDate" | "UtcTime" => "UTCTime"
          case x                     => x
        }
      case None => "Double" // XXX
    }
  }

  private val identChars0 = (('a' to 'z') ++ ('A' to 'Z') :+ '$' :+ '_').toSet
  private val identChars  = (identChars0.toList ++ ('0' to '9') :+ '$' :+ '_').toSet

  // Make sure s is a valid identifier, or wrap it in backticks
  private def makeIdent(s: String): String = {
    if (s.exists(c => !identChars.contains(c)) || !identChars0.contains(s.head))
      s"`$s`"
    else s
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
                s"""val $pNameKey: Key[String] = StringKey.make("${p.name}"$unitsArg)"""
              case "Boolean" =>
                warning("Replacing unsupported 'BooleanArray' type with 'Boolean' (can still have multiple values!)")
                s"""val $pNameKey: Key[Boolean] = BooleanKey.make("${p.name}"$unitsArg)"""
              case _ =>
                val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
                if (isMatrix)
                  s"""val $pNameKey: Key[MatrixData[$arrayType]] = ${arrayType}MatrixKey.make("${p.name}"$unitsArg)"""
                else
                  s"""val $pNameKey: Key[ArrayData[$arrayType]] = ${arrayType}ArrayKey.make("${p.name}"$unitsArg)"""
            }
          case t =>
            s"""val $pNameKey: Key[$t] = ${t}Key.make("${p.name}"$unitsArg)"""
        }
      case None =>
        p.maybeEnum match {
          case Some(e) =>
            val choices = e.map(choice => s""""$choice"""").mkString(", ")
            s"""val $pNameKey: GChoiceKey = ChoiceKey.make("${p.name}"$unitsArg, $choices)"""
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
       |object $className {
       |    val eventKey: EventKey = EventKey(prefix, EventName("${e.eventModel.name}"))
       |
       |    ${getParams(e.eventModel.parameterList)}
       |}
       |""".stripMargin
  }

  private def commandDefs(c: ReceivedCommandInfo): String = {
    val className = makeIdent(c.receiveCommandModel.name.capitalize + "Command")
    s"""
       |${makeComment(c.receiveCommandModel.description)}
       |object $className {
       |    val commandName: CommandName = CommandName("${c.receiveCommandModel.name}")
       |
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
    val className = makeIdent(info.componentModel.component.capitalize)
    s"""
       |$comment
       |object $className {
       |$prefix
       |
       |${eventKeys.mkString("\n")}
       |${commandKeys.mkString("\n")}
       |}
       |""".stripMargin
  }

  private def makeScalaFmtConf(): File = {
    val scalafmtConf = File.createTempFile("scalafmt", "conf")
    val f            = new PrintWriter(scalafmtConf)
    f.println("""
        |version = 3.3.2
        |runner.dialect = scala213source3
        |align.preset = more
        |docstrings.style = Asterisk
        |docstrings.wrap = yes
        |indentOperator.preset = spray
        |maxColumn = 130
        |newlines.alwaysBeforeElseAfterCurlyIf = true
        |""".stripMargin)
    f.close()
    scalafmtConf
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
    val infoList = new ComponentInfoHelper(false, false, maybeStaticHtml = Some(true))
      .getComponentInfoList(versionManager, sv, None, Map.empty)
    val defs       = infoList.map(sourceForComponent)
    val className  = sourceFile.getName.stripSuffix(".scala")
    val packageDef = maybePackage.map(p => s"package $p").getOrElse("")
    val f          = new PrintWriter(maybeFile.getOrElse(sourceFile))
    f.println(s"""$packageDef
        |// This file contains an API for the $subsystemStr subsystem and was generated by the icd-db command.
        |// DO NOT EDIT.
        |// See https://github.com/tmtsoftware/icd for more information.
        |
        |import csw.params.core.generics.KeyType._
        |import csw.params.commands.CommandName
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
    val scalafmtConf = makeScalaFmtConf()
    try {
      s"scalafmt -c $scalafmtConf ${maybeFile.getOrElse(sourceFile)}".!
    }
    catch {
      case _: Exception => println("Error: Scala formatting failed: Make sure you have 'scalafmt' installed.")
    }
    finally {
      scalafmtConf.delete()
    }
  }
}
