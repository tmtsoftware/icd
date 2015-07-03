package icd.web.client

import scala.scalajs.js
import scala.language.implicitConversions

/**
 * Adds jquery-ui-resizable support
 */
object JQueryUiResizable {
  implicit def jquery2ui(jquery: JQuery): JQueryUiResizable =
    jquery.asInstanceOf[JQueryUiResizable]
}

trait JQueryUiResizable extends js.Object {
  def resizable(options: js.Any): JQueryUiResizable = js.native
}

