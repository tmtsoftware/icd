package csw.services.icd

object PublishModel {

  case class Alarms(archive: String,
                    description: String,
                    name: String,
                    severity: String)

  case class ValueType(default: String,
                       enum: List[String]) // XXX

  case class Health(archive: String,
                    archiveRate: Double,
                    description: String,
                    maxRate: Double,
                    name: String,
                    rate: Double,
                    valueType: ValueType) // XXX

  case class Telemetry()

}

import PublishModel._

case class PublishModel(alarms: List[Alarms],
                        eventStreams: String,
                        events: String,
                        health: List[Health],
                        telemetry: List[Telemetry]) extends IcdModelBase
