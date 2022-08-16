package controllers

import csw.services.icd.db.IcdDb
import play.api.libs.concurrent.ActorModule
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import csw.services.icd.fits.IcdFitsDefs.FitsKeyMap
import icd.web.shared.IcdModels.{EventModel, IcdModel}
import icd.web.shared.{
  AllEventList,
  ApiVersionInfo,
  ComponentInfo,
  DiffInfo,
  IcdName,
  IcdVersionInfo,
  IcdVizOptions,
  PdfOptions,
  PublishApiInfo,
  PublishIcdInfo,
  SubsystemInfo,
  UnpublishApiInfo,
  UnpublishIcdInfo,
  VersionInfo
}

import scala.util.Try

/**
 * Use an actor to manage concurrent access to mutable cached data
 */
object ApplicationActor extends ActorModule {
  type Message = Messages
  sealed trait Messages
  final case class GetSubsystemNames(replyTo: ActorRef[List[String]]) extends Messages
  final case class GetSubsystemInfo(subsystem: String, maybeVersion: Option[String], replyTo: ActorRef[Option[SubsystemInfo]])
      extends Messages
  final case class GetComponents(subsystem: String, maybeVersion: Option[String], replyTo: ActorRef[List[String]])
      extends Messages
  final case class GetComponentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApi: Option[Boolean],
      replyTo: ActorRef[List[ComponentInfo]]
  ) extends Messages
  final case class GetEventList(replyTo: ActorRef[List[AllEventList.EventsForSubsystem]]) extends Messages
