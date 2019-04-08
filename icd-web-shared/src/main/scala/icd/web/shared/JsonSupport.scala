package icd.web.shared

import icd.web.shared.ComponentInfo._
import icd.web.shared.IcdModels._
import play.api.libs.json._

//noinspection TypeAnnotation
object JsonSupport {
  implicit val publishTypeWrites = new Writes[PublishType] {
    def writes(v: PublishType) = JsString(v match {
      case Events => "Events"
      case ObserveEvents => "ObserveEvents"
      case CurrentStates => "CurrentStates"
      case Alarms => "Alarms"
    })
  }
  implicit val publishTypeReads: Reads[PublishType] = {
    case JsString(s) => JsSuccess(s match {
      case "Events" => Events
      case "ObserveEvents" => ObserveEvents
      case "CurrentStates" => CurrentStates
      case "Alarms" => Alarms
      case x => throw new RuntimeException(s"Bad publish type: $x")
    })
    case x => throw new RuntimeException(s"JSon parse error: $x")
  }

  // Represents an entry in the list of differences between two JSON versions
  case class JsonDiff(op: String, path: String, old: String, value: String)

  implicit val jsonDiffReads: Reads[JsonDiff] = {
    case JsObject(obj) =>
      JsSuccess(
        JsonDiff(obj("op").asInstanceOf[JsString].value,
          obj("path").asInstanceOf[JsString].value,
          obj.getOrElse("old", JsString("")).toString(),
          obj.getOrElse("value", JsString("")).toString()
        )
      )
    case x => throw new RuntimeException(s"JSon parse error: $x")
  }


  implicit val componentModelFormat = Json.format[ComponentModel]
  implicit val attributeModelFormat = Json.format[AttributeModel]
  implicit val eventModelFormat = Json.format[EventModel]
  implicit val subscribeModelInfoFormat = Json.format[SubscribeModelInfo]
  implicit val subscribeInfoFormat = Json.format[SubscribeInfo]
  implicit val eventInfoFormat = Json.format[EventInfo]
  implicit val alarmModelFormat = Json.format[AlarmModel]
  implicit val alarmInfoFormat = Json.format[AlarmInfo]
  implicit val detailedSubscribeInfoFormat = Json.format[DetailedSubscribeInfo]
  implicit val publishesFormat = Json.format[Publishes]
  implicit val subscribesFormat = Json.format[Subscribes]
  implicit val otherComponentFormat = Json.format[OtherComponent]
  implicit val receiveCommandModelFormat = Json.format[ReceiveCommandModel]
  implicit val sentCommandInfoFormat = Json.format[SentCommandInfo]
  implicit val receivedCommandInfoFormat = Json.format[ReceivedCommandInfo]
  implicit val commandsFormat = Json.format[Commands]
  implicit val componentInfoFormat = Json.format[ComponentInfo]
  implicit val subsystemInfoFormat = Json.format[SubsystemInfo]
  implicit val subsystemWithVersionFormat = Json.format[SubsystemWithVersion]
  implicit val icdVersionFormat = Json.format[IcdVersion]
  implicit val icdNameFormat = Json.format[IcdName]
  implicit val diffFormat = Json.format[Diff]
  implicit val diffItemFormat = Json.format[DiffItem]
  implicit val diffInfoFormat = Json.format[DiffInfo]
  implicit val versionInfoFormat = Json.format[VersionInfo]
  implicit val icdVersionInfoFormat = Json.format[IcdVersionInfo]

}
