package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLInputElement, HTMLUListElement}
import scala.scalajs.js.annotation.JSExport

/**
 * Manages the sidebar items
 */
@JSExport
object Sidebar {
  // id of html list of sidebar items
  private def sidebarList = $id("sidebar-list").asInstanceOf[HTMLUListElement]

  // id used for component's checkbox
  private def checkboxId(compName: String): String = s"$compName-checkbox"

  // HTML for component
  private def renderComponentCheckBox(compName: String) = {
    import scalatags.JsDom.all._

    li(
      a(cls := "list-group-item", href := "#")(
        div(cls := "checkbox")(
          label(
            input(tpe := "checkbox", value := "", id := checkboxId(compName)),
            compName)
        )
      )
    ).render
  }

  // called when a component is selected or deselected
  private def componentSelected(compName: String)(e: dom.Event): Unit = {
    val checked = e.srcElement.asInstanceOf[HTMLInputElement].checked
    if (checked) Component.addComponent(compName) else Component.removeComponent(compName)
  }

  /**
   * Adds an ICD component to the sidebar
   */
  def addComponent(compName: String): Unit = {
    sidebarList.appendChild(renderComponentCheckBox(compName))
    $id(checkboxId(compName)).addEventListener("change", componentSelected(compName) _, useCapture = false)
  }

  /**
   * Removes all the ICD components from the sidebar
   */
  def clearComponents(): Unit = {
    clearElement(sidebarList)
  }
}
