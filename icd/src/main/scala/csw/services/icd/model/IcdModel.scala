package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/icd-schema.conf
 */
object IcdModel {

  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): IcdModel =
    IcdModel(
      modelVersion = config.getString("modelVersion"),
      name = config.getString("name"),
      description = config.getString("description"),
      subsystem = config.getString("subsystem"),
      version = config.getInt("version"),
      wbsId = config.as[Option[String]]("wbsId").getOrElse(""))
}

case class IcdModel(modelVersion: String,
                    name: String,
                    description: String,
                    subsystem: String,
                    version: Int,
                    wbsId: String)

// ---

