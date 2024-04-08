package icd.web.client

import icd.web.shared.IcdModels.ComponentModel
import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.Element
import org.scalajs.dom.html.UList
import scalatags.JsDom.all.*

trait SidebarListener {

  /**
   * called when one of the component links of clicked
   *
   * @param componentName the component name
   */
  def componentSelected(componentModel: ComponentModel): Unit
}

/**
 * Manages the sidebar items
 *
 * @param sidebarListener notified when a checkbox is changed or a link is clicked on
 */
case class Sidebar(sidebarListener: SidebarListener) extends Displayable {

  private val sidebarList = ul(cls := "list-group").render

  // HTML for component
  private def componentLink(componentModel: ComponentModel) = {
    val name = s"${componentModel.subsystem}.${componentModel.component}"
    a(
      cls := "list-group-item list-group-item-action",
      title := s"Scroll to $name",
      href := "#",
      name,
      onclick := componentSelected(componentModel) _
    )
  }

  // called when a component link is clicked
  private def componentSelected(componentModel: ComponentModel)(e: dom.Event): Unit = {
    e.preventDefault()
    sidebarListener.componentSelected(componentModel)
  }

  /**
   * Adds an HTML element to the sidebar.
   *
   * @param node a scalatags node
   */
  def addItem(node: Node): Unit = {
    sidebarList.appendChild(node)
  }

  /**
   * Adds a component to the sidebar
   */
  def addComponent(componentModel: ComponentModel): Unit = {
    addItem(componentLink(componentModel).render)
  }

  /**
   * Removes all the components from the sidebar
   */
  def clearComponents(): Unit = {
    sidebarList.innerHTML = ""
  }

  // Markup for the sidebar
  override def markup(): Element = {
    div(
      id := "sidebar",
      cls := "d-none col-1 h-100"
    )(
      sidebarList
    ).render
  }
}
