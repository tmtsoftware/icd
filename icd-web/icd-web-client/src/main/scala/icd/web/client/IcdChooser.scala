package icd.web.client

import icd.web.shared.{ SubsystemWithVersion, IcdVersionInfo, IcdVersion, IcdName }
import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.{ Promise, Future }
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

  private val emptyOptionMsg = "Select an ICD"
  private val unpublishedVersion = "*"
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
    import upickle.default._
    icdItem.value match {
      case `emptyOptionMsg` ⇒ None
      case json             ⇒ Some(read[IcdName](json))
    }
  }

  /**
   * Gets the currently selected ICD version, or None if none is selected
   */
  def getSelectedIcdVersion: Option[IcdVersion] = {
    import upickle.default._
    versionItem.value match {
      case `unpublishedVersion` | null | "" ⇒ None
      case json                             ⇒ Some(read[IcdVersion](json))
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
    import upickle.default._
    icdVersionOpt match {
      case Some(icdVersion) ⇒
        icdItem.value = write(IcdName(icdVersion.subsystem, icdVersion.target)) // JSON
        versionItem.value = write(icdVersion) // JSON
        versionItem.removeAttribute("hidden")
        if (notifyListener)
          listener.icdSelected(icdVersionOpt, saveHistory)
        else Future.successful()
      case None ⇒
        icdItem.value = emptyOptionMsg
        versionItem.value = unpublishedVersion
        versionItem.setAttribute("hidden", "true")
        if (notifyListener)
          listener.icdSelected(None, saveHistory)
        else Future.successful()
    }
  }

  /**
   * Gets the list of ICDs being displayed
   */
  def getIcds: List[IcdName] = {
    import upickle.default._
    icdItem.options.drop(1).map(s ⇒ read[IcdName](s.value)).toList
  }

  /**
   * If there is an ICD matching the given subsystem and target versions, select it in the ICD chooser
   * @param sv the source subsystem and version
   * @param tv the target subsystem and version
   */
  def selectMatchingIcd(sv: SubsystemWithVersion, tv: SubsystemWithVersion): Future[Unit] = {
    // Select none as the default, in case a matching ICD is not found
    setIcdWithVersion(None, notifyListener = false, saveHistory = false)
    val p = Promise[Unit]()
    val icdNames = for {
      subsystem ← sv.subsystemOpt
      subsystemVersion ← sv.versionOpt
      target ← tv.subsystemOpt
      targetVersion ← tv.versionOpt
      icd ← getIcds.find(i ⇒ i.subsystem == subsystem && i.target == target)
    } yield {
      for (icdVersionList ← getIcdVersionOptions(icd) recover { case ex ⇒ p.failure(ex); Nil }) {
        val icdVersionOpt = icdVersionList.find(i ⇒ i.subsystemVersion == subsystemVersion && i.targetVersion == targetVersion)
        if (icdVersionOpt.isDefined) {
          for {
            _ ← updateIcdVersionOptions(icdVersionList) recover { case ex ⇒ p.failure(ex) }
            _ ← setIcdWithVersion(icdVersionOpt, notifyListener = false, saveHistory = false)
          } p.success(())
        } else p.success(())
      }
      icd
    }
    if (icdNames.isEmpty) Future.successful() else p.future
  }

  // Update the ICD combobox options
  def updateIcdOptions(): Future[Unit] = {

    import upickle.default._

    Ajax.get(Routes.icdNames).flatMap {
      r ⇒
        val icdNames = read[List[IcdName]](r.responseText)
        updateIcdOptions(icdNames)
    }.recover {
      case ex ⇒
        ex.printStackTrace() // XXX TODO
        Nil
    }
  }

  // Update the ICD combobox options
  private def updateIcdOptions(icdNames: List[IcdName]): Future[Unit] = {

    import scalatags.JsDom.all._
    import upickle.default._

    val icdVersionOpt = getSelectedIcdVersion
    while (icdItem.options.length != 0) {
      icdItem.remove(0)
    }
    icdItem.add(option(value := emptyOptionMsg)(emptyOptionMsg).render)
    for (icdName ← icdNames) {
      icdItem.add(option(value := write(icdName))(icdName.toString).render)
    }

    // restore selected ICD (updateIcdVersionOptions() below depends on an ICD being selected)
    icdVersionOpt.foreach {
      icdVersion ⇒
        icdItem.value = write(IcdName(icdVersion.subsystem, icdVersion.target)) // JSON
    }

    // Update version options
    for {
      _ ← updateIcdVersionOptions()
      _ ← setIcdWithVersion(icdVersionOpt)
    } yield ()
  }

  /**
   * Sets the selected ICD version.
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedIcdVersion(versionOpt: Option[IcdVersion],
                            notifyListener: Boolean = true,
                            saveHistory: Boolean = true): Future[Unit] = {

    import upickle.default._

    val selectedVersionOpt = getSelectedIcdVersion
    if (versionOpt == selectedVersionOpt)
      Future.successful()
    else {
      versionOpt match {
        case Some(s) ⇒
          versionItem.value = write(s) // JSON
          versionItem.removeAttribute("hidden")
        case None ⇒
          versionItem.value = unpublishedVersion
          versionItem.setAttribute("hidden", "true")
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
        getIcdVersionOptions(icdName).flatMap {
          list ⇒ // Future!
            versionItem.removeAttribute("hidden")
            updateIcdVersionOptions(list)
        }.recover {
          case ex ⇒ ex.printStackTrace()
        }
      case None ⇒
        Future.successful()
    }
  }

  // Updates the ICD version combobox with the given list of available versions for the selected ICD
  private def updateIcdVersionOptions(versions: List[IcdVersion]): Future[Unit] = {

    import scalatags.JsDom.all._
    import upickle.default._

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

    import upickle.default._

    Ajax.get(Routes.icdVersions(icdName)).map {
      r ⇒
        read[List[IcdVersionInfo]](r.responseText)
    }.recover {
      case ex ⇒
        ex.printStackTrace() // XXX TODO
        Nil
    }.map(list ⇒ list.map(_.icdVersion))
  }

}
