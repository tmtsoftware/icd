package icd.web.client

import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax
import shared.{ IcdName, IcdVersion }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import IcdChooser._

/**
 * Manages the ICD and related ICD version comboboxes
 */
object IcdChooser {

  /**
   * Type of a listener for changes in the selected ICD
   */
  trait IcdListener {
    /**
     * Called when an ICD is selected
     * @param icdVersionOpt the selected ICD, or None if no ICD is selected
     * @param saveHistory if true, push the browser history state, otherwise not
     * @return a future indicating when changes are done
     */
    def icdSelected(icdVersionOpt: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit]
  }

  val emptyOptionMsg = "Select an ICD"
}

/**
 * Manages the ICD and related ICD version comboboxes
 * @param listener notified when the user makes a selection
 */
case class IcdChooser(listener: IcdListener) extends Displayable {

  // The ICD combobox
  private val icdItem = {
    import scalatags.JsDom.all._
    select(onchange := icdSelected _)(
      option(value := emptyOptionMsg)(emptyOptionMsg)).render
  }

  // The ICD version combobox
  private val versionItem = {
    import scalatags.JsDom.all._
    select(hidden := true, onchange := icdVersionSelected _).render
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = icdItem.selectedIndex == 0

  // called when an ICD is selected
  private def icdSelected(e: dom.Event): Unit = {
    for (_ ← updateIcdVersionOptions())
      listener.icdSelected(getSelectedIcdVersion)
  }

  // called when an ICD version is selected
  private def icdVersionSelected(e: dom.Event): Unit = {
    listener.icdSelected(getSelectedIcdVersion)
  }

  // HTML markup displaying the ICD and version comboboxes
  override def markup(): Element = {
    import scalatags.JsDom.all._
    li(a(label("ICD", " ", icdItem), " ", versionItem)).render
  }

  /**
   * Gets the currently selected ICD name
   */
  def getSelectedIcd: Option[IcdName] = {
    import upickle._
    icdItem.value match {
      case `emptyOptionMsg` ⇒ None
      case json             ⇒ Some(read[IcdName](json))
    }
  }

  /**
   * Returns true if the latest ICD version is selected
   */
  def isLatestIcdVersionSelected: Boolean =
    versionItem.selectedIndex == 1

  /**
   * Gets the currently selected ICD version, or None if none is selected
   */
  def getSelectedIcdVersion: Option[IcdVersion] = {
    import upickle._
    versionItem.value match {
      case null | "" ⇒ None
      case json      ⇒ Some(read[IcdVersion](json))
    }
  }

  /**
   * Sets the selected ICD and version.
   * @param icdVersionOpt the ICD name and version to set, or None to set none
   * @param notifyListener if true, notify the listener
   * @param saveHistory if true, save the current state to the browser history
   * @return a future indicating when any event handlers have completed
   */
  def setIcdWithVersion(icdVersionOpt: Option[IcdVersion],
                        notifyListener: Boolean = true,
                        saveHistory: Boolean = true): Future[Unit] = {
    import upickle._
    icdVersionOpt match {
      case Some(icdVersion) ⇒
        icdItem.value = write(IcdName(icdVersion.subsystem, icdVersion.target)) // JSON
        versionItem.value = write(icdVersion) // JSON
        if (notifyListener)
          listener.icdSelected(icdVersionOpt, saveHistory)
        else Future.successful()
      case None ⇒
        icdItem.value = emptyOptionMsg
        versionItem.setAttribute("hidden", "true")
        if (notifyListener)
          listener.icdSelected(None, saveHistory)
        else Future.successful()
    }
  }

  // Update the ICD combobox options
  def updateIcdOptions(): Unit = {
    import upickle._
    Ajax.get(Routes.icdNames).map { r ⇒
      val icdNames = read[List[IcdName]](r.responseText)
      updateIcdOptions(icdNames)
    }.recover {
      case ex ⇒
        ex.printStackTrace() // XXX TODO
        Nil
    }
  }

  // Update the ICD combobox options
  def updateIcdOptions(icdNames: List[IcdName]): Unit = {
    import scalatags.JsDom.all._
    import upickle._

    val icdVersionOpt = getSelectedIcdVersion
    while (icdItem.options.length != 0) {
      icdItem.remove(0)
    }
    icdItem.add(option(value := emptyOptionMsg)(emptyOptionMsg).render)
    for (icdName ← icdNames) {
      icdItem.add(option(value := write(icdName))(icdName.toString).render)
    }
    for (_ ← updateIcdVersionOptions()) {
      setIcdWithVersion(icdVersionOpt)
    }
  }

  /**
   * Sets the selected ICD version.
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedIcdVersion(versionOpt: Option[IcdVersion],
                            notifyListener: Boolean = true,
                            saveHistory: Boolean = true): Future[Unit] = {
    import upickle._
    val selectedVersionOpt = getSelectedIcdVersion
    if (versionOpt == selectedVersionOpt)
      Future.successful()
    else {
      versionOpt match {
        case Some(s) ⇒ versionItem.value = write(s) // JSON
        case None    ⇒
      }
      if (notifyListener)
        listener.icdSelected(selectedVersionOpt, saveHistory)
      else Future.successful()
    }
  }

  // Updates the version combobox with the list of available versions for the selected ICD.
  // Returns a future indicating when done.
  def updateIcdVersionOptions(): Future[Unit] = {
    versionItem.setAttribute("hidden", "true")
    getSelectedIcd match {
      case Some(icdName) ⇒
        getIcdVersionOptions(icdName).map { list ⇒ // Future!
          updateIcdVersionOptions(list)
          versionItem.removeAttribute("hidden")
        }.recover {
          case ex ⇒ ex.printStackTrace()
        }
      case None ⇒
        Future.successful()
    }
  }

  // Updates the ICD version combobox with the given list of available versions for the selected ICD
  private def updateIcdVersionOptions(versions: List[IcdVersion]): Unit = {
    import scalatags.JsDom.all._
    import upickle._
    while (versionItem.options.length != 0) {
      versionItem.remove(0)
    }
    for (v ← versions) {
      // The option value is the JSON for the IcdVersion object
      versionItem.add(option(value := write(v))(v.icdVersion).render)
    }
    setSelectedIcdVersion(versions.headOption, notifyListener = false)
  }

  // Gets the list of available versions for the given ICD
  private def getIcdVersionOptions(icdName: IcdName): Future[List[IcdVersion]] = {
    import upickle._
    Ajax.get(Routes.icdVersionNames(icdName)).map { r ⇒
      read[List[IcdVersion]](r.responseText)
    }.recover {
      case ex ⇒
        ex.printStackTrace() // XXX TODO
        Nil
    }
  }

}
