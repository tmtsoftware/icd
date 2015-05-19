package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import shared.{SubscribeInfo, ComponentInfo}
import upickle._

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages the component (Assembly, HCD) display
 * @param mainContent used to display information about selected components
 * @param listener called when the user clicks on a component link in the (subscriber, publisher, etc)
 */
case class Components(mainContent: MainContent, listener: String => Unit) {

  /**
   * Adds (appends) a component to the display
   * @param compName the name of the component
   * @param filter an optional list of target component names to use to filter the
   *               display (restrict to only those target components)
   */
  def addComponent(compName: String, filter: Option[List[String]]): Unit = {
    Ajax.get(Routes.componentInfo(compName)).map { r =>
      val info = applyFilter(filter, read[ComponentInfo](r.responseText))
      removeComponent(compName)
      displayInfo(info, filter.isDefined)
    }.recover {
      case ex =>
        mainContent.displayInternalError(ex)
    }
  }

  /**
   * Displays only the given component's information, ignoring any filter
   * @param compName the name of the component
   */
  def setComponent(compName: String): Unit = {
    Ajax.get(Routes.componentInfo(compName)).map { r =>
      val info = read[ComponentInfo](r.responseText)
      mainContent.clearContent()
      displayInfo(info, false)
    }.recover {
      case ex =>
        mainContent.displayInternalError(ex)
    }
  }

  // Filter out any components not in the filter, if the filter is defined
  private def applyFilter(filter: Option[List[String]], info: ComponentInfo): ComponentInfo = {
    filter match {
      case Some(names) =>
        val publishInfo = info.publishInfo.filter(p => p.subscribers.exists(s => names.contains(s.compName)))
        val subscribeInfo = info.subscribeInfo.filter(s => names.contains(s.compName))
        ComponentInfo(info.name, info.description, publishInfo, subscribeInfo)
      case None => info
    }
  }

  // Id of component info for given component name
  private def getComponentInfoId(compName: String) = s"$compName-info"

  // Removes the component display
  def removeComponent(compName: String): Unit = {
    val elem = $id(getComponentInfoId(compName))
    try {
      // XXX How to check if elem exists?
      mainContent.content.removeChild(elem)
    } catch {
      case t: Throwable =>
    }
  }

  // Displays the information for a component, appending to the other selected components, if any.
  private def displayInfo(info: ComponentInfo, filtered: Boolean): Unit = {
    if (info.publishInfo.nonEmpty || info.subscribeInfo.nonEmpty) {
      val titleStr = "Components" + (if (filtered) " (filtered)" else "")
      val markup = markupForComponent(info)
      if (mainContent.contentTitle.textContent != titleStr) {
        mainContent.clearContent()
        mainContent.setContentTitle(titleStr)
      }
      mainContent.content.appendChild(markup.render)
    }
  }

  // Action when user clicks on a subscriber link
  private def clickedOnSubscriber(info: SubscribeInfo)(e: dom.Event) = {
    println(s"XXX clickedOnSubscriber: component name = ${info.compName}")
    listener(info.compName)
  }

  // Action when user clicks on a subscriber link
  private def clickedOnPublisher(info: SubscribeInfo)(e: dom.Event) = {
    println(s"XXX clickedOnPublisher: component name = ${info.compName}")
    listener(info.compName)
  }

  // Makes the link for a subscriber component in the table
  private def makeLinkForSubscriber(info: SubscribeInfo) = {
    import scalatags.JsDom.all._
    a(s"${info.compName} ", href := "#", onclick := clickedOnSubscriber(info) _)
  }

  // Makes the link for a publisher component in the table
  private def makeLinkForPublisher(info: SubscribeInfo) = {
    import scalatags.JsDom.all._
    a(s"${info.compName} ", href := "#", onclick := clickedOnPublisher(info) _)
  }


  // Generates the HTML markup to display the component information
  private def markupForComponent(info: ComponentInfo) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    // Only display non-empty tables
    val pubDiv = if (info.publishInfo.isEmpty) div()
    else div(
      h3(s"Items published by ${info.name}"),
      table(Styles.componentTable, "data-toggle".attr := "table",
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
      )
    )

    val subDiv = if (info.subscribeInfo.isEmpty) div()
    else div(
      h3(s"Items subscribed to by ${info.name}"),
      table(Styles.componentTable, "data-toggle".attr := "table",
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

    div(cls := "container", id := getComponentInfoId(info.name))(
      h2(info.name),
      p(info.description),
      pubDiv,
      subDiv
    )
  }
}
