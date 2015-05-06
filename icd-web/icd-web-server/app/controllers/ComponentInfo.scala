package controllers

import csw.services.icd.db.IcdDb
import csw.services.icd.model.IcdModels
import play.api.libs.json._
import shared.PublishInfo
import shared.SubscribeInfo


object ComponentInfo {
  /**
   * Query the database for information about the given component
   */
  def apply(db: IcdDb, compName: String): shared.ComponentInfo = {
    // get the models for this component and it's subcomponents
    val modelsList = db.query.getModels(compName)
    val description = getDescription(modelsList)
    val publishInfo = for (models <- modelsList) yield {
      getPublishInfo(db, models)
    }
    shared.ComponentInfo(compName, description, publishInfo.flatten)
  }

  private def getDescription(modelsList: List[IcdModels]): String = {
    if (modelsList.isEmpty) ""
    else {
      modelsList.head.componentModel match {
        case Some(model) => model.description
        case None => ""
      }
    }
  }

  private def getPublishInfo(db: IcdDb, models: IcdModels): List[PublishInfo] = {
    val prefix = models.componentModel.get.prefix
    val result = models.publishModel.map { m =>
      m.telemetryList.map { t =>
        PublishInfo("telemetry", t.name, t.description, getSubscribers(db, prefix, t.name))
      } ++
        m.eventList.map { el =>
          PublishInfo("event", el.name, el.description, getSubscribers(db, prefix, el.name))
        } ++
        m.eventStreamList.map { esl =>
          PublishInfo("event stream", esl.name, esl.description, getSubscribers(db, prefix, esl.name))
        } ++
        m.alarmList.map { al =>
          PublishInfo("alarm", al.name, al.description, getSubscribers(db, prefix, al.name))
        } ++
        m.healthList.map { hl =>
          PublishInfo("health", hl.name, hl.description, getSubscribers(db, prefix, hl.name))
        }
    }
    result.toList.flatten
  }

  private def getSubscribers(db: IcdDb, prefix: String, name: String): List[SubscribeInfo] = {
    db.query.subscribes(s"$prefix.$name").map(s => SubscribeInfo(s.subsystem, s.componentName))
  }

  // JSON conversion
  implicit val SubscribeInfoWrites = new Writes[SubscribeInfo] {
    def writes(info: SubscribeInfo) = Json.obj(
      "subsystem" -> info.subsystem,
      "name" -> info.name
    )
  }
  implicit val PublishInfoWrites = new Writes[PublishInfo] {
    def writes(info: PublishInfo) = Json.obj(
      "itemType" -> info.itemType,
      "name" -> info.name,
      "description" -> info.description,
      "subscribers" -> info.subscribers
    )
  }
  implicit val ComponentInfoWrites = new Writes[shared.ComponentInfo] {
    def writes(info: shared.ComponentInfo) = Json.obj(
      "name" -> info.name,
      "description" -> info.description,
      "publishInfo" -> info.publishInfo
    )
  }
}

