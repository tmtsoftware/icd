package icd.web.client

import scalacss.internal.mutable.StyleSheet

import scalacss.DevDefaults.*

//noinspection TypeAnnotation
// CSS styles
object Styles extends StyleSheet.Inline {

  import dsl.*
  import language.postfixOps

  val mainContent = style(
    padding(7 px, 0 px, 0 px, 15 px),
  )

  val sidebar = style(
    padding(7 px, 0 px, 0 px, 5 px),
    overflowY.auto,
    overflowX.hidden
  )

  val layout = style(
    padding(60 px, 0 px, 0 px, 0 px),
  )

  val contentDiv = style(
    float.left,
    padding(0 px, 0 px, 100 px, 0 px)
  )

  // Control width of tables in the information displayed for selected components
  val componentTable = style(
    tableLayout.fixed,
    maxWidth(90 %%)
  )

  val fitsTags = style(
    marginLeft(16 px),
  )

  val fitsTagsLabel = style(
    marginRight(16 px),
  )

  val fitsTable = style(
    padding(10 px, 0 px, 0 px, 0 px),
    tableLayout.fixed,
    maxWidth(90 %%)
  )

  val emptyStyle = style()

  // Used for table columns that should not wrap
  val noWrapTableColumn = style(
    whiteSpace.nowrap
  )

  val fileUploadMessages = style(
    addClassNames("alert", "alert-info"),
    padding(0 px, 10 px),
    margin(1 em, 0 em),
    border(1 px, solid, gray)
  )

  val commentBox = style(
    padding(10 px, 0 px),
    margin(1 em, 0 em)
  )

//  val versionHistory = style(
//    position.absolute,
//    overflowY.auto,
//    left(0 px),
//    bottom(0 px),
//    width(100 %%),
//    height(15 %%),
//    backgroundColor(c"#f5f5f5"),
//    padding(5 px, 10 px, 0 px, 10 px),
//    borderTop(1 px, solid, gray)
//  )

  val component = style(
    addClassName("container-fluid"),
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
    margin(0 px),
    outline.none,
    borderRadius(0 px),
    border(0 px)
  )

  val navbarBtn = style(
    fontSize(17 px)
  )

  val attributeCell = style(
    whiteSpace.nowrap
  )

  val selectDialogLabel = style(
    addClassNames("col-1"),
    padding(6 px, 6 px, 0 px, 16 px)
  )

  val selectDialogSubsystem = style(
    addClassNames("col-2"),
    padding(0 px, 0 px, 0 px, 12 px)
  )

  val selectDialogVersion = style(
    addClassNames("col-1"),
    padding(0 px, 0 px, 0 px, 5 px)
  )

  val selectDialogComponent = style(
    addClassNames("col-4"),
    padding(0 px, 0 px, 0 px, 5 px)
  )

  val selectDialogIcdRow = style(
    padding(0 px, 0 px, 60 px, 0 px)
  )

  val selectDialogSubsystemRow = style(
    padding(0 px, 0 px, 5 px, 0 px)
  )

  val subsystemSwapper = style(
    padding(0 px, 0 px, 20 px, 200 px),
    fontSize(30 px)
  )

  val selectDialogButton = style(
    padding(0 px, 0 px, 0 px, 10 px)
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

}
