package csw.services.icd.codegen

import com.typesafe.config.ConfigFactory
import csw.services.icd.IcdValidator
import csw.services.icd.db.*
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{ComponentInfo, EventInfo, ReceivedCommandInfo, SubsystemWithVersion}

import java.io.{File, PrintWriter}

object JavaCodeGenerator {
  val allUnits: Set[String] = {
    import scala.jdk.CollectionConverters.*
    val config = ConfigFactory.parseResources(s"${IcdValidator.currentSchemaVersion}/units.conf")
    config.getStringList("enum").asScala.toSet
  }
}

/**
 * Generates Java code for a subsystem from the icd database.
 */
class JavaCodeGenerator(db: IcdDb) {
  import JavaCodeGenerator.*

  private def warning(s: String): Unit = {
    println(s"Warning: $s")
  }

  private def getUnits(units: String): Option[String] = {
    val unit = units.replace("<p>", "").replace("</p>", "")
    if (allUnits.contains(unit))
      Some(s"JUnits.$unit")
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
          case "TaiDate" | "TaiTime" => "TAITime"
          case "UtcDate" | "UtcTime" => "UTCTime"
          case x                     => x
        }
      case None => "Double" // XXX
    }
  }

  private val identChars0 = (('a' to 'z') ++ ('A' to 'Z') :+ '$' :+ '_').toSet
  private val identChars  = (identChars0.toList ++ ('0' to '9') :+ '$' :+ '_').toSet

  // Make sure s is a valid identifier, or replace any invalid chars with '_'
  private def makeIdent(s: String): String = {
    val x = s.map(c => if (identChars.contains(c)) c else '_')
    if (identChars0.contains(x.head)) x else s"_${x.drop(1)}"
  }

  private def keyType(s: String) = {
    s match {
      case "Integer" => "Int"
      case x         => x
    }
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
                s"""public static final Key<String> $pNameKey = JKeyType.StringKey().make("${p.name}"$unitsArg);"""
              case "Boolean" =>
                warning("Replacing unsupported 'BooleanArray' type with 'Boolean' (can still have multiple values!)")
                s"""public static final Key<Boolean> $pNameKey = JKeyType.BooleanKey().make("${p.name}"$unitsArg);"""
              case _ =>
                val isMatrix = p.maybeDimensions.exists(d => d.size == 2)
                if (isMatrix)
                  s"""public static final Key<MatrixData<$arrayType>> $pNameKey = JKeyType.${keyType(
                    arrayType
                  )}MatrixKey().make("${p.name}"$unitsArg);"""
                else
                  s"""public static final Key<ArrayData<$arrayType>> $pNameKey = JKeyType.${keyType(
                    arrayType
                  )}ArrayKey().make("${p.name}"$unitsArg);"""
            }
          case t =>
            s"""public static final Key<$t> $pNameKey = JKeyType.${keyType(t)}Key().make("${p.name}"$unitsArg);"""
        }
      case None =>
        p.maybeEnum match {
          case Some(e) =>
            val choices = e.map(choice => s"""new Choice("$choice")""").mkString(", ")
            s"""public static final GChoiceKey $pNameKey = JKeyType.ChoiceKey().make("${p.name}"$unitsArg, Choices.fromChoices($choices));"""
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
       |public static final class $className {
       |    public static final EventKey eventKey = new EventKey(prefix, new EventName("${e.eventModel.name}"));
       |
       |    ${getParams(e.eventModel.parameterList)}
       |}
       |""".stripMargin
  }

  private def commandDefs(c: ReceivedCommandInfo): String = {
    val className = makeIdent(c.receiveCommandModel.name.capitalize + "Command")
    s"""
       |${makeComment(c.receiveCommandModel.description)}
       |public static final class $className {
       |    public static final CommandName commandName = new CommandName("${c.receiveCommandModel.name}");
       |    ${getParams(c.receiveCommandModel.parameters)}
       |}
       |""".stripMargin
  }

  private def sourceForComponent(info: ComponentInfo): String = {
    val comment =
      s"/** API for ${info.componentModel.componentType}: ${info.componentModel.subsystem}.${info.componentModel.component} */"
    val prefix = s"""public static final Prefix prefix = new Prefix(JSubsystem.${info.componentModel.subsystem
      .toUpperCase()
      .replace("TEST2", "CSW")
      .replace("TEST", "CSW")}, "${info.componentModel.component}");"""
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
       |public static final class $className {
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
    import sys.process.*
    val s              = IcdVersionManager.SubsystemAndVersion(subsystemStr)
    val versionDef     = s"public static final String subsystem = \"$s\";"
    val sv             = SubsystemWithVersion(s.subsystem, s.maybeVersion, maybeComponent)
    val query          = new CachedIcdDbQuery(db.db, db.admin, Some(List(sv.subsystem)), None, Map.empty)
    val versionManager = new CachedIcdVersionManager(query)
    val subsystemModel = db.versionManager.getSubsystemModel(sv, None)
    val infoList = new ComponentInfoHelper(versionManager, false, false)
      .getComponentInfoList(sv, None, Map.empty)
    val defs       = infoList.map(sourceForComponent)
    val className  = sourceFile.getName.stripSuffix(".java")
    val packageDef = maybePackage.map(p => s"package $p;").getOrElse("")
    val f          = new PrintWriter(maybeFile.getOrElse(sourceFile))
    f.println(s"""$packageDef
                 |// This file contains the API for $s and was generated by the icd-db command.
                 |// DO NOT EDIT.
                 |// See https://github.com/tmtsoftware/icd for more information.
                 |
                 |import csw.params.commands.CommandName;
                 |import csw.params.core.generics.*;
                 |import csw.params.core.models.*;
                 |import csw.params.core.models.Coords.*;
                 |import csw.params.events.*;
                 |import csw.params.javadsl.JKeyType;
                 |import csw.params.javadsl.JUnits;
                 |import csw.prefix.javadsl.JSubsystem;
                 |import csw.prefix.models.*;
                 |import csw.time.core.models.*;
                 |
                 |/**
                 | * Top level API for: $s
                 | *
                 | * ${subsystemModel.map(_.description).getOrElse("")}
                 | */
                 |@SuppressWarnings("unused")
                 |public class $className {
                 |$versionDef
                 |${defs.mkString("\n")}
                 |}
                 |""".stripMargin)
    f.close()
    try {
      s"google-java-format -i ${maybeFile.getOrElse(sourceFile)}".!
    }
    catch {
      case _: Exception =>
        println(
          "Error: Java formatting failed: Make sure you have 'google-java-format' installed (Try: cs install --contrib google-java-format)."
        )
    }
  }
}