//  final case class GetEventInfo(
//      subsystem: String,
//      component: String,
//      event: String,
//      replyTo: ActorRef[Option[EventModel]]
//  ) extends Messages
  final case class GetIcdComponentInfo(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      replyTo: ActorRef[List[ComponentInfo]]
  ) extends Messages
  final case class GetIcdAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      pdfOptions: PdfOptions,
      replyTo: ActorRef[Option[Array[Byte]]]
  ) extends Messages
  final case class GetApiAsPdf(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      searchAll: Option[Boolean],
      clientApi: Option[Boolean],
      pdfOptions: PdfOptions,
      replyTo: ActorRef[Option[Array[Byte]]]
  ) extends Messages
  final case class GetArchivedItemsReport(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      pdfOptions: PdfOptions,
      replyTo: ActorRef[Option[Array[Byte]]]
  ) extends Messages
  final case class GetArchivedItemsReportFull(
      pdfOptions: PdfOptions,
      replyTo: ActorRef[Option[Array[Byte]]]
  ) extends Messages
  final case class MakeGraph(
      subsystem: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybeTarget: Option[String],
      maybeTargetVersion: Option[String],
      maybeTargetComponent: Option[String],
      maybeIcdVersion: Option[String],
      options: IcdVizOptions,
      replyTo: ActorRef[Option[Array[Byte]]]
  ) extends Messages
  final case class GetVersions(subsystem: String, replyTo: ActorRef[List[VersionInfo]])                       extends Messages
  final case class GetVersionNames(subsystem: String, replyTo: ActorRef[List[String]])                        extends Messages
  final case class GetIcdNames(replyTo: ActorRef[List[IcdName]])                                              extends Messages
  final case class GetIcdVersions(subsystem: String, target: String, replyTo: ActorRef[List[IcdVersionInfo]]) extends Messages
  final case class GetDiff(subsystem: String, versionsStr: String, replyTo: ActorRef[List[DiffInfo]])         extends Messages
  final case class PublishApi(publishApiInfo: PublishApiInfo, replyTo: ActorRef[Try[ApiVersionInfo]])         extends Messages
  final case class PublishIcd(publishIcdInfo: PublishIcdInfo, replyTo: ActorRef[Try[IcdVersionInfo]])         extends Messages
  final case class UnpublishApi(unpublishApiInfo: UnpublishApiInfo, replyTo: ActorRef[Try[Option[ApiVersionInfo]]])
      extends Messages
  final case class UnpublishIcd(unpublishIcdInfo: UnpublishIcdInfo, replyTo: ActorRef[Try[Option[IcdVersionInfo]]])
      extends Messages
  final case class UpdatePublished(replyTo: ActorRef[Unit]) extends Messages
  final case class GetIcdModels(
      subsystem: String,
      maybeVersion: Option[String],
      target: String,
      maybeTargetVersion: Option[String],
      replyTo: ActorRef[List[IcdModel]]
  ) extends Messages
  final case class Generate(
      subsystem: String,
      lang: String,
      className: String,
      maybeVersion: Option[String],
      maybeComponent: Option[String],
      maybePackageName: Option[String],
      replyTo: ActorRef[Option[String]]
  ) extends Messages

  // -------------------------------------------------------------------

  def create(db: IcdDb): Behavior[Messages] = {
    Behaviors.setup { _ =>
      val app = new ApplicationImpl(db)
      Behaviors.receiveMessage[Messages] {
        case GetSubsystemNames(replyTo) =>
          replyTo ! app.getSubsystemNames
          Behaviors.same
        case GetSubsystemInfo(subsystem, maybeVersion, replyTo) =>
          replyTo ! app.getSubsystemInfo(subsystem, maybeVersion)
          Behaviors.same
        case GetComponents(subsystem, maybeVersion, replyTo) =>
          replyTo ! app.getComponents(subsystem, maybeVersion)
          Behaviors.same
        case GetComponentInfo(subsystem, maybeVersion, maybeComponent, searchAll, clientApi, replyTo) =>
          replyTo ! app.getComponentInfo(subsystem, maybeVersion, maybeComponent, searchAll, clientApi)
          Behaviors.same
        case GetEventList(replyTo) =>
          replyTo ! app.getEventList
          Behaviors.same
//        case GetEventInfo(subsystem, component, event, replyTo) =>
//          replyTo ! app.getEventInfo(subsystem, component, event)
//          Behaviors.same
        case GetIcdComponentInfo(
              subsystem,
              maybeVersion,
              maybeComponent,
              target,
              maybeTargetVersion,
              maybeTargetComponent,
              replyTo
            ) =>
          replyTo ! app.getIcdComponentInfo(
            subsystem,
            maybeVersion,
            maybeComponent,
            target,
            maybeTargetVersion,
            maybeTargetComponent
          )
          Behaviors.same
        case GetIcdAsPdf(
              subsystem,
              maybeVersion,
              maybeComponent,
              target,
              maybeTargetVersion,
              maybeTargetComponent,
              maybeIcdVersion,
              pdfOptions,
              replyTo: ActorRef[Option[Array[Byte]]]
            ) =>
          replyTo ! app.getIcdAsPdf(
            subsystem,
            maybeVersion,
            maybeComponent,
            target,
            maybeTargetVersion,
            maybeTargetComponent,
            maybeIcdVersion,
            pdfOptions
          )
          Behaviors.same
        case GetApiAsPdf(
              subsystem,
              maybeVersion,
              maybeComponent,
              searchAll,
              clientApi,
              pdfOptions,
              replyTo: ActorRef[Option[Array[Byte]]]
            ) =>
          replyTo ! app.getApiAsPdf(
            subsystem,
            maybeVersion,
            maybeComponent,
            searchAll,
            clientApi,
            pdfOptions
          )
          Behaviors.same
        case GetArchivedItemsReport(
              subsystem,
              maybeVersion,
              maybeComponent,
              pdfOptions,
              replyTo
            ) =>
          replyTo ! app.getArchivedItemsReport(
            subsystem,
            maybeVersion,
            maybeComponent,
            pdfOptions
          )
          Behaviors.same
        case GetArchivedItemsReportFull(pdfOptions, replyTo) =>
          replyTo ! app.getArchivedItemsReportFull(pdfOptions)
          Behaviors.same
        case MakeGraph(
              subsystem,
              maybeVersion,
              maybeComponent,
              maybeTarget,
              maybeTargetVersion,
              maybeTargetComponent,
              maybeIcdVersion,
              options,
              replyTo
            ) =>
          replyTo ! app.makeGraph(
            subsystem,
            maybeVersion,
            maybeComponent,
            maybeTarget,
            maybeTargetVersion,
            maybeTargetComponent,
            maybeIcdVersion,
            options
          )
          Behaviors.same
        case GetVersions(subsystem, replyTo) =>
          replyTo ! app.getVersions(subsystem)
          Behaviors.same
        case GetVersionNames(subsystem, replyTo) =>
          replyTo ! app.getVersionNames(subsystem)
          Behaviors.same
        case GetIcdNames(replyTo) =>
          replyTo ! app.getIcdNames
          Behaviors.same
        case GetIcdVersions(subsystem, target, replyTo) =>
          replyTo ! app.getIcdVersions(subsystem, target)
          Behaviors.same
        case GetDiff(subsystem, versionStr, replyTo) =>
          replyTo ! app.getDiff(subsystem, versionStr)
          Behaviors.same
        case PublishApi(publishApiInfo, replyTo) =>
          replyTo ! app.publishApi(publishApiInfo)
          Behaviors.same
        case PublishIcd(publishIcdInfo, replyTo) =>
          replyTo ! app.publishIcd(publishIcdInfo)
          Behaviors.same
        case UnpublishApi(unpublishApiInfo, replyTo) =>
          replyTo ! app.unpublishApi(unpublishApiInfo)
          Behaviors.same
        case UnpublishIcd(unpublishIcdInfo, replyTo) =>
          replyTo ! app.unpublishIcd(unpublishIcdInfo)
          Behaviors.same
        case UpdatePublished(replyTo) =>
          replyTo ! app.updatePublished()
          Behaviors.same
        case GetIcdModels(
              subsystem,
              maybeVersion,
              target,
              maybeTargetVersion,
              replyTo
            ) =>
          replyTo ! app.getIcdModels(
            subsystem,
            maybeVersion,
            target,
            maybeTargetVersion
          )
          Behaviors.same
        case Generate(
              subsystem,
              lang,
              className,
              maybeVersion,
              maybeComponent,
              maybePackageName,
              replyTo: ActorRef[Option[String]]
            ) =>
          replyTo ! app.generate(
            subsystem,
            lang,
            className,
            maybeVersion,
            maybeComponent,
            maybePackageName
          )
          Behaviors.same
      }
    }
  }
}
