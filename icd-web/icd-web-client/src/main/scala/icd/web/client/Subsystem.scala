package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._

/**
 * Manages the subsystem combobox
 * @param listener notified when the user makes a selection
 * @param labelStr the text for the combobox label
 * @param msg the initial message to display before the first selection is made
 * @param removeMsg if true, remove the default msg item from the choices once a selection has been made
 */
case class Subsystem(listener: String => Unit,
                     labelStr: String = "Subsystem",
                     msg: String = "Select a subsystem",
                     removeMsg: Boolean = true) extends Displayable {

  // called when an item is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (removeMsg && selectItem.options.length > 1 && selectItem.options(0).value == msg)
      selectItem.remove(0)

    getSelectedSubsystem.foreach(listener)
  }

  val selectItem = {
    import scalatags.JsDom.all._
    select(onchange := subsystemSelected _)(
      option(value := msg)(msg)
    ).render
  }

  // HTML for the subsystem combobox
  override def markup(): Element = {
    import scalatags.JsDom.all._
    li(
      a(
        label(s"$labelStr ", selectItem)
      )
    ).render
  }

  // Gets the currently selected subsystem name
  def getSelectedSubsystem: Option[String] =
    selectItem.value match {
      case `msg` => None
      case subsystemName => Some(subsystemName)
    }


  // Update the Subsystem combobox options
  def updateSubsystemOptions(items: List[String]): Unit = {
    import scalatags.JsDom.all._

    val list = msg :: items
    while (selectItem.options.length != 0) {
      selectItem.remove(0)
    }
    for (s <- list) {
      selectItem.add(option(value := s)(s).render)
    }
  }
}
