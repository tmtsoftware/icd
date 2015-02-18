package csw.services.icd.model

import com.typesafe.config.Config

/**
 * This model is a value that is based on the json-schema {"$ref": "resource:/json-schema.json"}.
 * In this case it can define a primitive type, enum, array, or object, for example.
 * Since we don't know ahead of time what the format is, this class
 * just contains the raw config object.
 */
case class JsonSchemaModel(config: Config) extends IcdModelBase {

}
