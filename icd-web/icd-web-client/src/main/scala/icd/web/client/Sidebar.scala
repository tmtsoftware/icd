package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.{HTMLStyleElement, HTMLInputElement, HTMLUListElement}

import scalacss.Defaults._
import scalatags.JsDom.TypedTag
import org.scalajs.dom.Element

/**
 * Manages the sidebar items
 */
trait Sidebar {

  // id of html list of sidebar items
  val sidebarListId: String
  lazy val sidebarList = $id(sidebarListId).asInstanceOf[HTMLUListElement]

  // id used for component's checkbox
  private def checkboxId(compName: String): String = s"$compName-checkbox"

  // HTML for component
  private def renderComponentCheckBox(compName: String) = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._

    li(
      a(Styles.listGroupItem, href := "#")(
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

  // Uncheck all of the checkboxes in the sidebar
  def uncheckAll(): Unit = {
    val nodeList = sidebarList.getElementsByTagName("input")
    for (i <- 0 until nodeList.length) nodeList(i).asInstanceOf[HTMLInputElement].checked = false
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
//    clearElement(sidebarList)
    sidebarList.innerHTML = ""
  }

  private def markup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    //Styles.render[TypedTag[HTMLStyleElement]], Styles.sidebarWrapper,
//    Styles.sidebar
    div(Styles.sidebarWrapper)(
      div(Styles.sidebar)(
        ul(id := sidebarListId, cls := "nav list-group")
      )
    )
  }

  /**
   * Adds and initializes the left sidebar
   */
  def init(): Unit = {
    Layout.addItem(markup().render)
  }
}

object LeftSidebar extends Sidebar {
  override val sidebarListId = "leftSidebar"
}

object RightSidebar extends Sidebar {
  override val sidebarListId = "rightSidebar"
}
