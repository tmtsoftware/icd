package csw.services.icd.model

import com.typesafe.config.Config

/**
 * This model is a value that is based on the json-schema {"$ref": "resource:/json-schema.json"}.
 * In this case it can define a primitive type, enum, array, or object, for example.
 * Since we don't know ahead of time what the format is, this class
 * just contains the raw config object.
 */
case class JsonSchemaModel(config: Config) extends IcdModelBase {
  import net.ceedubs.ficus.Ficus._
  val name = config.as[Option[String]]("name").getOrElse("")
  val description = config.as[Option[String]]("description").getOrElse("")
  val typeOpt = config.as[Option[String]]("type")
  val enumOpt = config.as[Option[List[String]]]("enum")
}
