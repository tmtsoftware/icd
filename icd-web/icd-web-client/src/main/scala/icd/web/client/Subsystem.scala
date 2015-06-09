package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._

/**
 * Manages the subsystem combobox
 * @param listener notified when the user makes a selection or changes the filter checkbox
 * @param labelStr the text for the combobox label
 * @param msg the initial message to display before the first selection is made
 * @param removeMsg if true, remove the default msg item from the choices once a selection has been made
 */
case class Subsystem(listener: (Option[String], Boolean) ⇒ Unit,
                     labelStr: String = "Subsystem",
                     msg: String = "Select a subsystem",
                     removeMsg: Boolean = true,
                     showFilterCheckbox: Boolean = false) extends Displayable {

  // Optional filter checkbox, if subsystem should act as a filter
  private val filterCb = {
    import scalatags.JsDom.all._
    input(tpe := "checkbox", value := "", checked := true, onchange := subsystemSelected _).render
  }

  val selectItem = {
    import scalatags.JsDom.all._
    select(onchange := subsystemSelected _)(
      option(value := msg)(msg)).render
  }

  /**
   * Returns true if the filter checkbox is showing and selected
   * @return
   */
  def isFilterSelected: Boolean = showFilterCheckbox && filterCb.checked

  /**
   * Sets the state of the filter checkbox
   */
  def setFilterSelected(checked: Boolean): Unit = {
    if (checked != isFilterSelected) filterCb.checked = checked
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

    listener(getSelectedSubsystem, isFilterSelected)
  }

  // HTML for the subsystem combobox
  override def markup(): Element = {
    import scalatags.JsDom.all._
    if (showFilterCheckbox) {
      div()(
        label(s"$labelStr", " ", selectItem),
        "  ",
        label(filterCb, " ", "Filter")).render
    } else {
      label(s"$labelStr", " ", selectItem).render
    }
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
   * Sets the selected subsystem
   */
  def setSelectedSubsystem(nameOpt: Option[String], notifyListener: Boolean = true): Unit = {
    //    if (nameOpt != getSelectedSubsystem) {
    nameOpt match {
      case Some(s) ⇒ selectItem.value = s
      case None    ⇒ if (!removeMsg) selectItem.value = msg
    }
    if (notifyListener) listener(getSelectedSubsystem, isFilterSelected)
    //    }
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
