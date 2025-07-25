package icd.web.shared

import icd.web.shared.ComponentInfo.*
import icd.web.shared.IcdModels.{IcdModel, *}
import icd.web.shared.SharedUtils.Credentials
import play.api.libs.json.*
import play.api.libs.functional.syntax.*

//noinspection TypeAnnotation
object JsonSupport {
  implicit val publishTypeWrites: Writes[PublishType] = new Writes[PublishType] {
    def writes(v: PublishType): JsValue =
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

  implicit val componentModelFormat: OFormat[ComponentModel] = Json.format[ComponentModel]
  implicit val EventParameterFitsKeyInfo: OFormat[EventParameterFitsKeyInfo] = Json.format[EventParameterFitsKeyInfo]
  implicit val parameterModelFormat: OFormat[ParameterModel] = Json.format[ParameterModel]
  implicit val eventModelFormat: OFormat[EventModel] = Json.format[EventModel]
  implicit val metadataModelFormat: OFormat[MetadataModel] = Json.format[MetadataModel]
  implicit val imageModelFormat: OFormat[ImageModel] = Json.format[ImageModel]
  implicit val subscribeModelInfoFormat: OFormat[SubscribeModelInfo] = Json.format[SubscribeModelInfo]
  implicit val subscribeInfoFormat: OFormat[SubscribeInfo] = Json.format[SubscribeInfo]
  implicit val eventInfoFormat: OFormat[EventInfo] = Json.format[EventInfo]
  implicit val imageInfoFormat: OFormat[ImageInfo] = Json.format[ImageInfo]
  implicit val alarmModelFormat: OFormat[AlarmModel] = Json.format[AlarmModel]
  implicit val detailedSubscribeInfoFormat: OFormat[DetailedSubscribeInfo] = Json.format[DetailedSubscribeInfo]
  implicit val publishesFormat: OFormat[Publishes] = Json.format[Publishes]
  implicit val subscribesFormat: OFormat[Subscribes] = Json.format[Subscribes]
  implicit val otherComponentFormat: OFormat[OtherComponent] = Json.format[OtherComponent]
  implicit val commandResultModelFormat: OFormat[CommandResultModel] = Json.format[CommandResultModel]
  implicit val receiveCommandModelFormat: OFormat[ReceiveCommandModel] = Json.format[ReceiveCommandModel]
  implicit val sentCommandInfoFormat: OFormat[SentCommandInfo] = Json.format[SentCommandInfo]
  implicit val receivedCommandInfoFormat: OFormat[ReceivedCommandInfo] = Json.format[ReceivedCommandInfo]
  implicit val commandsFormat: OFormat[Commands] = Json.format[Commands]
  implicit val subsystemWithVersionFormat: OFormat[SubsystemWithVersion] = Json.format[SubsystemWithVersion]
  implicit val subsystemInfoFormat: OFormat[SubsystemInfo] = Json.format[SubsystemInfo]
  implicit val icdVersionFormat: OFormat[IcdVersion] = Json.format[IcdVersion]
  implicit val icdNameFormat: OFormat[IcdName] = Json.format[IcdName]
  implicit val diffFormat: OFormat[Diff] = Json.format[Diff]
  implicit val diffItemFormat: OFormat[DiffItem] = Json.format[DiffItem]
  implicit val diffInfoFormat: OFormat[DiffInfo] = Json.format[DiffInfo]
  implicit val versionInfoFormat: OFormat[VersionInfo] = Json.format[VersionInfo]
  implicit val icdVersionInfoFormat: OFormat[IcdVersionInfo] = Json.format[IcdVersionInfo]
  implicit val apiVersionInfoFormat: OFormat[ApiVersionInfo] = Json.format[ApiVersionInfo]
  implicit val publishInfoFormat: OFormat[PublishInfo] = Json.format[PublishInfo]
  implicit val gitHubCredentialsFormat: OFormat[GitHubCredentials] = Json.format[GitHubCredentials]
  implicit val credentialsFormat: OFormat[Credentials] = Json.format[Credentials]
  implicit val publishApiInfoFormat: OFormat[PublishApiInfo] = Json.format[PublishApiInfo]
  implicit val publishIcdInfoFormat: OFormat[PublishIcdInfo] = Json.format[PublishIcdInfo]
  implicit val unpublishApiInfoFormat: OFormat[UnpublishApiInfo] = Json.format[UnpublishApiInfo]
  implicit val unpublishIcdInfoFormat: OFormat[UnpublishIcdInfo] = Json.format[UnpublishIcdInfo]
  implicit val icdModelFormat: OFormat[IcdModel] = Json.format[IcdModel]
  implicit val servicePathFormat: OFormat[ServicePath] = Json.format[ServicePath]
  implicit val serviceModelClientFormat: OFormat[ServiceModelClient] = Json.format[ServiceModelClient]
  implicit val serviceModelProviderFormat: OFormat[ServiceModelProvider] = Json.format[ServiceModelProvider]
  implicit val serviceModelClientComponent: OFormat[ServiceModelClientComponent] = Json.format[ServiceModelClientComponent]
  implicit val serviceModelFormat: OFormat[ServiceModel] = Json.format[ServiceModel]
  implicit val serviceProvidedInfoFormat: OFormat[ServiceProvidedInfo] = Json.format[ServiceProvidedInfo]
  implicit val servicesRequiredInfoFormat: OFormat[ServicesRequiredInfo] = Json.format[ServicesRequiredInfo]
  implicit val servicesFormat: OFormat[Services] = Json.format[Services]
  implicit val componentInfoFormat: OFormat[ComponentInfo] = Json.format[ComponentInfo]
  implicit val fitsSourceFormat: OFormat[FitsSource] = Json.format[FitsSource]
  implicit val fitsChannelFormat: OFormat[FitsChannel] = Json.format[FitsChannel]

  // This version is used for exchanging data between the web app and server
//  implicit val fitsKeyInfoFormat           = Json.format[FitsKeyInfo]

  // This version is used only when writing to the human editable FITS-Dictionary.json file:
  // To make it easier to read/edit, uses the abbrieviated "source" syntax if there is only a default channel.
  val fitsKeyInfoWrites: Writes[FitsKeyInfo] = (fitsKeyInfo: FitsKeyInfo) => {
    if (fitsKeyInfo.channels.size == 1 && fitsKeyInfo.channels.head.name.isEmpty)
      Json.obj(
        "name" -> fitsKeyInfo.name,
        "description" -> fitsKeyInfo.description,
        "type" -> fitsKeyInfo.`type`,
        "units" -> fitsKeyInfo.units,
        //          "source"      -> fitsSourceFormat.writes(fitsKeyInfo.channels.head.source)
        "source" -> fitsKeyInfo.channels.head.source
      )
    else
      Json.obj(
        "name" -> fitsKeyInfo.name,
        "description" -> fitsKeyInfo.description,
        "type" -> fitsKeyInfo.`type`,
        "units" -> fitsKeyInfo.units,
        //          "channel"     -> fitsKeyInfo.channels.map(c => fitsChannelFormat.writes(c))
        "channel" -> fitsKeyInfo.channels
      )
  }

  implicit val fitsKeyInfoReads: Reads[FitsKeyInfo] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "units").readNullable[String] and
      (JsPath \ "source").readNullable[FitsSource] and
      (JsPath \ "channel").readNullable[List[FitsChannel]]
  )(FitsKeyInfo.fromSourceOrChannel)

  implicit val fitsKeyInfoFormat: Format[FitsKeyInfo] =
    Format(fitsKeyInfoReads, fitsKeyInfoWrites)

  implicit val fitsKeyInfoListFormat: OFormat[FitsKeyInfoList] = Json.format[FitsKeyInfoList]
  implicit val fitsKeywordFormat: OFormat[FitsKeyword] = Json.format[FitsKeyword]
  implicit val fitsTagsFormat: OFormat[FitsTags] = Json.format[FitsTags]
  implicit val availableChannelsFormat: OFormat[AvailableChannels] = Json.format[AvailableChannels]
  implicit val fitsDictionaryFormat: OFormat[FitsDictionary] = Json.format[FitsDictionary]
}
