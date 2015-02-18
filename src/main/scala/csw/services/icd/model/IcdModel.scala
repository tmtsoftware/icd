package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/icd-schema.conf
 */
object IcdModel {
  def apply(config: Config): IcdModel = {
    val conf = config.getConfig("icd")
    IcdModel(
      modelVersion = conf.getString("modelVersion"),
      name = conf.getString("name"),
      subsystem = conf.getString("subsystem"),
      version = conf.getInt("version"),
      wbsId = conf.getString("wbsId"))
  }
}

case class IcdModel(modelVersion: String,
                    name: String,
                    subsystem: String,
                    version: Int,
                    wbsId: String) extends IcdModelBase

// ---

