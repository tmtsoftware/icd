package csw.services.icd.model

import com.typesafe.config.Config
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AttributeModel

/**
 * This model is a value that is based on the json-schema "ref": "resource:/json-schema.json".
 * In this case it can define a primitive type, enum, array, or object, for example.
 */
object AttributeModelParser {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): AttributeModel = {
    val name = config.as[Option[String]]("name").getOrElse("")
    val description = config.as[Option[String]]("description").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maybeType = config.as[Option[String]]("type")
    val maybeEnum = config.as[Option[List[String]]]("enum")
    val units = config.as[Option[String]]("units").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maxItems = config.as[Option[String]]("maxItems")
    val minItems = config.as[Option[String]]("minItems")
    val minimum = config.as[Option[String]]("minimum").orElse(config.as[Option[String]]("items.minimum"))
    val maximum = config.as[Option[String]]("maximum").orElse(config.as[Option[String]]("items.maximum"))
    val exclusiveMinimum = config.as[Option[Boolean]]("exclusiveMinimum").orElse(config.as[Option[Boolean]]("items.exclusiveMinimum")).getOrElse(false)
    val exclusiveMaximum = config.as[Option[Boolean]]("exclusiveMaximum").orElse(config.as[Option[Boolean]]("items.exclusiveMaximum")).getOrElse(false)
    val defaultValue = if (config.hasPath("default")) config.getAnyRef("default").toString else ""

    def getPath(path: String, s: String): String = if (path.isEmpty) s else s"$path.$s"

    // Returns a string describing an array type
    def parseArrayTypeStr(itemPath: String, dimPath: String): String = {
      val dimsOpt = config.as[Option[List[String]]](getPath(dimPath, "dimensions"))
      val t = config.as[Option[String]](s"$itemPath.type")
      val e = config.as[Option[List[String]]](s"$itemPath.enum")
      val s = if (t.isDefined) {
        parseTypeStr(t, s"$itemPath.items", getPath(dimPath, "items"))
      } else if (e.isDefined) {
        "enum: (" + e.get.mkString(", ") + ")"
      } else "?"

      if (dimsOpt.isDefined)
        s"array[${dimsOpt.get.mkString(",")}] of $s"
      else
        s"array of $s"
    }

    // Returns a string describing the given type or enum
    def parseTypeStr(opt: Option[String], itemPath: String = "items", dimPath: String = ""): String = {
      opt match {
        case Some("array") => parseArrayTypeStr(itemPath, dimPath)
        case Some("integer") => numberTypeStr("integer")
        case Some("number") => numberTypeStr("double")
        case Some("short") => numberTypeStr("short")
        case Some("long") => numberTypeStr("long")
        case Some("float") => numberTypeStr("float")
        case Some("double") => numberTypeStr("double")
        case Some("byte") => numberTypeStr("byte")
        case Some(otherType) => otherType
        case None => maybeEnum match {
          case Some(list) => "enum: (" + list.mkString(", ") + ")"
          case None => ""
        }
      }
    }

    // Returns a string describing a numeric type t with optional range
    def numberTypeStr(t: String): String = {
      if (minimum.isDefined || maximum.isDefined) {
        val min = minimum.getOrElse("")
        val max = maximum.getOrElse("")
        val lt = if (minimum.isEmpty) "" else if (exclusiveMinimum) " < " else " ≤ "
        val gt = if (maximum.isEmpty) "" else if (exclusiveMaximum) " < " else " ≤ "
        s"$t ($min${lt}x$gt$max)"
      } else t
    }

    val typeStr = parseTypeStr(config.as[Option[String]]("type"))

    AttributeModel(name, description, maybeType, maybeEnum, units,
      maxItems, minItems, minimum, maximum, exclusiveMinimum, exclusiveMaximum,
      defaultValue, typeStr)
  }
}
