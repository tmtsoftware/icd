package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._

import scala.concurrent.Future

/**
 * Type of a listener for changes in the selected subsystem
 */
trait SubsystemListener {
  /**
   * Called when a subsystem is selected
   * @param subsystemOpt the selected subsystem, or None if the default option is selected
   * @param saveHistory if true, push the browser history state, otherwise not
   * @return a future indicating when changes are done
   */
  def subsystemSelected(subsystemOpt: Option[String], saveHistory: Boolean = true): Future[Unit]
}

/**
 * Manages the subsystem combobox
 * @param listener notified when the user makes a selection or changes the filter checkbox
 * @param labelStr the text for the combobox label
 * @param msg the initial message to display before the first selection is made
 * @param removeMsg if true, remove the default msg item from the choices once a selection has been made
 */
case class Subsystem(listener: SubsystemListener,
                     labelStr: String = "Subsystem",
                     msg: String = "Select a subsystem",
                     removeMsg: Boolean = true) extends Displayable {

  val selectItem = {
    import scalatags.JsDom.all._
    select(onchange := subsystemSelected _)(
      option(value := msg)(msg)).render
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = !removeMsg && selectItem.selectedIndex == 0

  // called when an item is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (removeMsg && selectItem.options.length > 1 && selectItem.options(0).value == msg)
      selectItem.remove(0)

    listener.subsystemSelected(getSelectedSubsystem)
  }

  // HTML for the subsystem combobox
  override def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(label(s"$labelStr", " ", selectItem))).render
  }

  /**
   * Gets the currently selected subsystem name
   */
  def getSelectedSubsystem: Option[String] =
    selectItem.value match {
      case `msg`         ⇒ None
      case ""            ⇒ None
      case subsystemName ⇒ Some(subsystemName)
    }

  /**
   * Sets the selected subsystem.
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedSubsystem(nameOpt: Option[String],
                           notifyListener: Boolean = true,
                           saveHistory: Boolean = true): Future[Unit] = {
    if (nameOpt == getSelectedSubsystem)
      Future.successful()
    else {
      nameOpt match {
        case Some(s) ⇒ selectItem.value = s
        case None    ⇒ if (!removeMsg) selectItem.value = msg
      }
      if (notifyListener)
        listener.subsystemSelected(getSelectedSubsystem, saveHistory)
      else Future.successful()
    }
  }

  // Update the Subsystem combobox options
  def updateSubsystemOptions(items: List[String]): Unit = {
    import scalatags.JsDom.all._

    val selected = getSelectedSubsystem
    val list = selected match {
      case Some(subsystem) ⇒ items
      case None            ⇒ msg :: items
    }
    while (selectItem.options.length != 0) {
      selectItem.remove(0)
    }
    for (s ← list) {
      selectItem.add(option(value := s)(s).render)
    }
    setSelectedSubsystem(selected, notifyListener = true)
  }
}
