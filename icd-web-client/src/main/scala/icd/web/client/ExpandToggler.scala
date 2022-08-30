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
          cls := "btn btn-sm",
          tpe := "button",
          id := "expand-init",
          attr("data-bs-toggle") := "collapse",
          attr("data-bs-target") := ".panel-collapse",
          title := "Expand or collapse all detailed information"
        )(i(Styles.navbarBtn, cls := "bi bi-caret-down-square"))
      )
    ).render
  }
}
