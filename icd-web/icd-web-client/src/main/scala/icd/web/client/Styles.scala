package icd.web.client

import scalacss.internal.mutable.StyleSheet

import scalacss.Defaults._

// CSS styles
object Styles extends StyleSheet.Inline {

  import dsl._
  import language.postfixOps

  val layout = style(
    height(95 %%),
    width(100 %%),
    position.fixed,
    top(50 px),
    // Increase top offset in the range 750 to 1170 px,
    // since the navbar items wrap around at that size
    media.minWidth(750 px).maxWidth(1170 px)(top(170 px)),
    media.print(top(0 px)),
    left(0 px),
    display.block,
    media.print(width.auto, height.auto, display.block, overflow.visible, float.none, position.static)
  )

  val mainContent = style(
    addClassName("col-xs-11"),
    padding(0 px, 0 px, 0 px, 0 px),
    height(100 %%),
    media.print(width.auto, height.auto, display.block, overflow.visible, float.none, position.static)
  )

  val main = style(
    position.relative,
    height(100 %%),
    overflowY.auto,
    padding(0 px, 0 px, 0 px, 15 px),
    media.print(width.auto, height.auto, display.block, overflow.visible, float.none, position.static)
  )

  val contentDiv = style(
    float.left,
    media.print(float.none)
  )

  val sidebarWrapper = style(
    addClassNames("col-xs-1", "hidden-print"),
    height(100 %%)
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
    tableLayout.fixed,
    maxWidth(90 %%)
  )

  val emptyStyle = style()

  // Used for table columns that should not wrap
  val noWrapTableColumn = style(
    whiteSpace.nowrap
  )

  // Used for table columns that should wrap at "."
  val wrapTableColumn = style(
    wordBreak.breakAll, wordWrap.breakWord
  )

  val fileUploadMessages = style(
    addClassNames("alert", "alert-info"),
    padding(0 px, 10 px),
    margin(1 em, 0 em),
    border(1 px, solid, gray)
  )

  val publishMessages = fileUploadMessages

  val commentBox = style(
    padding(10 px, 0 px),
    margin(1 em, 0 em)
  )

  val versionHistory = style(
    position.absolute,
    overflowY.auto,
    left(0 px),
    bottom(0 px),
    width(100 %%),
    height(15 %%),
    backgroundColor(c"#f5f5f5"),
    padding(5 px, 10 px, 0 px, 10 px),
    borderTop(1 px, solid, gray)
  )

  val component = style(
    addClassName("container"),
    pageBreakBefore.always
  )

  val componentSection = style(
    pageBreakInside.avoid
  )

  val attributeTable = style(
    margin(0 px)
  )

  val attributeBtn = style(
    addClassNames("btn", "btn-default", "btn-xs"),
    margin(0 px),
    outline.none,
    borderRadius(0 px),
    border(0 px)
  )

  val attributeCell = style(
    whiteSpace.nowrap
  )

  val scrollableDiv = style(
    width(100 %%),
    height(100 %%),
    maxHeight(300 px),
    maxWidth(600 px),
    margin(0 px),
    padding(0 px),
    overflow.auto
  )

  val unstyledPre = style(
    border(0 px),
    color.black,
    backgroundColor.transparent
  )
}
