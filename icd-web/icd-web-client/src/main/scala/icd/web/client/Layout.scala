package icd.web.client

import scalatags.JsDom.TypedTag
import org.scalajs.dom.Element

/**
 * Manages the main layout (below the navbar)
 */
object Layout {

  // id of the wrapper HTML div
  val wrapperId = "wrapper"
  lazy val wrapper = $id(wrapperId)


//  private object Styles extends StyleSheet.Inline {
//    import dsl._
//    import language.postfixOps
//
//    val wrapper = style(
//      minHeight(100 %%),
//      height(100 %%),
//      width(100 %%),
//      position.absolute,
//      top(0 px),
//      left(0 px),
//      display.inlineBlock
//    )
//  }
//
//  private def markup(): TypedTag[Element] = {
//    import scalatags.JsDom.all._
//    import scalacss.ScalatagsCss._
//    div(Styles.wrapper, id := wrapperId)
//  }
//
//  /**
//   * Creates the html "wrapper" that holds the items to be added
//   */
//  def init(): Unit = {
//    main.appendChild(markup().render)
//  }

  /**
   * Adds an HTML element to the layout.
   * @param elem a scalatags element
   */
  def addItem(elem: TypedTag[Element]): Unit = {
    wrapper.appendChild(elem.render)
  }
}
