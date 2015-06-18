package icd.web.client

import org.scalajs.dom._

/**
 * A navbar item to handle publishing an API or ICD.
 */
case class PublishItem(listener: () ⇒ Unit) extends Displayable {

  // Returns the HTML markup for the navbar item
  def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(onclick := listener)("Publish")).render
  }
}

