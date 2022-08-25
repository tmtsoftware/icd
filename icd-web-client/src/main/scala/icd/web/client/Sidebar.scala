package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.Element
import scalatags.JsDom.all._

trait SidebarListener {

  /**
   * called when one of the component links of clicked
   *
   * @param componentName the component name
   */
  def componentSelected(componentName: String): Unit
}

/**
 * Manages the sidebar items
 *
 * @param sidebarListener notified when a checkbox is changed or a link is clicked on
 */
case class Sidebar(sidebarListener: SidebarListener) extends Displayable {

  private val sidebarList = div(cls := "list-group list-group-flush mx-3 mt-4").render

  // HTML for component
  private def componentLink(compName: String) = {
    val compId = Components.getComponentInfoId(compName)
    a(
      cls := "list-group-item list-group-item-action py-2 ripple",
      title := s"Scroll to $compName",
      href := s"#$compId",
      onclick := componentSelected(compName) _
    )(i(cls := "fas fa-tachometer-alt fa-fw me-3")(span(compName)))
  }

  // called when a component link is clicked
  private def componentSelected(compName: String)(e: dom.Event): Unit = {
    e.preventDefault()
    sidebarListener.componentSelected(compName)
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
  def addComponent(compName: String): Unit = {
    addItem(componentLink(compName).render)
  }

  /**
   * Removes all the components from the sidebar
   */
  def clearComponents(): Unit = {
    sidebarList.innerHTML = ""
  }

  // Markup for the sidebar
  override def markup(): Element = {
    import scalacss.ScalatagsCss._
    import scalatags.JsDom.tags2._

    header(Styles.sidebarWrapper)(
      nav(cls := "hide collapse d-lg-block sidebar collapse bg-white", id := "sidebar")(
        div(Styles.sidebar, cls := "position-sticky")(
          sidebarList
        )
      )
    ).render
  }
}
