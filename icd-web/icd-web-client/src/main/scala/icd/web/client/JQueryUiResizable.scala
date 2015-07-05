package icd.web.client

import org.scalajs.dom
//import org.scalajs.jquery.{ JQuery, JQueryEventObject }
import org.querki.jquery._

import scala.scalajs.js
import scala.language.implicitConversions

/**
 * JQueryUi resizable facade
 */
object JQueryUiResizable {
  implicit def jquery2ui(jquery: JQuery): JQueryUiResizable =
    jquery.asInstanceOf[JQueryUiResizable]

  // Argument to resizable() call
  trait JQueryUiResizableSettings extends js.Object {
    var handles: String = js.native
    var minWidth: Int = js.native
    var maxWidth: Int = js.native
    var resize: Function2[JQueryEventObject, Ui, Unit] = js.native

    //    def apply(handles: String, minWidth: Int, maxWidth: Int): JQueryUiResizableSettings =
    //      new JQueryUiResizableSettings(handles = handles)
  }

  trait Ui extends js.Object {
    //  The jQuery object representing the element to be resized
    var element: JQuery = js.native

    //  The jQuery object representing the helper that's being resized
    var helper: JQuery = js.native
    //  The jQuery object representing the original element before it is wrapped
    var originalElement: JQuery = js.native
    //  The position represented as { left, top } before the resizable is resized
    var originalPosition: Position = js.native
    //  The size represented as { width, height } before the resizable is resized
    var originalSize: Size = js.native
    //  The current position represented as { left, top }. The values may be changed to modify where the element will be positioned. Useful for custom resizing logic.
    var position: Position = js.native
    //  The current size represented as { width, height }. The values may be changed to modify where the element will be positioned. Useful for custom resizing logic.
    var size: Size = js.native
  }

  trait Size extends js.Object {
    var width: Int = js.native
    var height: Int = js.native
  }

  trait Position extends js.Object {
    var left: Int = js.native
    var top: Int = js.native
  }

  private def resize(event: JQueryEventObject, ui: Ui): Unit = {

  }

  /**
   * Makes an item (div) resizable in thegh given direction(s)
   * @param idStr the item's id
   * @param horizontal make horizontally resizable
   * @param vertical make vertically resizable
   */
  def makeResizable(idStr: String, horizontal: Boolean = true, vertical: Boolean = false): Unit = {
//    import org.scalajs.jquery.{ jQuery ⇒ $, _ }
    import JQueryUiResizable._
//    $(dom.document).ready { () ⇒
//      //      $(idStr).resizable()
//    }

    /*
     $(document).ready(function () {
     $("#sidebar").resizable({
         handles: 'e',
         minWidth: 150,
         maxWidth: 1200,
         resize: function (event, ui) {
             var x = ui.element.outerWidth();
             //var y=ui.element.outerHeight();
             var par = $(this).parent().width();
             var ele = ui.element;
             var factor = par - x;

             if (x == par) {
                 return;
             }

             $.each(ele.siblings(), function (idx, item) {
                 //ele.siblings().eq(idx).css('height',y+'px');
                 ele.siblings().eq(idx).css('width', (factor) + 'px');
             });

             if (x >= (par - 100)) {
                 $("#sidebar").resizable("option", "maxWidth", ui.size.width);
             }
         }
     });
 });
      */
  }

}

trait JQueryUiResizable extends JQuery {
  import JQueryUiResizable._
  def resizable(options: JQueryUiResizableSettings): JQueryUiResizable = js.native
}

