package icd.web.shared

import icd.web.shared.ComponentInfo._
import icd.web.shared.AllEventList.{Event, EventsForComponent, EventsForSubsystem}
import icd.web.shared.IcdModels.{IcdModel, _}
import icd.web.shared.SharedUtils.Credentials
import play.api.libs.json._

//noinspection TypeAnnotation
object JsonSupport {
  implicit val publishTypeWrites = new Writes[PublishType] {
    def writes(v: PublishType) =
      JsString(v match {
        case Events        => "Events"
        case ObserveEvents => "ObserveEvents"
        case CurrentStates => "CurrentStates"
        case Images        => "Images"
        case Alarms        => "Alarms"
      })
  }
  implicit val publishTypeReads: Reads[PublishType] = {
    case JsString(s) =>
      JsSuccess(s match {
        case "Events"        => Events
        case "ObserveEvents" => ObserveEvents
        case "CurrentStates" => CurrentStates
        case "Images"        => Images
        case "Alarms"        => Alarms
        case x               => throw new RuntimeException(s"Bad publish type: $x")
      })
    case x => throw new RuntimeException(s"JSon parse error: $x")
  }

  // Represents an entry in the list of differences between two JSON versions
  case class JsonDiff(op: String, path: String, old: String, value: String)

  implicit val jsonDiffReads: Reads[JsonDiff] = {
    case JsObject(obj) =>
      JsSuccess(
        JsonDiff(
          obj("op").asInstanceOf[JsString].value,
          obj("path").asInstanceOf[JsString].value,
          obj.getOrElse("old", JsString("")).toString(),
          obj.getOrElse("value", JsString("")).toString()
        )
      )
    case x => throw new RuntimeException(s"JSon parse error: $x")
  }

  implicit val componentModelFormat        = Json.format[ComponentModel]
  implicit val parameterModelFormat        = Json.format[ParameterModel]
  implicit val eventModelFormat            = Json.format[EventModel]
  implicit val metadataModelFormat         = Json.format[MetadataModel]
  implicit val imageModelFormat            = Json.format[ImageModel]
  implicit val subscribeModelInfoFormat    = Json.format[SubscribeModelInfo]
  implicit val subscribeInfoFormat         = Json.format[SubscribeInfo]
  implicit val eventInfoFormat             = Json.format[EventInfo]
  implicit val imageInfoFormat             = Json.format[ImageInfo]
  implicit val alarmModelFormat            = Json.format[AlarmModel]
  implicit val detailedSubscribeInfoFormat = Json.format[DetailedSubscribeInfo]
  implicit val publishesFormat             = Json.format[Publishes]
  implicit val subscribesFormat            = Json.format[Subscribes]
  implicit val otherComponentFormat        = Json.format[OtherComponent]
  implicit val receiveCommandModelFormat   = Json.format[ReceiveCommandModel]
  implicit val sentCommandInfoFormat       = Json.format[SentCommandInfo]
  implicit val receivedCommandInfoFormat   = Json.format[ReceivedCommandInfo]
  implicit val commandsFormat              = Json.format[Commands]
  implicit val subsystemWithVersionFormat  = Json.format[SubsystemWithVersion]
  implicit val subsystemInfoFormat         = Json.format[SubsystemInfo]
  implicit val icdVersionFormat            = Json.format[IcdVersion]
  implicit val icdNameFormat               = Json.format[IcdName]
  implicit val diffFormat                  = Json.format[Diff]
  implicit val diffItemFormat              = Json.format[DiffItem]
  implicit val diffInfoFormat              = Json.format[DiffInfo]
  implicit val versionInfoFormat           = Json.format[VersionInfo]
  implicit val icdVersionInfoFormat        = Json.format[IcdVersionInfo]
  implicit val apiVersionInfoFormat        = Json.format[ApiVersionInfo]
  implicit val publishInfoFormat           = Json.format[PublishInfo]
  implicit val gitHubCredentialsFormat     = Json.format[GitHubCredentials]
  implicit val credentialsFormat           = Json.format[Credentials]
  implicit val publishApiInfoFormat        = Json.format[PublishApiInfo]
  implicit val publishIcdInfoFormat        = Json.format[PublishIcdInfo]
  implicit val unpublishApiInfoFormat      = Json.format[UnpublishApiInfo]
  implicit val unpublishIcdInfoFormat      = Json.format[UnpublishIcdInfo]
  implicit val eventFormat                 = Json.format[Event]
  implicit val eventsForComponentFormat    = Json.format[EventsForComponent]
  implicit val eventsForSubsystemFormat    = Json.format[EventsForSubsystem]
  implicit val icdModelFormat              = Json.format[IcdModel]
  implicit val servicePathFormat           = Json.format[ServicePath]
  implicit val serviceModelClientFormat    = Json.format[ServiceModelClient]
  implicit val serviceModelProviderFormat  = Json.format[ServiceModelProvider]
  implicit val serviceModelClientComponent  = Json.format[ServiceModelClientComponent]
  implicit val serviceModelFormat          = Json.format[ServiceModel]
  implicit val serviceProvidedInfoFormat   = Json.format[ServiceProvidedInfo]
  implicit val servicesRequiredInfoFormat  = Json.format[ServicesRequiredInfo]
  implicit val servicesFormat              = Json.format[Services]
  implicit val componentInfoFormat         = Json.format[ComponentInfo]
  implicit val fitsSourceFormat            = Json.format[FitsSource]
  implicit val fitsChannelFormat            = Json.format[FitsChannel]
  implicit val fitsKeyInfoFormat           = Json.format[FitsKeyInfo]
  implicit val fitsKeyInfoListFormat       = Json.format[FitsKeyInfoList]
  implicit val fitsTagsFormat              = Json.format[FitsTags]
  implicit val fitsDictionaryFormat        = Json.format[FitsDictionary]
}
