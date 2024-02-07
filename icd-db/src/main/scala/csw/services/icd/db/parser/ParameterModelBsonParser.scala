package csw.services.icd.db.parser

import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.ParameterModel
import icd.web.shared.{FitsSource, EventParameterFitsKeyInfo, PdfOptions, SubsystemWithVersion}
import reactivemongo.api.bson.*

/**
 * This model is a value that is based on the json-schema "ref": "resource:/json-schema.json".
 * In this case it can define a primitive type, enum, array, or object, for example.
 */
object ParameterModelBsonParser {

  private def bsonValueToString(b: BSONValue): String =
    b match {
      case s: BSONString  => s.value
      case d: BSONDouble  => d.value.toString
      case d: BSONInteger => d.value.toString
      case d: BSONLong    => d.value.toString
      case d: BSONBoolean => d.value.toString
      case d: BSONArray   => s"[${d.values.map(bsonValueToString).mkString(", ")}]"
      case x              => x.toString // should not happen
    }

  private object EventParameterFitsKeyInfoParser {
    def apply(doc: BSONDocument): EventParameterFitsKeyInfo = {
      val name     = doc.getAsOpt[String]("keyword").get
      val channel  = doc.getAsOpt[String]("channel")
      val index    = doc.getAsOpt[Int]("index")
      val rowIndex = doc.getAsOpt[Int]("rowIndex")
      EventParameterFitsKeyInfo(name, channel, index, rowIndex)
    }
  }

  def apply(
      doc: BSONDocument,
      maybePdfOptions: Option[PdfOptions],
      fitsKeyMap: FitsKeyMap = Map.empty,
      maybeSv: Option[SubsystemWithVersion] = None,
      maybeEventName: Option[String] = None
  ): ParameterModel = {
    val name        = doc.getAsOpt[String]("name").getOrElse("")
    val ref         = doc.getAsOpt[String]("ref").getOrElse("")
    val description = doc.getAsOpt[String]("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse("")
    val maybeType   = doc.getAsOpt[String]("type")
    // Handle case where enum values include numbers and strings
    val maybeEnum       = doc.getAsOpt[Array[BSONValue]]("enum").map(_.toList.map(bsonValueToString))
    val units           = doc.getAsOpt[String]("units").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse("")
    val maxItems        = doc.getAsOpt[Int]("maxItems")
    val minItems        = doc.getAsOpt[Int]("minItems")
    val maxLength       = doc.getAsOpt[Int]("maxLength")
    val minLength       = doc.getAsOpt[Int]("minLength")
    val maybeDimensions = doc.getAsOpt[Array[Int]]("dimensions").map(_.toList)
    val itemsDoc        = doc.get("items").map(_.asInstanceOf[BSONDocument])
    val maybeArrayType  = itemsDoc.flatMap(_.get("type").map(bsonValueToString))

    // FITS keyword(s) from publish-model.conf
    val maybeKeyword = doc.getAsOpt[String]("keyword")
    val maybeChannel = doc.getAsOpt[String]("channel")
    val keywords0    = maybeKeyword.toList.map(EventParameterFitsKeyInfo(_, maybeChannel))
    def getItems[A](name: String, f: BSONDocument => A): List[A] =
      for (subDoc <- doc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)
    val keywords = keywords0 ::: getItems("keywords", EventParameterFitsKeyInfoParser(_))

    // --- Old json-schema uses Boolean for exclusive* keys, new one uses Number! ---
    val exclusiveMinimumStr = doc
      .get("exclusiveMinimum")
      .map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("exclusiveMinimum").map(bsonValueToString)))
      .getOrElse("false")
    val exclusiveMinimum = exclusiveMinimumStr.toLowerCase() != "false"
    val exclusiveMaximumStr = doc
      .get("exclusiveMaximum")
      .map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("exclusiveMaximum").map(bsonValueToString)))
      .getOrElse("false")
    val exclusiveMaximum = exclusiveMaximumStr.toLowerCase() != "false"
    val allowNaN         = doc.getAsOpt[Boolean]("allowNaN").getOrElse(false)

    def isNumeric(str: String): Boolean        = str.matches("([-+]?\\d+(\\.\\d+)?|[-]?[Ii]nf)")
    def ifNumeric(str: String): Option[String] = Some(str).filter(isNumeric)

    // For compatibility, use numeric value of exclusive min/max if found
    val minimum = doc
      .get("minimum")
      .map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("minimum").map(bsonValueToString)))
      .orElse(ifNumeric(exclusiveMinimumStr))
    val maximum = doc
      .get("maximum")
      .map(bsonValueToString)
      .orElse(itemsDoc.flatMap(_.get("maximum").map(bsonValueToString)))
      .orElse(ifNumeric(exclusiveMaximumStr))

    // ---

    val defaultValue = doc.get("default").map(bsonValueToString).getOrElse("")

    // Returns "string" and includes the min/max length, if specified, in brackets
    def makeStringTypeStr(): String = {
      (minLength, maxLength) match {
        case (None, None)      => "string"
        case (None, Some(max)) => s"string[?..$max]"
        case (Some(min), None) => s"string[$min..?]"
        case (Some(min), Some(max)) =>
          if (min == max) s"string[$max]"
          else s"string[$min..$max]"
      }
    }

    // Returns a string describing an array type
    def parseArrayTypeStr(doc: BSONDocument): String = {
      val items = doc.getAsOpt[BSONDocument]("items")
      val t     = items.flatMap(_.getAsOpt[String]("type"))
      val e     = items.flatMap(_.getAsOpt[Array[String]]("enum").map(_.toList))
      val s = if (t.isDefined) {
        parseTypeStr(items.get, t)
      }
      else if (e.isDefined) {
        "enum: (" + e.get.mkString(", ") + ")"
      }
      else "?"

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
        case Some("string")  => makeStringTypeStr()
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
        val nan = if (allowNaN) ", or NaN" else ""
        s"$t ($min${lt}x$gt$max$nan)"
      }
      else t
    }

    val typeStr = parseTypeStr(doc, doc.getAsOpt[String]("type"))

    // only need FITS keys for events
    val fitsKeys =
      if (fitsKeyMap.nonEmpty && maybeEventName.isDefined && maybeSv.isDefined) {
        val sv = maybeSv.get
        fitsKeyMap.getOrElse(FitsSource(sv.subsystem, sv.maybeComponent.get, maybeEventName.get, name, None, None), Nil)
      }
      else Nil

    ParameterModel(
      name,
      ref,
      "",
      description,
      maybeType,
      maybeEnum,
      maybeArrayType,
      maybeDimensions,
      units,
      maxItems,
      minItems,
      maxLength,
      minLength,
      minimum,
      maximum,
      exclusiveMinimum,
      exclusiveMaximum,
      allowNaN,
      defaultValue,
      typeStr,
      keywords,
      fitsKeys
    )
  }
}
