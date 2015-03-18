package csw.services.icd.model

import com.typesafe.config.Config

/**
 * See resources/publish-schema.conf
 */
object EventStreamModel {

  // Just reuse this definition for now, since they have the same fields
  type EventStreamModel = TelemetryModel

  def apply(config: Config): EventStreamModel = TelemetryModel(config)
}
