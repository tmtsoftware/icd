package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/icd-schema.conf
 */
object IcdModel {
  import net.ceedubs.ficus.Ficus._

  def apply(config: Config): IcdModel = {
    IcdModel(
      modelVersion = config.getString("modelVersion"),
      description = config.getString("description"),
      name = config.getString("name"),
      subsystem = config.getString("subsystem"),
      version = config.getInt("version"),
      wbsId = config.as[Option[String]]("wbsId").getOrElse(""))

  }
}

case class IcdModel(modelVersion: String,
                    name: String,
                    description: String,
                    subsystem: String,
                    version: Int,
                    wbsId: String) extends IcdModelBase

// ---

