package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import shared.{SubscribeInfo, ComponentInfo}
import upickle._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages the component (Assembly, HCD) display
 */
object Component {
  // Adds the component to the display
  def addComponent(compName: String): Unit = {
    removeComponent(compName)
    Ajax.get(Routes.componentInfo(compName)).map { r =>
      val info = read[ComponentInfo](r.responseText)
      displayInfo(info)
    }.recover {
      case ex =>
        Main.displayInternalError(ex)
    }
  }

  // Id if component info for given component name
  def getComponentInfoId(compName: String) = s"$compName-info"

  // Removes the component display
  def removeComponent(compName: String): Unit = {
    val elem = $id(getComponentInfoId(compName))
    try {
      // XXX How to check if elem exists?
      Main.content.removeChild(elem)
    } catch {
      case t: Throwable =>
    }
  }

  // Displays the information for a component, appending to the other selected components, if any.
  def displayInfo(info: ComponentInfo): Unit = {
    val titleStr = "Components"
    val markup = markupForComponent(info)
    if (Main.contentTitle.textContent != titleStr) {
      Main.clearContent()
      Main.setContentTitle(titleStr)
    }

    Main.content.appendChild(markup.render)
  }

  // Action when user clicks on a subscriber link
  def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event) = {
    println(s"XXX clickedOnSubscriber: component name = ${info.compName}")
  }

  // Action when user clicks on a subscriber link
  def clickedOnPublisher(info: SubscribeInfo)(e: dom.Event) = {
    println(s"XXX clickedOnPublisher: component name = ${info.compName}")
  }

  // Makes the link for a subscriber component in the table
  def makeLinkForSubscriber(info: SubscribeInfo) = {
    import scalatags.JsDom.all._
    a(s"${info.compName} ", href := "#", onclick := clickedOnSubscriber(info) _)
  }

  // Makes the link for a publisher component in the table
  def makeLinkForPublisher(info: SubscribeInfo) = {
    import scalatags.JsDom.all._
    a(s"${info.compName} ", href := "#", onclick := clickedOnPublisher(info) _)
  }


  // Generates the HTML markup to display the component information
  def markupForComponent(info: ComponentInfo) = {
    import scalatags.JsDom.all._

    div(cls := "container", id := getComponentInfoId(info.name),
      h2(info.name),
      p(info.description),
      h3(s"Items published by ${info.name}"),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Name"),
            th("Type"),
            th("Description"),
            th("Subscribers")
          )
        ),
        tbody(
          for (pubInfo <- info.publishInfo) yield {
            tr(
              td(pubInfo.name),
              td(pubInfo.itemType),
              td(pubInfo.description),
              td(pubInfo.subscribers.map(makeLinkForSubscriber))
            )
          }
        )
      ),
      h3(s"Items subscribed to by ${info.name}"),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Prefix.Name"),
            th("Type"),
            th("Description"),
            th("Publisher")
          )
        ),
        tbody(
          for (subscribeInfo <- info.subscribeInfo) yield {
            tr(
              td(subscribeInfo.name),
              td(subscribeInfo.itemType),
              td(subscribeInfo.description),
              td(makeLinkForPublisher(subscribeInfo))
            )
          }
        )
      )

    )
  }
}
