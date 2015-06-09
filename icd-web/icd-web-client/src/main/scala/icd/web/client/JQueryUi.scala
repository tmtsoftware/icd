package icd.web.client

import scala.scalajs.js
import org.scalajs.jquery._
import scala.language.implicitConversions

/**
 * Adds some jquery-ui support (jquery-ui is an extension of jquery)
 */
object JQueryUi {
  implicit def jquery2ui(jquery: JQuery): JQueryUi =
    jquery.asInstanceOf[JQueryUi]
}

trait JQueryUi extends JQuery {
  def resizable(options: js.Any): JQueryUi = js.native
}

