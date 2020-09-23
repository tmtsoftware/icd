package csw.services.icd.db

import icd.web.shared.IcdModels
import icd.web.shared.IcdModels.EventModel

import collection.immutable.Set

object ComponentDataReporter {
  def printAllUsedUnits(db: IcdDb): Unit = {
    var units      = Set[String]()
    val components = db.query.getComponents(None)
    components.foreach { componentModel =>
      println(s"--------- Component ${componentModel.component} --------")
      val publishModel = db.query.getPublishModel(componentModel, None)
      publishModel.foreach { model =>
        model.eventList.foreach { item =>
          println(s"----- Item ${item.name}")
          item.parameterList.foreach { att =>
            print(s"-- Attribute ${att.name}: type=${att.typeStr}")
            units = units + att.units
          }
        }
        model.observeEventList.foreach { item =>
          println(s"----- Item ${item.name}")
          item.parameterList.foreach { att =>
            println(s"-- Attribute ${att.name}: type=${att.typeStr}")
            units = units + att.units
          }
        }
      }
      val commandModel = db.query.getCommandModel(componentModel, None)
      commandModel.foreach(
        model =>
          model.receive.foreach { command =>
            println(s"----- Command ${command.name}")
            command.parameters.foreach { arg =>
              println(s"-- Argument ${arg.name}: type=${arg.typeStr}")
              units = units + arg.units
            }
          }
      )
    }
    println(units.toSeq.sorted.mkString("\n\n\n----- Units -----\n", "\n", "\n------------"))
  }

  def listData(db: IcdDb, subsystem: String): Unit = {
    val publishInfo = db.query.getPublishInfo(subsystem, None)
    publishInfo.foreach { componentPublishInfo =>
      println(s" ----  ${componentPublishInfo.componentName} ----- ")
      val componentModel = db.query.getComponentModel(subsystem, componentPublishInfo.componentName, None)
      val totals = componentModel.flatMap { cm =>
        db.query.getPublishModel(cm, None).map { cpm =>
          val totalEventData = if (cpm.eventList.nonEmpty) {
            println("--- Event Data")
            listEventData(cpm.eventList)
          } else 0
          val totalObserveData = if (cpm.observeEventList.nonEmpty) {
            println("--- Observe Event Data")
            listEventData(cpm.observeEventList)
          } else 0
          (totalEventData, totalObserveData)
        }
      }
      val pair          = totals.unzip
      val totalEvents   = pair._1.sum
      val totalObserves = pair._2.sum
      if (totalEvents != 0 && totalObserves != 0) {
        val name     = componentPublishInfo.componentName
        val totalStr = IcdModels.bytesToString(totalEvents + totalObserves)
        println(s"==== Total archived data per year for $name: $totalStr")

      }
    }
  }

  def listEventData(events: List[EventModel]): Long = {
    val totals = events.map { event =>
      val (maxRate, defaultMaxRateUsed) = EventModel.getMaxRate(event.maybeMaxRate)
      println(s"Item Name: ${event.name}, max rate=$maxRate, archive=${event.archive}")
      if (event.archive) {
        val s = if (defaultMaxRateUsed) " (by default since not specified)" else ""
        println(s"Item is archived at a rate of $maxRate Hz$s")
        println(s"Total size of event: ${event.totalSizeInBytes} bytes. Yearly accumulation: ${event.totalArchiveSpacePerYear}")
        event.totalArchiveBytesPerYear
      } else 0
    }
    val sum = totals.sum
    println(s"Total data accumulation per year for this component model: ${IcdModels.bytesToString(sum)}")
    sum
  }
}
