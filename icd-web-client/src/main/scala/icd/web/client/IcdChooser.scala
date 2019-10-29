package icd.web.client

import icd.web.shared.{SubsystemWithVersion, IcdVersionInfo, IcdVersion, IcdName}
import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.ext.Ajax

import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import IcdChooser._
import play.api.libs.json._

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
     *
     * @param maybeIcdVersion the selected ICD, or None if no ICD is selected
     * @param saveHistory     if true, push the browser history state, otherwise not
     * @return a future indicating when changes are done
     */
    def icdSelected(maybeIcdVersion: Option[IcdVersion], saveHistory: Boolean = true): Future[Unit]
  }

  private val emptyOptionMsg     = "Select an ICD"
  private val unpublishedVersion = "*"
}

/**
 * Manages the ICD and related ICD version comboboxes
 *
 * @param listener notified when the user makes a selection
 */
//noinspection DuplicatedCode
case class IcdChooser(listener: IcdListener) extends Displayable {

  import icd.web.shared.JsonSupport._

  // The ICD combobox
  private val icdItem = {
    import scalatags.JsDom.all._
    select(cls := "form-control", onchange := icdSelected _)(
      option(value := emptyOptionMsg)(emptyOptionMsg)
    ).render
  }

  // The ICD version combobox
  private val versionItem = {
    import scalatags.JsDom.all._
    select(cls := "form-control", onchange := icdVersionSelected _).render
  }

  def setEnabled(enabled: Boolean): Unit = {
    if (enabled) {
      icdItem.removeAttribute("disabled")
      versionItem.removeAttribute("disabled")
    } else {
      icdItem.setAttribute("disabled", "true")
      versionItem.setAttribute("disabled", "true")
    }
  }

  /**
   * Returns true if the combobox is displaying the default item (i.e.: the initial item, no selection)
   */
  def isDefault: Boolean = icdItem.selectedIndex == 0

  // called when an ICD is selected
  private def icdSelected(e: dom.Event): Unit = {
    for (_ <- updateIcdVersionOptions())
      listener.icdSelected(getSelectedIcdVersion)
  }

  // called when an ICD version is selected
  private def icdVersionSelected(e: dom.Event): Unit = {
    listener.icdSelected(getSelectedIcdVersion)
  }

  // HTML markup displaying the ICD and version comboboxes
  override def markup(): Element = {
    import scalatags.JsDom.all._
    import scalacss.ScalatagsCss._
    div(cls := "row")(
      div(Styles.selectDialogLabel)(label("ICD")),
      div(Styles.selectDialogSubsystem)(icdItem),
      div(Styles.selectDialogVersion)(versionItem)
    ).render
  }

  /**
   * Gets the currently selected ICD name
   */
  def getSelectedIcd: Option[IcdName] = {
    icdItem.value match {
      case `emptyOptionMsg` => None
      case json =>
        Json.fromJson[IcdName](Json.parse(json)) match {
          case JsSuccess(icdName: IcdName, _: JsPath) => Some(icdName)
          case _: JsError                             => None
        }
    }
  }

  /**
   * Gets the currently selected ICD version, or None if none is selected
   */
  def getSelectedIcdVersion: Option[IcdVersion] = {
    versionItem.value match {
      case `unpublishedVersion` | null | "" => None
      case json =>
        Json.fromJson[IcdVersion](Json.parse(json)) match {
          case JsSuccess(icdVersion: IcdVersion, _: JsPath) => Some(icdVersion)
          case e: JsError =>
            println(s"Error: ${e.errors}")
            None
        }
    }
  }

  /**
   * Sets the selected ICD and version.
   *
   * @param maybeIcdVersion the ICD name and version to set, or None to set none
   * @param notifyListener  if true, notify the listener
   * @param saveHistory     if true, save the current state to the browser history
   * @return a future indicating when any event handlers have completed
   */
  def setIcdWithVersion(
      maybeIcdVersion: Option[IcdVersion],
      notifyListener: Boolean = true,
      saveHistory: Boolean = true
  ): Future[Unit] = {
    if (maybeIcdVersion == getSelectedIcdVersion)
      Future.successful()
    else {
      maybeIcdVersion match {
        case Some(icdVersion) =>
          icdItem.value = Json.toJson(IcdName(icdVersion.subsystem, icdVersion.target)).toString() // JSON
          versionItem.value = Json.toJson(icdVersion).toString()                                   // JSON
          if (notifyListener)
            listener.icdSelected(maybeIcdVersion, saveHistory)
          else Future.successful()
        case None =>
          icdItem.value = emptyOptionMsg
          versionItem.value = unpublishedVersion
          if (notifyListener)
            listener.icdSelected(None, saveHistory)
          else Future.successful()
      }
    }
  }

  /**
   * Gets the list of ICDs being displayed
   */
  def getIcds: List[IcdName] = {
    val icds = icdItem.options.toList.drop(1)
    icds.map(s => Json.fromJson[IcdName](Json.parse(s.value)).get)
  }

  /**
   * If there is an ICD matching the given subsystem and target versions, select it in the ICD chooser
   *
   * @param sv the source subsystem and version
   * @param maybeTargetSv optional target subsystem and version
   */
  def selectMatchingIcd(sv: SubsystemWithVersion, maybeTargetSv: Option[SubsystemWithVersion]): Future[Unit] = {
    // Select none as the default, in case a matching ICD is not found
    setIcdWithVersion(None, notifyListener = false, saveHistory = false)
    updateIcdVersionOptions(Nil)
    val p = Promise[Unit]()
    val icdNames = for {
      subsystemVersion <- sv.maybeVersion
      targetSv         <- maybeTargetSv
      targetVersion    <- targetSv.maybeVersion
      icd              <- getIcds.find(i => i.subsystem == sv.subsystem && i.target == targetSv.subsystem)
    } yield {
      for (icdVersionList <- getIcdVersionOptions(icd) recover { case ex => p.failure(ex); Nil }) {
        val maybeIcdVersion =
          icdVersionList.find(i => i.subsystemVersion == subsystemVersion && i.targetVersion == targetVersion)
        if (maybeIcdVersion.isDefined) {
          for {
            _ <- updateIcdVersionOptions(icdVersionList) recover { case ex => p.failure(ex) }
            _ <- setIcdWithVersion(maybeIcdVersion, notifyListener = false, saveHistory = false)
          } p.success(())
        } else p.success(())
      }
      icd
    }
    if (icdNames.isEmpty) Future.successful() else p.future
  }

  // Update the ICD combobox options
  def updateIcdOptions(): Future[Unit] = {
    Ajax
      .get(Routes.icdNames)
      .flatMap { r =>
        val icdNames = Json.fromJson[Array[IcdName]](Json.parse(r.responseText)).map(_.toList).getOrElse(Nil)
        updateIcdOptions(icdNames)
      }
      .recover {
        case ex =>
          ex.printStackTrace() // XXX TODO
          Nil
      }
  }

  // Update the ICD combobox options
  private def updateIcdOptions(icdNames: List[IcdName]): Future[Unit] = {

    import scalatags.JsDom.all._

    val maybeIcdVersion = getSelectedIcdVersion
    while (icdItem.options.length != 0) {
      icdItem.remove(0)
    }
    icdItem.add(option(value := emptyOptionMsg)(emptyOptionMsg).render)
    for (icdName <- icdNames) {
      icdItem.add(option(value := Json.toJson(icdName).toString())(icdName.toString).render)
    }

    // restore selected ICD (updateIcdVersionOptions() below depends on an ICD being selected)
    maybeIcdVersion.foreach { icdVersion =>
      icdItem.value = Json.toJson(IcdName(icdVersion.subsystem, icdVersion.target)).toString // JSON
    }

    // Update version options
    for {
      _ <- updateIcdVersionOptions()
      _ <- setIcdWithVersion(maybeIcdVersion)
    } yield ()
  }

  /**
   * Sets the selected ICD version.
   *
   * @return a future indicating when any event handlers have completed
   */
  def setSelectedIcdVersion(
      maybeVersion: Option[IcdVersion],
      notifyListener: Boolean = true,
      saveHistory: Boolean = true
  ): Future[Unit] = {

    val maybeSelectedVersion = getSelectedIcdVersion
    if (maybeVersion == maybeSelectedVersion)
      Future.successful()
    else {
      maybeVersion match {
        case Some(s) =>
          versionItem.value = Json.toJson(s).toString() // JSON
        case None =>
          versionItem.value = unpublishedVersion
      }
      if (notifyListener)
        listener.icdSelected(maybeSelectedVersion, saveHistory)
      else Future.successful()
    }
  }

  // Updates the version combobox with the list of available versions for the selected ICD.
  // Returns a future indicating when done.
  def updateIcdVersionOptions(): Future[Unit] = {
    getSelectedIcd match {
      case Some(icdName) =>
        getIcdVersionOptions(icdName)
          .flatMap { list => // Future!
            updateIcdVersionOptions(list)
          }
          .recover {
            case ex => ex.printStackTrace()
          }
      case None =>
        updateIcdVersionOptions(Nil)
        versionItem.value = unpublishedVersion
        Future.successful()
    }
  }

  // Updates the ICD version combobox with the given list of available versions for the selected ICD
  private def updateIcdVersionOptions(versions: List[IcdVersion]): Future[Unit] = {

    import scalatags.JsDom.all._

    while (versionItem.options.length != 0) {
      versionItem.remove(0)
    }
    for (v <- versions) {
      // The option value is the JSON for the IcdVersion object
      versionItem.add(option(value := Json.toJson(v).toString())(v.icdVersion).render)
    }
    setSelectedIcdVersion(versions.headOption, notifyListener = false)
  }

  // Gets the list of available versions for the given ICD
  private def getIcdVersionOptions(icdName: IcdName): Future[List[IcdVersion]] = {
    Ajax
      .get(Routes.icdVersions(icdName))
      .map { r =>
        Json.fromJson[Array[IcdVersionInfo]](Json.parse(r.responseText)).map(_.toList).getOrElse(Nil)
      }
      .recover {
        case ex =>
          ex.printStackTrace() // XXX TODO
          Nil
      }
      .map(list => list.map(_.icdVersion))
  }

}
