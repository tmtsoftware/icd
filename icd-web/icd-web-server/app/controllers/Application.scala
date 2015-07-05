package controllers

import java.io.ByteArrayOutputStream

import csw.services.icd.IcdToPdf
import csw.services.icd.db.{ IcdComponentInfo, ComponentInfo, IcdDbPrinter, IcdDb }
import play.Play
import play.api.mvc._
import play.filters.csrf.CSRFAddToken
import play.api.libs.json._
import shared.{ IcdName, VersionInfo, SubsystemInfo, Csrf }

/**
 * Provides the interface between the web client and the server
 */
object Application extends Controller {
  val db = IcdDb()

  def index = CSRFAddToken {
    Action { implicit request ⇒
      import play.filters.csrf.CSRF
      val token = CSRF.getToken(request).map(t ⇒ Csrf(t.value)).getOrElse(Csrf(""))
      Ok(views.html.index(token))
    }
  }

  /**
   * Gets a list of top level subsystem names
   */
  def subsystemNames = Action {
    val names = db.query.getSubsystemNames
    Ok(Json.toJson(names))
  }

  /**
   * Gets information about a named subsystem
   */
  def subsystemInfo(subsystem: String, versionOpt: Option[String]) = Action {
    import upickle._
    db.versionManager.getSubsystemModel(subsystem, versionOpt) match {
      case Some(model) ⇒
        val info = SubsystemInfo(model.name, versionOpt, model.title, model.description)
        val json = write(info)
        Ok(json).as(JSON)
      case None ⇒
        NotFound
    }
  }

  /**
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, versionOpt: Option[String]) = Action {
    val names = db.versionManager.getComponentNames(subsystem, versionOpt)
    Ok(Json.toJson(names))
  }

  /**
   * Gets information about a named component in the given version of the given subsystem
   * @param subsystem the subsystem
   * @param versionOpt the subsystem's version (default: current)
   * @param compName the component name
   */
  def componentInfo(subsystem: String, versionOpt: Option[String], compName: String) = Action {
    import upickle._
    val info = ComponentInfo(db, subsystem, versionOpt, compName)
    val json = write(info)
    Ok(json).as(JSON)
  }

  /**
   * Gets information about a component in a given version of an ICD
   * @param subsystem the source subsystem
   * @param versionOpt the source subsystem's version (default: current)
   * @param compName the source component name
   * @param target the target subsystem
   * @param targetVersion the target subsystem's version
   */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compName: String,
                       target: String, targetVersion: Option[String]) = Action {
    import upickle._
    val info = IcdComponentInfo(db, subsystem, versionOpt, compName, target, targetVersion)
    val json = write(info)
    Ok(json).as(JSON)
  }

  //  // Gets the HTML for the named subsystem or component (without inserting any CSS)
  //  private def getAsPlainHtml(name: String): String = {
  //    val html = IcdDbPrinter(db.query).getAsPlainHtml(name)
  //    html
  //  }
  //
  //  // Gets the HTML for the named subsystem or component
  //  private def getAsHtml(name: String): String = {
  //    val html = IcdDbPrinter(db.query).getAsHtml(name)
  //    html
  //  }
  //
  //  /**
  //   * Returns the HTML API for the subsystem or component with the given name
  //   */
  //  def apiAsHtml(name: String) = Action {
  //    Ok(getAsPlainHtml(name)).as(HTML)
  //  }
  //
  //  /**
  //   * Returns the PDF for the API with the given name
  //   * @param name the subsystem or component name
  //   */
  //  def apiAsPdf(name: String) = Action {
  //    val out = new ByteArrayOutputStream()
  //    IcdToPdf.saveAsPdf(out, getAsHtml(name))
  //    val bytes = out.toByteArray
  //    Ok(bytes).as("application/pdf")
  //  }

  /**
   * Returns a detailed list of the versions of the given subsystem
   */
  def getVersions(subsystem: String) = Action {
    import upickle._
    val versions = db.versionManager.getVersions(subsystem).map(v ⇒
      VersionInfo(v.versionOpt, v.user, v.comment, v.date.toString))
    Ok(write(versions)).as(JSON)
  }

  /**
   * Returns a list of version names for the given subsystem
   */
  def getVersionNames(subsystem: String) = Action {
    import upickle._
    val versions = db.versionManager.getVersionNames(subsystem)
    Ok(write(versions)).as(JSON)
  }

  /**
   * Publishes the given version of the given subsystem
   */
  def publishApi(path: String, majorVersion: Boolean, comment: String) = Action {
    // XXX error handling?
    db.versionManager.publishApi(path, majorVersion, comment)
    Ok.as(JSON)
  }

  /**
   * Publishes an ICD from the given version of the given subsystem to the target subsystem and version
   */
  def publishIcd(subsystem: String, version: String,
                 target: String, targetVersion: String,
                 majorVersion: Boolean, comment: String) = Action {
    // XXX error handling?
    db.versionManager.publishIcd(subsystem, version, target, targetVersion, majorVersion, comment)
    Ok.as(JSON)
  }

  /**
   * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
   */
  def getIcdNames = Action {
    import upickle._
    // convert list to use shared IcdName class
    val list = db.versionManager.getIcdNames.map(icd ⇒ IcdName(icd.subsystem, icd.target))
    Ok(write(list)).as(JSON)
  }

  /**
   * Gets a list of versions for the ICD from subsystem to target subsystem
   */
  def getIcdVersions(subsystem: String, target: String) = Action {
    import upickle._
    // convert list to use shared IcdVersion class
    val list = db.versionManager.getIcdVersions(subsystem, target)
    Ok(write(list)).as(JSON)
  }
}
