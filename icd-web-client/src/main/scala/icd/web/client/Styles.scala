package icd.web.client

import scalacss.internal.mutable.StyleSheet

import scalacss.DevDefaults._
//import scalacss.ProdDefaults._

//noinspection TypeAnnotation
// CSS styles
object Styles extends StyleSheet.Inline {

  import dsl._
  import language.postfixOps

  val layout: StyleA = style(
    height(95 %%),
    width(100 %%),
    position.fixed,
    top(50 px),
    // Increase top offset in the range 400 to 790 px,
    // since the navbar items wrap around at that size
    media.minWidth(400 px).maxWidth(790 px)(top(100 px)),
    left(0 px),
    display.block,
  )

  val mainContent: StyleA = style(
    addClassName("col-11"),
    padding(0 px, 0 px, 0 px, 0 px),
    height(100 %%),
  )

  val main = style(
    position.relative,
    height(100 %%),
    overflow.auto,
    padding(0 px, 0 px, 0 px, 15 px),
  )

  val contentDiv = style(
    float.left,
  )

  val sidebarWrapper = style(
    height(100 %%)
  )

  val sidebar = style(
    height(100 %%),
    overflow.auto
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
    wordBreak.breakAll,
    wordWrap.breakWord
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
    width(100 %%),
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

  val selectDialogLabel = style(
    addClassNames("col-xs-1"),
    padding(6 px, 6 px, 0 px, 16 px)
  )

  val selectDialogSubsystem = style(
    addClassNames("col-xs-2"),
    padding(0 px, 0 px, 0 px, 12 px)
  )

  val selectDialogVersion = style(
    addClassNames("col-xs-1"),
    padding(0 px, 0 px, 0 px, 5 px)
  )

  val selectDialogComponent = style(
    addClassNames("col-xs-4"),
    padding(0 px, 0 px, 0 px, 5 px)
  )

  val selectDialogIcdRow = style(
    padding(0 px, 0 px, 60 px, 0 px)
  )

  val selectDialogSubsystemRow = style(
    padding(0 px, 0 px, 20 px, 0 px)
  )

  val subsystemSwapper = style(
    padding(0 px, 0 px, 20 px, 200 px),
    fontSize(30 px)
  )

  val selectDialogApplyButton = style(
    padding(20 px, 0 px, 20 px, 0 px)
  )

  val statusDialogLabel = style(
    addClassNames("col-xs-1"),
    padding(6 px, 6 px, 0 px, 16 px)
  )

  val statusDialogSubsystem = style(
    addClassNames("col-xs-2"),
    padding(0 px, 0 px, 0 px, 12 px)
  )

  val statusDialogSubsystemRow = style(
    padding(0 px, 0 px, 20 px, 0 px)
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

  val checkboxStyle = style(
    float.left,
    margin(9 px, 0 px, 0 px)
  )

//  val busyWaiting = style(
//    cursor.progress
//  )
}
