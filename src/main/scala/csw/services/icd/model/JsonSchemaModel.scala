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

  val defaultValue = if (config.hasPath("default")) config.getAnyRef("default").toString else ""

  // XXX TODO: Add number range, array bounds (make typeStr() recursive?)
  //        integer (-2 ≤ i ≤ 22)
  //        array of numbers (length = 5)
  //        String: ("red", "green", "blue")

  private def arrayTypeStr: String = {
    val t = config.as[Option[String]]("items.type")
    val e = config.as[Option[List[String]]]("items.enum")
    val s = if (t.isDefined) t.get else if (e.isDefined) e.get.mkString(", ") else "?"
    s"array of $s"
  }

  def typeStr: String = {
    if (typeOpt.isDefined) {
      typeOpt.get match {
        case "array" ⇒ arrayTypeStr
        case x       ⇒ x
      }
    } else if (enumOpt.isDefined) {
      "String: (" + enumOpt.get.mkString(", ") + ")"
    } else ""
  }
}
