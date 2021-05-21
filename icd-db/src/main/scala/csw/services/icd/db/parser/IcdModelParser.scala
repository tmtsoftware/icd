package csw.services.icd.db.parser

import com.typesafe.config.Config
import icd.web.shared.IcdModels.IcdModel

object IcdModelParser {

  def apply(config: Config): IcdModel = {
    val subsystem       = config.getString("subsystem")
    val targetSubsystem = config.getString("targetSubsystem")
    val title =
      if (config.hasPath("title"))
        config.getString("title")
      else s"Interface between $subsystem and $targetSubsystem"
    IcdModel(
      subsystem = subsystem,
      targetSubsystem = targetSubsystem,
      title = title,
      description = config.getString("description")
    )
  }
}
