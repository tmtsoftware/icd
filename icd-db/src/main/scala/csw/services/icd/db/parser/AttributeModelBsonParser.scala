package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AttributeModel
import reactivemongo.bson.BSONDocument

/**
 * This model is a value that is based on the json-schema "ref": "resource:/json-schema.json".
 * In this case it can define a primitive type, enum, array, or object, for example.
 */
object AttributeModelBsonParser {

  def apply(doc: BSONDocument): AttributeModel = {
    val name        = doc.getAs[String]("name").getOrElse("")
    val description = doc.getAs[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maybeType   = doc.getAs[String]("type")
    val maybeEnum   = doc.getAs[Array[String]]("enum").map(_.toList)
    val units       = doc.getAs[String]("units").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maxItems    = doc.getAs[String]("maxItems")
    val minItems    = doc.getAs[String]("minItems")

    // --- Old json-schema uses Boolean for exclusive* keys, new one uses Number! ---
    val exclusiveMinimumStr =
      doc.getAs[String]("exclusiveMinimum").orElse(doc.getAs[String]("items.exclusiveMinimum")).getOrElse("false")
    val exclusiveMinimum = exclusiveMinimumStr.toLowerCase() != "false"
    val exclusiveMaximumStr =
      doc.getAs[String]("exclusiveMaximum").orElse(doc.getAs[String]("items.exclusiveMaximum")).getOrElse("false")
    val exclusiveMaximum = exclusiveMaximumStr.toLowerCase() != "false"

    def isNumeric(str: String): Boolean        = str.matches("[-+]?\\d+(\\.\\d+)?")
    def ifNumeric(str: String): Option[String] = Some(str).filter(isNumeric)

    // For compatibility, use numeric value of exclusive min/max if found
    val minimum = doc
      .getAs[String]("minimum")
      .orElse(doc.getAs[String]("items.minimum"))
      .orElse(ifNumeric(exclusiveMinimumStr))
    val maximum = doc
      .getAs[String]("maximum")
      .orElse(doc.getAs[String]("items.maximum"))
      .orElse(ifNumeric(exclusiveMaximumStr))

    // ---

    val defaultValue = doc.getAs[String]("default").getOrElse("")

    // Returns a string describing an array type
    def parseArrayTypeStr(): String = {
      val dimsOpt = doc.getAs[Array[Int]]("dimensions").map(_.toList)
      val items = doc.getAs[BSONDocument]("items")
      val t       = items.flatMap(_.getAs[String]("type"))
      val e       = items.flatMap(_.getAs[Array[String]]("enum").map(_.toList))
      val s = if (t.isDefined) {
        parseTypeStr(t)
      } else if (e.isDefined) {
        "enum: (" + e.get.mkString(", ") + ")"
      } else "?"

      if (dimsOpt.isDefined)
        s"array[${dimsOpt.get.mkString(",")}] of $s"
      else
        s"array of $s"
    }

    // Returns a string describing the given type or enum
    def parseTypeStr(opt: Option[String]): String = {
      opt match {
        case Some("array")   =>
          val x = parseArrayTypeStr()
          println(s"XXX x = $x")
          x
        case Some("integer") => numberTypeStr("integer")
        case Some("number")  => numberTypeStr("double")
        case Some("short")   => numberTypeStr("short")
        case Some("long")    => numberTypeStr("long")
        case Some("float")   => numberTypeStr("float")
        case Some("double")  => numberTypeStr("double")
        case Some("byte")    => numberTypeStr("byte")
        case Some(otherType) => otherType
        case None =>
          maybeEnum match {
            case Some(list) => "enum: (" + list.mkString(", ") + ")"
            case None       => ""
          }
      }
    }

    // Returns a string describing a numeric type t with optional range
    def numberTypeStr(t: String): String = {
      if (minimum.isDefined || maximum.isDefined) {
        val min = minimum.getOrElse("")
        val max = maximum.getOrElse("")
        val lt  = if (minimum.isEmpty) "" else if (exclusiveMinimum) " < " else " ≤ "
        val gt  = if (maximum.isEmpty) "" else if (exclusiveMaximum) " < " else " ≤ "
        s"$t ($min${lt}x$gt$max)"
      } else t
    }

    val typeStr = parseTypeStr(doc.getAs[String]("type"))

    AttributeModel(
      name,
      description,
      maybeType,
      maybeEnum,
      units,
      maxItems,
      minItems,
      minimum,
      maximum,
      exclusiveMinimum,
      exclusiveMaximum,
      defaultValue,
      typeStr
    )
  }
}
