package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import Subsystem._

/**
 * Manages the subsystem and related subsystem version comboboxes
 */
object Subsystem {
  /**
   * Type of a listener for changes in the selected subsystem
   */
  trait SubsystemListener {
    /**
     * Called when a subsystem is selected
     * @param sv the selected and version
     * @param saveHistory if true, push the browser history state, otherwise not
     * @return a future indicating when changes are done
     */
    def subsystemSelected(sv: SubsystemWithVersion, saveHistory: Boolean = true): Future[Unit]
  }

  /**
   * Called when a subsystem is selected
   * @param subsystemOpt the selected subsystem, or None if no subsystem is selected
   * @param versionOpt optional version of the subsystem (None means the latest version)
   */
  case class SubsystemWithVersion(subsystemOpt: Option[String], versionOpt: Option[String])
}

/**
 * Manages the subsystem and related subsystem version comboboxes
 * @param listener notified when the user makes a selection or changes the filter checkbox
 * @param labelStr the text for the combobox label
 * @param msg the initial message to display before the first selection is made
 * @param removeMsg if true, remove the default msg item from the choices once a selection has been made
 */
case class Subsystem(listener: SubsystemListener,
                     labelStr: String = "Subsystem",
                     msg: String = "Select a subsystem",
                     removeMsg: Boolean = true) extends Displayable {

  // The subsystem combobox
  private val subsystemItem = {
    import scalatags.JsDom.all._
    select(onchange := subsystemSelected _)(
      option(value := msg)(msg)).render
  }

  // The subsystem version combobox
  private val versionItem = {
    import scalatags.JsDom.all._
    select(hidden := true, onchange := subsystemVersionSelected _).render
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = !removeMsg && subsystemItem.selectedIndex == 0

  // called when a subsystem is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    // remove empty option
    if (removeMsg && subsystemItem.options.length > 1 && subsystemItem.options(0).value == msg)
      subsystemItem.remove(0)

    for (_ ← updateSubsystemVersionOptions())
      listener.subsystemSelected(getSubsystemWithVersion)
  }

  // called when a subsystem version is selected
  private def subsystemVersionSelected(e: dom.Event): Unit = {
    listener.subsystemSelected(getSubsystemWithVersion)
  }

  // HTML markup displaying the subsystem and version comboboxes
  override def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(label(s"$labelStr", " ", subsystemItem), " ", versionItem)).render
  }

  /**
   * Gets the currently selected subsystem name
   */
  def getSelectedSubsystem: Option[String] =
    subsystemItem.value match {
      case `msg`         ⇒ None
      case subsystemName ⇒ Some(subsystemName)
    }

  /**
   * Returns true if the latest subsystem version is selected
   */
  def isLatestSubsystemVersionSelected: Boolean =
    versionItem.selectedIndex == 0

  /**
   * Gets the currently selected subsystem version (None for latest version)
   */
  def getSelectedSubsystemVersion: Option[String] =
    versionItem.value match {
      case null | "" ⇒ None
      case version   ⇒ Some(version)
    }

  /**
   * Gets the selected subsystem with the selected version
   */
  def getSubsystemWithVersion: SubsystemWithVersion =
    SubsystemWithVersion(getSelectedSubsystem, getSelectedSubsystemVersion)

  /**
   * Sets the selected subsystem and version.
   * @param sv the subsystem name and version to set
   * @param notifyListener if true, notify the listener
   * @param saveHistory if true, save the current state to the browser history
   * @return a future indicating when any event handlers have completed
   */
  def setSubsystemWithVersion(sv: SubsystemWithVersion,
                              notifyListener: Boolean = true,
                              saveHistory: Boolean = true): Future[Unit] = {
    if (sv == getSubsystemWithVersion)
      Future.successful()
    else {
      sv.subsystemOpt match {
        case Some(s) ⇒ subsystemItem.value = s
        case None    ⇒ if (!removeMsg) subsystemItem.value = msg
      }
      (for (_ ← updateSubsystemVersionOptions()) yield {
        sv.versionOpt match {
          case Some(s) ⇒ versionItem.value = s
          case None    ⇒ versionItem.value = ""
        }
      }) andThen {
        case _ ⇒
          if (notifyListener)
            listener.subsystemSelected(getSubsystemWithVersion, saveHistory)
          else Future.successful()
      }
    }
  }

  // Update the Subsystem combobox options
  def updateSubsystemOptions(items: List[String]): Unit = {
    import scalatags.JsDom.all._

    val selected = getSubsystemWithVersion
    val latest = isLatestSubsystemVersionSelected
    val list = selected.subsystemOpt match {
      case Some(subsystem) ⇒ items
      case None            ⇒ msg :: items
    }
    while (subsystemItem.options.length != 0) {
      subsystemItem.remove(0)
    }
    for (s ← list) {
      subsystemItem.add(option(value := s)(s).render)
    }
    for (_ ← updateSubsystemVersionOptions()) {
      // Update to latest version, if it makes sense
      if (latest) {
        val versionOpt = versionItem.options.headOption.map(_.value)
        setSubsystemWithVersion(SubsystemWithVersion(selected.subsystemOpt, versionOpt))
      } else setSubsystemWithVersion(selected)
    }
  }

  /**
   * Sets the selected subsystem version.
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedSubsystemVersion(versionOpt: Option[String],
                                  notifyListener: Boolean = true,
                                  saveHistory: Boolean = true): Future[Unit] = {
    if (versionOpt == getSelectedSubsystemVersion)
      Future.successful()
    else {
      versionOpt match {
        case Some(s) ⇒ versionItem.value = s
        case None    ⇒ versionItem.value = ""
      }
      if (notifyListener)
        listener.subsystemSelected(getSubsystemWithVersion, saveHistory)
      else Future.successful()
    }
  }

  // Updates the version combobox with the list of available versions for the selected subsystem.
  // Returns a future indicating when done.
  private def updateSubsystemVersionOptions(): Future[Unit] = {
    versionItem.setAttribute("hidden", "true")
    getSelectedSubsystem match {
      case Some(subsystem) ⇒
        getSubsystemVersionOptions(subsystem).map { list ⇒ // Future!
          updateSubsystemVersionOptions(list)
          if (list.nonEmpty) {
            versionItem.value = list.head
            versionItem.removeAttribute("hidden")
          }
        }
      case None ⇒ Future.successful()
    }
  }

  // Updates the version combobox with the given list of available versions for the selected subsystem
  private def updateSubsystemVersionOptions(versions: List[String]): Unit = {
    import scalatags.JsDom.all._
    while (versionItem.options.length != 0) {
      versionItem.remove(0)
    }
    for (s ← versions) {
      versionItem.add(option(value := s)(s).render)
    }
    setSelectedSubsystemVersion(versions.headOption, notifyListener = false)

  }

  // Gets the list of available versions for the given subsystem
  private def getSubsystemVersionOptions(subsystem: String): Future[List[String]] = {
    import upickle._
    Ajax.get(Routes.versionNames(subsystem)).map { r ⇒
      read[List[String]](r.responseText)
    }.recover {
      case ex ⇒
        ex.printStackTrace() // XXX TODO
        Nil
    }
  }

}
