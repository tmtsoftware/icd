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

  //    <div id="wrapper">

  //      <div id="sidebar-wrapper" class="col-md-1">
  //        <div id="sidebar">
  //          <ul id="sidebar-list" class="nav list-group"></ul>
  //        </div>
  //      </div>

  //      <div id="main-wrapper" class="col-md-10">
  //        <div id="main">
  //          <h3 id="contentTitle" class="page-header"></h3>
  //          <div id="content">
  //          </div>
  //        </div>
  //      </div>

  //      <div id="right-sidebar-wrapper" class="col-md-1">
  //        <div id="right-sidebar">
  //          <ul id="right-sidebar-list" class="nav list-group"></ul>
  //        </div>
  //      </div>

  //    </div>


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
