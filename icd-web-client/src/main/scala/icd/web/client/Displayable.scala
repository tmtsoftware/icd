package icd.web.client

import org.scalajs.dom.*

/**
 * An object that can be displayed in the browser
 */
trait Displayable {

  /**
   * Returns the initial HTML markup for the item
   */
  def markup(): Element

  /** Enable or disable the item */
  def setEnabled(enabled: Boolean): Unit = {}
}
