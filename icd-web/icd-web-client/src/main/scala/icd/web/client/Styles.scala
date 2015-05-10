package icd.web.client

import scalacss.Defaults._

// CSS styles
object Styles extends StyleSheet.Inline {
  import dsl._
  import language.postfixOps

  val mainWrapper = style(
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
    addClassName("col-md-1"),
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
}
