package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.AttributeModel
import reactivemongo.api.bson._
/**
 * This model is a value that is based on the json-schema "ref": "resource:/json-schema.json".
 * In this case it can define a primitive type, enum, array, or object, for example.
 */
object AttributeModelBsonParser {

  private def bsonValueToString(b: BSONValue): String = b match {
    case s: BSONString  => s.value
    case d: BSONDouble  => d.value.toString
    case d: BSONInteger => d.value.toString
    case d: BSONLong    => d.value.toString
    case d: BSONBoolean => d.value.toString
    case x              => x.toString // should not happen
  }

  def apply(doc: BSONDocument): AttributeModel = {
    val name            = doc.getAsOpt[String]("name").getOrElse("")
    val description     = doc.getAsOpt[String]("description").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maybeType       = doc.getAsOpt[String]("type")
    val maybeEnum       = doc.getAsOpt[Array[String]]("enum").map(_.toList)
    val units           = doc.getAsOpt[String]("units").map(HtmlMarkup.gfmToHtml).getOrElse("")
    val maxItems        = doc.getAsOpt[Int]("maxItems")
    val minItems        = doc.getAsOpt[Int]("minItems")
    val maybeDimensions = doc.getAsOpt[Array[Int]]("dimensions").map(_.toList)
    val itemsDoc = doc.get("items").map(_.asInstanceOf[BSONDocument])
    val maybeArrayType  = itemsDoc.flatMap(_.get("type").map(bsonValueToString))

    // --- Old json-schema uses Boolean for exclusive* keys, new one uses Number! ---
    val exclusiveMinimumStr = doc.get("exclusiveMinimum").map(bsonValueToString)
        .orElse(itemsDoc.flatMap(_.get("exclusiveMinimum").map(bsonValueToString))).getOrElse("false")
    val exclusiveMinimum = exclusiveMinimumStr.toLowerCase() != "false"
    val exclusiveMaximumStr = doc.get("exclusiveMaximum").map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("exclusiveMaximum").map(bsonValueToString))).getOrElse("false")
    val exclusiveMaximum = exclusiveMaximumStr.toLowerCase() != "false"

    def isNumeric(str: String): Boolean        = str.matches("[-+]?\\d+(\\.\\d+)?")
    def ifNumeric(str: String): Option[String] = Some(str).filter(isNumeric)

    // For compatibility, use numeric value of exclusive min/max if found
    val minimum = doc
      .get("minimum").map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("minimum").map(bsonValueToString)))
      .orElse(ifNumeric(exclusiveMinimumStr))
    val maximum = doc
      .get("maximum").map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("maximum").map(bsonValueToString)))
      .orElse(ifNumeric(exclusiveMaximumStr))

    // ---

    val defaultValue = doc.getAsOpt[String]("default").getOrElse("")

    // Returns a string describing an array type
    def parseArrayTypeStr(doc: BSONDocument): String = {
      val items = doc.getAsOpt[BSONDocument]("items")
      val t     = items.flatMap(_.getAsOpt[String]("type"))
      val e     = items.flatMap(_.getAsOpt[Array[String]]("enum").map(_.toList))
      val s = if (t.isDefined) {
        parseTypeStr(items.get, t)
      } else if (e.isDefined) {
        "enum: (" + e.get.mkString(", ") + ")"
      } else "?"

      val maybeDimensions = doc.getAsOpt[Array[Int]]("dimensions").map(_.toList)
      if (maybeDimensions.isDefined)
        s"array[${maybeDimensions.get.mkString(",")}] of $s"
      else
        s"array of $s"
    }

    // Returns a string describing the given type or enum
    def parseTypeStr(doc: BSONDocument, opt: Option[String]): String = {
      opt match {
        case Some("integer") => numberTypeStr("integer")
        case Some("number")  => numberTypeStr("double")
        case Some("short")   => numberTypeStr("short")
        case Some("long")    => numberTypeStr("long")
        case Some("float")   => numberTypeStr("float")
        case Some("double")  => numberTypeStr("double")
        case Some("byte")    => numberTypeStr("byte")
        case Some("array")   => parseArrayTypeStr(doc)
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

    val typeStr = parseTypeStr(doc, doc.getAsOpt[String]("type"))

    // If type is "struct", attributeList gives the fields of the struct
    val attributesList = if (typeStr == "struct") {
      for (subDoc <- doc.getAsOpt[Array[BSONDocument]]("attributes").map(_.toList).getOrElse(Nil))
        yield AttributeModelBsonParser(subDoc)
    } else if (typeStr == "array of struct") {
      doc
        .getAsOpt[BSONDocument]("items")
        .toList
        .flatMap(
          items =>
            for (subDoc <- items.getAsOpt[Array[BSONDocument]]("attributes").map(_.toList).getOrElse(Nil))
              yield AttributeModelBsonParser(subDoc)
        )
    } else Nil

    AttributeModel(
      name,
      description,
      maybeType,
      maybeEnum,
      maybeArrayType,
      maybeDimensions,
      units,
      maxItems,
      minItems,
      minimum,
      maximum,
      exclusiveMinimum,
      exclusiveMaximum,
      defaultValue,
      typeStr,
      attributesList
    )
  }
}
