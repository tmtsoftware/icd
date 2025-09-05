package icd.web.client

import icd.web.shared.SubsystemWithVersion
import org.scalajs.dom

import scala.concurrent.Future
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import Subsystem.*
import org.scalajs.dom.Element

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
     * @param maybeSv     optional selected subsystem and version
     * @param findMatchingIcd if true, update the ICD item if a matching published ICD can be found
     * @return a future indicating when changes are done
     */
    def subsystemSelected(
        maybeSv: Option[SubsystemWithVersion],
        findMatchingIcd: Boolean = true
    ): Future[Unit]
  }

  private val componentPlaceholder = "All Components"
}

/**
 * Manages the subsystem and related subsystem version comboboxes
 *
 * @param listener          notified when the user makes a selection
 * @param labelStr          the text for the combobox label
 */
//noinspection DuplicatedCode
case class Subsystem(
    listener: SubsystemListener,
    labelStr: String = "Subsystem"
) extends Displayable {

  private val placeholderMsg = "Select subsystem"

  // The subsystem combobox
  private val subsystemItem = {
    import scalatags.JsDom.all.*
    select(cls := "form-select", onchange := subsystemSelected)(
      option(value := placeholderMsg, selected := true)(placeholderMsg)
    ).render
  }

  // The subsystem version combobox
  private val versionItem = {
    import scalatags.JsDom.all.*
    select(cls := "form-select", onchange := subsystemVersionSelected).render
  }

  // The component combobox
  private val componentItem = {
    import scalatags.JsDom.all.*
    select(cls := "form-select")(
      option(value := componentPlaceholder, selected := true)(componentPlaceholder)
    ).render
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = subsystemItem.selectedIndex == 0

  override def setEnabled(enabled: Boolean): Unit = {
    subsystemItem.disabled = !enabled
    versionItem.disabled = !enabled
    componentItem.disabled = !enabled
  }

  // noinspection ScalaUnusedSymbol
  // called when a subsystem is selected
  private def subsystemSelected(e: dom.Event): Unit = {
    val f = for {
      _ <- updateSubsystemVersionOptions()
      x <- listener.subsystemSelected(getSubsystemWithVersion(subsystemOnly = true))
    } yield x
    showBusyCursorWhile(f)
  }

  // noinspection ScalaUnusedSymbol
  // called when a subsystem version is selected
  private def subsystemVersionSelected(e: dom.Event): Unit = {
    showBusyCursorWhile(
      listener.subsystemSelected(getSubsystemWithVersion())
    )
  }

  // HTML markup displaying the subsystem and version comboboxes
  override def markup(): Element = {
    import scalatags.JsDom.all.*

    div(cls := "row")(
      div(cls := "selectDialogLabel col-1")(label(s"$labelStr")),
      div(cls := "selectDialogSubsystem col-2")(subsystemItem),
      div(cls := "selectDialogVersion col-1")(versionItem),
      div(cls := "selectDialogComponent col-4")(componentItem)
    ).render
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
   * Gets the currently selected component name
   */
  def getSelectedComponent: Option[String] = {
    componentItem.value match {
      case `componentPlaceholder` | "" => None
      case componentName               => Some(componentName)
    }
  }

  /**
   * Gets the list of subsystems being displayed
   */
  def getSubsystems: List[String] = {
    val items = subsystemItem.options.toList
    items.drop(1).map(_.value)
  }

//  /**
//   * Gets the list of component names being displayed
//   */
//  def getComponents: List[String] = {
//    componentItem.options.toList.drop(1).map(_.value)
//  }

  /**
   * Gets the currently selected subsystem version (None for latest version)
   */
  def getSelectedSubsystemVersion: Option[String] =
    versionItem.value match {
      case null | "" => None
      case version   => Some(version)
    }

  /**
   * Gets the selected subsystem with the selected version and component.
   * If subsystemOnly is true, gets only the subsystem with no version or component selected.
   */
  def getSubsystemWithVersion(subsystemOnly: Boolean = false): Option[SubsystemWithVersion] = {
    getSelectedSubsystem.map(subsystem =>
      SubsystemWithVersion(
        subsystem,
        if (subsystemOnly) None else getSelectedSubsystemVersion,
        if (subsystemOnly) None else getSelectedComponent
      )
    )
  }

  /**
   * Sets (or clears) the selected subsystem and version.
   *
   * @param maybeSv        optional subsystem name and version to set (clear if None)
   * @param findMatchingIcd if true, update the ICD item if a matching published ICD can be found
   * @return a future indicating when any event handlers have completed
   */
  def setSubsystemWithVersion(
      maybeSv: Option[SubsystemWithVersion],
      findMatchingIcd: Boolean = true
  ): Future[Unit] = {
    if (maybeSv == getSubsystemWithVersion())
      Future.successful(())
    else {
      maybeSv match {
        case Some(sv) =>
          subsystemItem.value = sv.subsystem
          sv.maybeVersion match {
            case Some(version) =>
              versionItem.value = version
            case None =>
              versionItem.value = ""
          }
          sv.maybeComponent match {
            case Some(component) =>
              componentItem.value = component
            case None =>
              componentItem.value = componentPlaceholder
          }
        case None =>
          subsystemItem.value = placeholderMsg
          versionItem.value = ""
          componentItem.value = componentPlaceholder
      }
    }

    showBusyCursorWhile {
      for {
        _ <- updateSubsystemVersionOptions(maybeSv.flatMap(_.maybeVersion))
        _ <- listener.subsystemSelected(maybeSv, findMatchingIcd = false)
      } yield {}
    }
  }

  /**
   * Update the Subsystem combobox options
   */
  def updateSubsystemOptions(items: List[String]): Future[Unit] = {
    val currentSubsystems = getSubsystems
    items.foreach { subsystem =>
      import scalatags.JsDom.all.*
      if (!currentSubsystems.contains(subsystem))
        subsystemItem.add(option(value := subsystem)(subsystem).render)
    }
    updateSubsystemVersionOptions()
  }

  /**
   * Update the Component combobox options
   */
  def updateComponentOptions(items: List[String]): Unit = {
    for (i <- (1 until componentItem.length).reverse) {
      componentItem.remove(i)
    }
    items.foreach { str =>
      import scalatags.JsDom.all.*
      componentItem.add(option(value := str)(str).render)
    }
  }

//  /**
//   * Sets the selected subsystem version.
//   *
//   * @return a future indicating when any event handlers have completed
//   */
//  def setSelectedSubsystemVersion(version: String): Future[Unit] = {
//    if (getSelectedSubsystemVersion.contains(version))
//      Future.successful(())
//    else
//      versionItem.value = version
//    listener.subsystemSelected(getSubsystemWithVersion(), findMatchingIcd = false)
//  }

  /**
   * Sets the selected subsystem component.
   *
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedComponent(maybeComponent: Option[String]): Unit = {
    if (maybeComponent != getSelectedComponent)
      maybeComponent match {
        case Some(s) => componentItem.value = s
        case None    => componentItem.value = componentPlaceholder
      }
  }

  // Updates the version combobox with the list of available versions for the selected subsystem
  // and selects the given version, if defined.
  // Returns a future indicating when done.
  def updateSubsystemVersionOptions(maybeVersion: Option[String] = None): Future[Unit] = {
    getSelectedSubsystem match {
      case Some(subsystem) =>
        getSubsystemVersionOptions(subsystem)
          .map { list => // Future!
            def getDefaultVersion: String = {
              val publishedVersions = list.filter(v => v != masterVersion && v != uploadedVersion)
              val v                 = if (publishedVersions.isEmpty) list.headOption else publishedVersions.headOption
              v.getOrElse(masterVersion)
            }
            updateSubsystemVersionOptions(list)
            val version = maybeVersion.getOrElse(getDefaultVersion)
            versionItem.value = version
          }
      case None =>
        versionItem.value = ""
        updateSubsystemVersionOptions(Nil)
        Future.successful(())
    }
  }

  // Updates the version combobox with the given list of available versions for the selected subsystem
  private def updateSubsystemVersionOptions(versions: List[String]): Unit = {
    import scalatags.JsDom.all.*
    while (versionItem.options.length != 0) {
      versionItem.remove(0)
    }
    for (s <- versions) {
      versionItem.add(option(value := s)(s).render)
    }
  }

  // Gets the list of available versions for the given subsystem
  private def getSubsystemVersionOptions(subsystem: String): Future[List[String]] = {
    import play.api.libs.json.*
    Fetch
      .get(ClientRoutes.versionNames(subsystem))
      .map { text =>
        Json.fromJson[Array[String]](Json.parse(text)).get.toList
      }
  }

}
