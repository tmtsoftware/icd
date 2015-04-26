package icd.web

import org.scalajs.dom

/**
 * Common definitions
 */
package object client {
  def $id(s: String) = dom.document.getElementById(s)
}
