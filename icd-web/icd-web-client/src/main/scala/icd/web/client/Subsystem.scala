package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import scalatags.JsDom.TypedTag

/**
 * Manages the subsystem combobox
 */
case class Subsystem(listener: String => Unit) {
  private val msg = "Select a subsystem"

  // called when an item is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (selectItem.options.length > 1 && selectItem.options(0).value == msg)
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
  def markup(): TypedTag[Element] = {
    import scalatags.JsDom.all._
    li(
      a(
        label("Subsystem ", selectItem)
      )
    )
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
