package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.Element
import org.scalajs.dom.html.UList
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

  val sidebarList: UList = ul(cls := "nav list-group").render

  // HTML for component
  private def componentLink(compName: String) = {
    val compId = Components.getComponentInfoId(compName)
    li(a(title := s"Scroll to $compName", href := s"#$compId", compName, onclick := componentSelected(compName) _))
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

    div(Styles.sidebarWrapper, id := "sidebar")(
      div(Styles.sidebar)(
        sidebarList
      )
    ).render
  }
}
