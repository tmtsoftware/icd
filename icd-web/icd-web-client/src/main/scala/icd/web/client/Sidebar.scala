package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.{Node, HTMLInputElement}

import org.scalajs.dom.Element

import scalatags.JsDom.all._

/**
 * Manages the sidebar items
 */
case class Sidebar(components: Components) extends Displayable {

  val sidebarList = ul(cls := "nav list-group").render

  // id used for component's checkbox
  private def checkboxId(compName: String): String = s"$compName-checkbox"

  // HTML for component
  private def componentCheckBox(compName: String) = {
    import scalacss.ScalatagsCss._
    li(
      a(Styles.listGroupItem, href := "#")(
        div(cls := "checkbox")(
          label(
            input(tpe := "checkbox", value := "", id := checkboxId(compName)),
            compName)
        )
      )
    )
  }

  // called when a component is selected or deselected
  private def componentSelected(compName: String)(e: dom.Event): Unit = {
    val checked = e.srcElement.asInstanceOf[HTMLInputElement].checked
    if (checked) components.addComponent(compName) else components.removeComponent(compName)
  }

  // Uncheck all of the checkboxes in the sidebar
  def uncheckAll(): Unit = {
    val nodeList = sidebarList.getElementsByTagName("input")
    for (i <- 0 until nodeList.length) nodeList(i).asInstanceOf[HTMLInputElement].checked = false
  }

  /**
   * Adds an HTML element to the sidebar.
   * @param node a scalatags node
   */
  def addItem(node: Node): Unit = {
    sidebarList.appendChild(node)
  }

  /**
   * Adds an ICD component to the sidebar
   */
  def addComponent(compName: String): Unit = {
    addItem(componentCheckBox(compName).render)
    $id(checkboxId(compName)).addEventListener("change", componentSelected(compName) _, useCapture = false)
  }

  /**
   * Removes all the ICD components from the sidebar
   */
  def clearComponents(): Unit = {
    //    sidebarList.innerHTML = ""
    val nodeList = sidebarList.getElementsByTagName("input")
    for (i <- 0 until nodeList.length) sidebarList.removeChild(nodeList(i))
  }

  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    //Styles.render[TypedTag[HTMLStyleElement]], Styles.sidebarWrapper,
    div(Styles.sidebarWrapper)(
      div(Styles.sidebar)(
        sidebarList
      )
    ).render
  }
}

