package icd.web.client

import scalacss.Defaults._

// CSS styles
object Styles extends StyleSheet.Inline {

  import dsl._
  import language.postfixOps


  /*
  @media (min-width: 992px) {
    #main-wrapper {
        float:right;
    }
}
@media (max-width: 992px) {
    #main-wrapper {
        padding-top: 0;
    }
}
@media (max-width: 992px) {
    #sidebar-wrapper {
        position: static;
        height:auto;
        max-height: 300px;
        border-right:0;
    }
}

   */


  val wrapper = style(
    minHeight(100 %%),
    height(100 %%),
    width(100 %%),
    position.absolute,
    top(0 px),
    left(0 px),
    display.inlineBlock
  )

  val mainWrapper = style(
    addClassName("col-md-8"),
    height(100 %%),
    overflowY.auto,
    padding(50 px, 0 px, 0 px, 0 px)
  )

  val main = style(
    position.relative,
    height(100 %%),
    overflowY.auto,
    padding(0 px, 15 px)
  )

  val sidebarWrapper = style(
    addClassName("col-md-2"),
    height(100 %%),
    padding(50 px, 0 px, 0 px, 0 px),
    borderRight(1 px, solid, gray),
    borderLeft(1 px, solid, gray)
  )

  val sidebar = style(
    height(100 %%),
    overflowY.auto
  )

  val listGroupItem = style(
    addClassName("list-group-item"),
    borderRadius(0 px),
    borderLeft(0 px),
    borderRight(0 px),
    borderTop(0 px)
  )

  // Control width of tables in the information displayed for selected components
  val componentTable = style(
    maxWidth(80 %%)
//    tableLayout.fixed,
//    width(100%%)
  )
}
