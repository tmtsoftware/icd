package icd.web.client

import org.scalajs.dom.Element

/**
 * Displays a button that, when pressed, expands or collapses all the hidden table rows
 */
case class ExpandToggler() extends Displayable {

  /**
   * Returns the initial HTML markup for the item
   */
  override def markup(): Element = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    li(
      a(
        button(
          Styles.attributeBtn,
          tpe := "button",
          id := "expand-init",
          title := "Expand or collapse all detailed information"
          // XXX Its easier to do this in JavaScript directly, see resources/resize.js: navbarExpandAll
          //      onclick := listener
        )(i(cls := "bi bi-caret-down-square"))
      )
    ).render
  }
}
