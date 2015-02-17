package csw.services.icd

import com.typesafe.config.Config


// ---

object IcdModel {
  def apply(config: Config): IcdModel = {
    val conf = config.getConfig("icd")
    IcdModel(
      modelVersion = conf.getString("modelVersion"),
      name = conf.getString("name"),
      subsystem = conf.getString("subsystem"),
      version = conf.getDouble("version"),
      wbsId = conf.getString("wbsId"))
  }
}

case class IcdModel(modelVersion: String,
                    name: String,
                    subsystem: String,
                    version: Double,
                    wbsId: String) extends IcdModelBase

// ---


