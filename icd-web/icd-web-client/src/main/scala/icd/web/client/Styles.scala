package icd.web.client

import scalacss.Defaults._

// CSS styles
object Styles extends StyleSheet.Inline {

  import dsl._
  import language.postfixOps

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
  )

  val fileUploadMessages = style(
    padding(0 px, 10 px),
    margin(1 em, 0 em),
    border(1 px, solid, gray)
  )
}
