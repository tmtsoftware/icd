package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.raw.Node

import scalacss.ScalatagsCss._
import scalatags.JsDom.all._

/**
 * Manages the main layout (below the navbar)
 */
object Layout {

  //Styles.render[TypedTag[HTMLStyleElement]], Styles.mainWrapper,
  val wrapper = div(Styles.wrapper).render

  /**
   * Creates the html "wrapper" that holds the items to be added
   */
  def init(): Unit = {
    dom.document.body.appendChild(wrapper)
  }

  /**
   * Adds an HTML element to the layout.
   * @param node a scalatags node
   */
  def addItem(node: Node): Unit = {
    wrapper.appendChild(node)
  }
}
