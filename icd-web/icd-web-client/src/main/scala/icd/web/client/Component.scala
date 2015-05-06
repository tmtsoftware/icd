package icd.web.client

import org.scalajs.dom.ext.Ajax
import shared.ComponentInfo
import upickle._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Manages the component (Assembly, HCD) display
 */
@JSExport
object Component {
  // Adds the component to the display
  def addComponent(compName: String): Unit = {
    removeComponent(compName)
    Ajax.get(Routes.componentInfo(compName)).map { r =>
      val info = read[ComponentInfo](r.responseText)
      displayInfo(info)
    }.recover {
      case ex =>
        displayInternalError(ex)
    }
  }

  // Id if component info for given component name
  def getComponentInfoId(compName: String) = s"$compName-info"

  // Removes the component display
  def removeComponent(compName: String): Unit = {
    val elem = $id(getComponentInfoId(compName))
      try {
        // XXX How to check if elem exists?
        content.removeChild(elem)
      } catch {
        case t: Throwable =>
      }
  }

  // Displays the information for a component, appending to the other selected components, if any.
  def displayInfo(info: ComponentInfo): Unit = {
    val titleStr = "Components"
    val markup = markupForComponent(info)
    if (contentTitle.textContent != titleStr) {
      setContent(titleStr, markup.toString())
    } else {
      content.appendChild(markup.render)
    }
  }

  // Generates the HTML markup to display the component information
  def markupForComponent(info: ComponentInfo) = {
    import scalatags.JsDom.all._

    for (pubInfo <- info.publishInfo) {
      println(s"XXX ${pubInfo.name}")
    }

    div(cls := "container", id := getComponentInfoId(info.name),
      h2(info.name),
      p(info.description),
      table("data-toggle".attr := "table",
        thead(
          tr(
            th("Publishes"),
            th("Type"),
            th("Description"),
            th("Subscribers")
          )
        ),
        tbody(
          for (pubInfo <- info.publishInfo) yield tr(
            td(pubInfo.name),
            td(pubInfo.itemType),
            td(pubInfo.description),
            td(pubInfo.subscribers.map(s => s"${s.subsystem}/${s.name}").mkString(", "))
          )
        )
      )
    )
  }
}
