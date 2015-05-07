package controllers

import csw.services.icd.db.{IcdDbQuery, IcdDb}
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
    val publishInfo = for (models <- modelsList.headOption) yield {
      getPublishInfo(db, models)
    }
    val subscribeInfo = for (models <- modelsList.headOption) yield {
      getSubscribeInfo(db, models)
    }
    shared.ComponentInfo(compName, description, publishInfo.toList.flatten, subscribeInfo.toList.flatten)
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
        PublishInfo("Telemetry", t.name, t.description, getSubscribers(db, prefix, t.name, t.description))
      } ++
        m.eventList.map { el =>
          PublishInfo("Event", el.name, el.description, getSubscribers(db, prefix, el.name, el.description))
        } ++
        m.eventStreamList.map { esl =>
          PublishInfo("EventStream", esl.name, esl.description, getSubscribers(db, prefix, esl.name, esl.description))
        } ++
        m.alarmList.map { al =>
          PublishInfo("Alarm", al.name, al.description, getSubscribers(db, prefix, al.name, al.description))
        } ++
        m.healthList.map { hl =>
          PublishInfo("Health", hl.name, hl.description, getSubscribers(db, prefix, hl.name, hl.description))
        }
    }
    result.toList.flatten
  }

  private def getSubscribers(db: IcdDb, prefix: String, name: String, desc: String): List[SubscribeInfo] = {
    db.query.subscribes(s"$prefix.$name").map { s =>
      SubscribeInfo(s.subscribeType.toString, s.name, desc, s.subsystem, s.componentName)
    }
  }

  private def getSubscribeInfo(db: IcdDb, models: IcdModels): List[SubscribeInfo] = {

    def getInfo(itemType: String, si: csw.services.icd.model.SubscribeInfo): List[SubscribeInfo] = {
      db.query.publishes(si.name).map { pi =>
        SubscribeInfo(itemType, si.name, pi.item.description, si.subsystem, pi.componentName)
      }
    }

    val result = models.subscribeModel.map { m =>
      m.telemetryList.map(getInfo("Telemetry", _)) ++
        m.eventList.map(getInfo("Event", _)) ++
        m.eventStreamList.map(getInfo("EventStream", _)) ++
        m.alarmList.map(getInfo("Alarm", _)) ++
        m.healthList.map(getInfo("Health", _))
    }
    result.toList.flatten.flatten
  }


  // JSON conversion
  implicit val SubscribeInfoWrites = new Writes[SubscribeInfo] {
    def writes(info: SubscribeInfo) = Json.obj(
      "itemType" -> info.itemType,
      "name" -> info.name,
      "description" -> info.description,
      "subsystem" -> info.subsystem,
      "compName" -> info.compName
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
      "publishInfo" -> info.publishInfo,
      "subscribeInfo" -> info.subscribeInfo
    )
  }
}

