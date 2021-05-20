package csw.services.icd.db.parser

import com.typesafe.config.Config
import icd.web.shared.IcdModels.IcdModel

object IcdModelParser {

  def apply(config: Config): IcdModel =
    IcdModel(
      subsystem = config.getString("subsystem"),
      targetSubsystem = config.getString("targetSubsystem"),
      title = config.getString("title"),
      description = config.getString("description"),
    )
}
