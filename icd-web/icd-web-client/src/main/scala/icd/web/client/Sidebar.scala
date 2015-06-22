package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.{ Node, HTMLInputElement }

import org.scalajs.dom.Element

import scala.concurrent.Future
import scalatags.JsDom.all._

trait SidebarListener {
  /**
   * Called when a component in the sidebar is checked or unchecked
   * @param componentName the component name
   * @param checked true if the checkbox is checked
   */
  def componentSelected(componentName: String, checked: Boolean): Unit
}

/**
 * Manages the sidebar items
 *
 * @param listener called with the component name and checkbox state
 *                 when one of the component checkboxes is checked or unchecked
 */
case class Sidebar(listener: SidebarListener) extends Displayable {

  val sidebarList = ul(cls := "nav list-group").render

  // HTML for component
  private def componentCheckBox(compName: String, checkboxListener: dom.Event ⇒ Unit) = {
    import scalacss.ScalatagsCss._
    val compId = Components.getComponentInfoId(compName)
    li(
      a(Styles.listGroupItem)(
        div(cls := "checkbox")(
          label(
            input(
              title := s"Toggle the display of $compName",
              tpe := "checkbox",
              value := compName,
              checked := true,
              onchange := checkboxListener),
            a(title := s"Scroll to $compName", href := s"#$compId", compName)))))
  }

  /**
   * Returns a list of names of all the components whose checkboxes are checked
   */
  def getSelectedComponents: List[String] = {
    val nodeList = sidebarList.getElementsByTagName("input")
    val result = for (i ← 0 until nodeList.length) yield {
      val elem = nodeList(i).asInstanceOf[HTMLInputElement]
      if (elem.checked) Some(elem.value) else None
    }
    result.toList.flatten
  }

  /**
   * Sets the list of checked components in the sidebar
   * @return true if anything was changed
   */
  def setSelectedComponents(components: List[String]): Boolean = {
    import org.scalajs.dom.ext._
    val set = components.toSet
    val changes = for (elem ← sidebarList.getElementsByTagName("input").toList) yield {
      val checkbox = elem.asInstanceOf[HTMLInputElement]
      val checked = set.contains(checkbox.value)
      val changed = checkbox.checked != checked
      if (changed) checkbox.checked = checked
      changed
    }
    changes.contains(true)
  }

  // called when a component is selected or deselected
  private def componentSelected(compName: String)(e: dom.Event): Unit = {
    val checked = e.target.asInstanceOf[HTMLInputElement].checked
    listener.componentSelected(compName, checked)
  }

  // Uncheck all of the checkboxes in the sidebar
  def uncheckAll(): Unit = {
    val nodeList = sidebarList.getElementsByTagName("input")
    for (i ← 0 until nodeList.length) nodeList(i).asInstanceOf[HTMLInputElement].checked = false
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
    addItem(componentCheckBox(compName, componentSelected(compName)).render)
  }

  /**
   * Removes all the ICD components from the sidebar
   */
  def clearComponents(): Unit = {
    sidebarList.innerHTML = ""
  }

  // Display the subsystem combobox at top, then the list of component checkboxes
  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    div(Styles.sidebarWrapper)(
      div(Styles.sidebar)(
        sidebarList)).render
  }
}

