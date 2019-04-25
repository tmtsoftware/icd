package icd.web.client

import icd.web.shared.SubsystemWithVersion
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
     *
     * @param sv          the selected and version
     * @param saveHistory if true, push the browser history state, otherwise not
     * @return a future indicating when changes are done
     */
    def subsystemSelected(sv: SubsystemWithVersion, saveHistory: Boolean = true): Future[Unit]
  }

  /**
   * Value displayed for the unpublished working version of the subsystem
   */
  private val unpublishedVersion = "*"
}

/**
 * Manages the subsystem and related subsystem version comboboxes
 *
 * @param listener          notified when the user makes a selection
 * @param labelStr          the text for the combobox label
 * @param placeholderMsg    the initial message to display before the first selection is made
 * @param enablePlaceholder if true, allow selecting the placeholder item
 */
case class Subsystem(
    listener:          SubsystemListener,
    labelStr:          String            = "Subsystem",
    placeholderMsg:    String            = "Select subsystem",
    enablePlaceholder: Boolean           = false
) extends Displayable {

  // The subsystem combobox
  private val subsystemItem = {
    import scalatags.JsDom.all._
    select(onchange := subsystemSelected _)(
      if (enablePlaceholder)
        option(value := placeholderMsg, selected := true)(placeholderMsg)
      else
        option(value := placeholderMsg, disabled := true, selected := true)(placeholderMsg)
    ).render
  }

  // The subsystem version combobox
  private val versionItem = {
    import scalatags.JsDom.all._
    select(hidden := true, onchange := subsystemVersionSelected _).render
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = subsystemItem.selectedIndex == 0

  // called when a subsystem is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    for (_ <- updateSubsystemVersionOptions())
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
      case `placeholderMsg` => None
      case subsystemName    => Some(subsystemName)
    }

  /**
   * Gets the list of subsystems being displayed
   */
  def getSubsystems: List[String] = {
    subsystemItem.options.drop(1).map(_.value).toList
  }

  /**
   * Gets the currently selected subsystem version (None for latest version)
   */
  def getSelectedSubsystemVersion: Option[String] =
    versionItem.value match {
      case `unpublishedVersion` | null | "" => None
      case version                          => Some(version)
    }

  /**
   * Gets the selected subsystem with the selected version
   */
  def getSubsystemWithVersion: SubsystemWithVersion =
    SubsystemWithVersion(getSelectedSubsystem, getSelectedSubsystemVersion)

  /**
   * Sets the selected subsystem and version.
   *
   * @param sv             the subsystem name and version to set
   * @param notifyListener if true, notify the listener
   * @param saveHistory    if true, save the current state to the browser history
   * @return a future indicating when any event handlers have completed
   */
  def setSubsystemWithVersion(
    sv:             SubsystemWithVersion,
    notifyListener: Boolean              = true,
    saveHistory:    Boolean              = true
  ): Future[Unit] = {
    if (sv == getSubsystemWithVersion)
      Future.successful()
    else {
      sv.subsystemOpt match {
        case Some(s) => subsystemItem.value = s
        case None    => subsystemItem.value = placeholderMsg
      }

      if (notifyListener) {
        for {
          _ <- updateSubsystemVersionOptions(sv.versionOpt)
          _ <- listener.subsystemSelected(getSubsystemWithVersion, saveHistory)
        } yield {
        }
      } else {
        updateSubsystemVersionOptions(sv.versionOpt)
      }
    }
  }

  /**
   * Update the Subsystem combobox options
   */
  def updateSubsystemOptions(items: List[String]): Unit = {
    for (i <- (1 until subsystemItem.length).reverse) {
      subsystemItem.remove(i)
    }
    items.foreach { str =>
      import scalatags.JsDom.all._
      subsystemItem.add(option(value := str)(str).render)
    }
    updateSubsystemVersionOptions() // Future!
  }

  // XXX TODO: Should it be allowed to view an ICD between a subsystem and itself?
  def disableOption(name: String): Unit = {
//    subsystemItem.options.drop(1).find(_.value == name).foreach { option =>
//      option.setAttribute("disabled", "true")
//    }
  }

  def setAllOptionsEnabled(): Unit = {
    subsystemItem.options.drop(1).foreach { option =>
      option.removeAttribute("disabled")
    }
  }

  def setEnabled(enabled: Boolean): Unit = {
    if (enabled) {
      subsystemItem.removeAttribute("disabled")
      versionItem.removeAttribute("disabled")
    } else {
      subsystemItem.setAttribute("disabled", "true")
      versionItem.setAttribute("disabled", "true")
    }
  }

  /**
   * Sets the selected subsystem version.
   *
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedSubsystemVersion(
    versionOpt:     Option[String],
    notifyListener: Boolean        = true,
    saveHistory:    Boolean        = true
  ): Future[Unit] = {
    if (versionOpt == getSelectedSubsystemVersion)
      Future.successful()
    else {
      versionOpt match {
        case Some(s) => versionItem.value = s
        case None    => versionItem.value = unpublishedVersion
      }
      if (notifyListener)
        listener.subsystemSelected(getSubsystemWithVersion, saveHistory)
      else Future.successful()
    }
  }

  // Updates the version combobox with the list of available versions for the selected subsystem
  // and selects the given version, if defined.
  // Returns a future indicating when done.
  def updateSubsystemVersionOptions(versionOpt: Option[String] = None): Future[Unit] = {
    versionItem.setAttribute("hidden", "true")
    getSelectedSubsystem match {
      case Some(subsystem) =>
        getSubsystemVersionOptions(subsystem).map { list => // Future!
          updateSubsystemVersionOptions(list)
          versionItem.removeAttribute("hidden")
          val version = versionOpt.getOrElse(unpublishedVersion)
          versionItem.value = version
        }.recover {
          case ex => ex.printStackTrace()
        }
      case None =>
        Future.successful()
    }
  }

  // Updates the version combobox with the given list of available versions for the selected subsystem
  private def updateSubsystemVersionOptions(versions: List[String]): Unit = {
    import scalatags.JsDom.all._
    while (versionItem.options.length != 0) {
      versionItem.remove(0)
    }
    // Insert unpublished working version (*) as first item
    for (s <- unpublishedVersion :: versions) {
      versionItem.add(option(value := s)(s).render)
    }
    setSelectedSubsystemVersion(versions.headOption, notifyListener = false)

  }

  // Gets the list of available versions for the given subsystem
  private def getSubsystemVersionOptions(subsystem: String): Future[List[String]] = {
    import play.api.libs.json._
    Ajax.get(Routes.versionNames(subsystem)).map { r =>
      Json.fromJson[List[String]](Json.parse(r.responseText)).get
    }.recover {
      case ex =>
        ex.printStackTrace() // XXX TODO
        Nil
    }
  }

}
