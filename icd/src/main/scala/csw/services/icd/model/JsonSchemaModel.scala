package csw.services.icd.model

import com.typesafe.config.Config

/**
 * This model is a value that is based on the json-schema "ref": "resource:/json-schema.json".
 * In this case it can define a primitive type, enum, array, or object, for example.
 * Since we don't know ahead of time what the format is, this class
 * just contains the raw config object.
 */
case class JsonSchemaModel(config: Config) {

  import net.ceedubs.ficus.Ficus._

  val name = config.as[Option[String]]("name").getOrElse("")
  val description = config.as[Option[String]]("description").getOrElse("")
  val typeOpt = config.as[Option[String]]("type")
  val enumOpt = config.as[Option[List[String]]]("enum")
  val units = config.as[Option[String]]("units").getOrElse("")

  val minimum = config.as[Option[String]]("minimum")
  val maximum = config.as[Option[String]]("maximum")
  val exclusiveMinimum = config.as[Option[Boolean]]("exclusiveMinimum").getOrElse(false)
  val exclusiveMaximum = config.as[Option[Boolean]]("exclusiveMaximum").getOrElse(false)

  val defaultValue = if (config.hasPath("default")) config.getAnyRef("default").toString else ""

  // XXX TODO: Add number range, array bounds (make typeStr() recursive?)
  //        integer (-2 ≤ i ≤ 22)
  //        array of numbers (length = 5)
  //        String: ("red", "green", "blue")

  // Returns a string describing an array type
  private def arrayTypeStr: String = {
    val t = config.as[Option[String]]("items.type")
    val e = config.as[Option[List[String]]]("items.enum")
    val s = if (t.isDefined) t.get else if (e.isDefined) e.get.mkString(", ") else "?"
    s"array of $s"
  }

  // Returns a string describing a type or enum
  def typeStr: String = {
    typeOpt match {
      case Some("array")   ⇒ arrayTypeStr
      case Some("integer") ⇒ numberTypeStr("integer")
      case Some("number")  ⇒ numberTypeStr("number")
      case Some(otherType) ⇒ otherType
      case None ⇒ enumOpt match {
        case Some(list) ⇒ "enum: (" + list.mkString(", ") + ")"
        case None       ⇒ ""
      }
    }
  }

  // Returns a string describing a numeric type t with optional range
  def numberTypeStr(t: String): String = {
    if (minimum.isDefined || maximum.isDefined) {
      // include range with () or []
      val infinity = "inf" // java and html escape sequences get lost in conversion...
      val min = minimum.getOrElse(infinity)
      val max = maximum.getOrElse(infinity)
      val exMin = if (exclusiveMinimum) "(" else "["
      val exMax = if (exclusiveMaximum) ")" else "]"
      s"$t $exMin$min, $max$exMax"
    } else t
  }
}
