package icd.web.client

import org.scalajs.dom.{Element, document}

import scala.util.Success
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

case class ReloadButton(selectDialog: SelectDialog) extends Displayable {
  private def reloadPage(): Unit = {
    val main = document.getElementById("mainContent")
    val y    = main.scrollTop
    val f = selectDialog.applySettings()
    f.onComplete {
      case Success(_) =>
        main.scrollTop = y
      case _ =>
    }
  }

  private val item = {
    import scalatags.JsDom.all.*

    button(
      cls := "attributeBtn btn btn-sm d-none",
      tpe := "button",
      id := "reload",
      title := "Reload the selected subsystem, API or ICD, refresh from icd database",
      onclick := reloadPage _
    )(i(cls := "navbarBtn bi bi-arrow-clockwise")).render
  }

  override def setEnabled(enabled: Boolean): Unit = {
    item.disabled = !enabled
  }

  def setVisible(show: Boolean): Unit = {
    if (show) {
      item.classList.remove("d-none")
    }
    else {
      item.classList.add("d-none")
    }
  }


  override def markup(): Element = {
    import scalatags.JsDom.all.*
    li(a(item)).render
  }
}
