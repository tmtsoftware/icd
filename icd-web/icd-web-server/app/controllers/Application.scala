package controllers

import java.io.ByteArrayOutputStream

import csw.services.icd.IcdToPdf
import csw.services.icd.db.{ IcdDbPrinter, IcdDb }
import play.Play
import play.api.mvc._
import play.filters.csrf.CSRFAddToken
import shared.{ IcdVersionInfo, Csrf }
import play.api.libs.json._

/**
 * Provides the interface between the web client and the server
 */
object Application extends Controller {
  val databaseName = Play.application().configuration().getString("database.name")
  val db = IcdDb(databaseName)

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
   * Gets a list of components belonging to the given version of the given subsystem
   */
  def components(subsystem: String, versionOpt: Option[String]) = Action {
    val names = db.versionManager.getComponentNames(subsystem, versionOpt)
    Ok(Json.toJson(names))
  }

  /**
   * Gets information about a named component in the given version of the given subsystem
   */
  def componentInfo(subsystem: String, compName: String, versionOpt: Option[String]) = Action {
    import upickle._
    val info = ComponentInfo(db, subsystem, versionOpt, compName)
    val json = write(info)
    Ok(json).as(JSON)
  }

  // Gets the HTML for the named subsystem or component (without inserting any CSS)
  private def getAsPlainHtml(name: String): String = {
    val html = IcdDbPrinter(db.query).getAsPlainHtml(name)
    html
  }

  // Gets the HTML for the named subsystem or component
  private def getAsHtml(name: String): String = {
    val html = IcdDbPrinter(db.query).getAsHtml(name)
    html
  }

  /**
   * Returns the HTML API for the subsystem or component with the given name
   */
  def apiAsHtml(name: String) = Action {
    Ok(getAsPlainHtml(name)).as(HTML)
  }

  /**
   * Returns the PDF for the API with the given name
   * @param name the subsystem or component name
   */
  def apiAsPdf(name: String) = Action {
    val out = new ByteArrayOutputStream()
    IcdToPdf.saveAsPdf(out, getAsHtml(name))
    val bytes = out.toByteArray
    Ok(bytes).as("application/pdf")
  }

  /**
   * Returns a detailed list of the versions of the given subsystem (or subsystem.component)
   */
  def getVersions(name: String) = Action {
    import upickle._
    val versions = db.versionManager.getVersions(name).map(v ⇒
      IcdVersionInfo(v.versionOpt, v.user, v.comment, v.date.toString))
    Ok(write(versions)).as(JSON)
  }

  /**
   * Returns a list of version names for the given subsystem or (subsystem.component)
   */
  def getVersionNames(name: String) = Action {
    import upickle._
    val versions = db.versionManager.getVersionNames(name)
    Ok(write(versions)).as(JSON)
  }

  /**
   * Publishes the given version of the given subsystem
   */
  def publishApi(path: String, majorVersion: Boolean, comment: String) = Action {
    // XXX error handling?
    db.versionManager.publishApi(path, comment, majorVersion)
    Ok.as(JSON)
  }

  /**
   * Publishes an ICD from the given version of the given subsystem to the target subsystem and version
   */
  def publishIcd(subsystem: String, version: String, target: String, targetVersion: String, comment: String) = Action {
    // XXX error handling?
    db.versionManager.publishIcd(subsystem, version, target, targetVersion, comment)
    Ok.as(JSON)
  }
}
