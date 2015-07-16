package controllers

import java.io.ByteArrayOutputStream

import csw.services.icd.IcdToPdf
import csw.services.icd.db.{ IcdDbPrinter, IcdComponentInfo, ComponentInfoHelper, IcdDb }
import icd.web.shared.{ IcdVersion, SubsystemWithVersion, SubsystemInfo, IcdName, VersionInfo, Csrf }
import play.api.mvc._
import play.filters.csrf.CSRFAddToken
import play.api.libs.json._

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
    val info = ComponentInfoHelper(db, subsystem, versionOpt, compName)
    val json = write(info)
    Ok(json).as(JSON)
  }

  /**
   * Gets information about a component in a given version of an ICD
   * @param subsystem the source subsystem
   * @param versionOpt the source subsystem's version (default: current)
   * @param compName the source component name
   * @param target the target subsystem
   * @param targetVersionOpt the target subsystem's version
   */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compName: String,
                       target: String, targetVersionOpt: Option[String]) = Action {
    import upickle._
    val info = IcdComponentInfo(db, subsystem, versionOpt, compName, target, targetVersionOpt)
    val json = write(info)
    Ok(json).as(JSON)
  }

  /**
   * Returns the PDF for the given ICD
   * @param subsystem the source subsystem
   * @param versionOpt the source subsystem's version (default: current)
   * @param compNamesOpt an optional comma separated list of component names to include (default: all)
   * @param target the target subsystem
   * @param targetVersionOpt optional target subsystem's version (default: current)
   * @param icdVersionOpt optional ICD version (default: current)
   */
  def icdAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String],
               target: String, targetVersionOpt: Option[String],
               icdVersionOpt: Option[String]) = Action {
    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) ⇒ s.split(",").toList
      case None    ⇒ db.versionManager.getComponentNames(subsystem, versionOpt)
    }
    val sv = SubsystemWithVersion(Some(subsystem), versionOpt)
    val tv = SubsystemWithVersion(Some(target), targetVersionOpt)
    val iv = for {
      version ← versionOpt
      targetVersion ← targetVersionOpt
      icdVersion ← icdVersionOpt
    } yield IcdVersion(icdVersion, subsystem, version, target, targetVersion)
    IcdDbPrinter(db).getAsHtml(compNames, sv, tv, iv) match {
      case Some(html) ⇒
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None ⇒
        NotFound
    }
  }

  /**
   * Returns the PDF for the given subsystem API
   * @param subsystem the source subsystem
   * @param versionOpt the source subsystem's version (default: current)
   * @param compNamesOpt an optional comma separated list of component names to include (default: all)
   */
  def apiAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String]) = Action {
    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) ⇒ s.split(",").toList
      case None    ⇒ db.versionManager.getComponentNames(subsystem, versionOpt)
    }
    val sv = SubsystemWithVersion(Some(subsystem), versionOpt)
    val tv = SubsystemWithVersion(None, None)
    IcdDbPrinter(db).getAsHtml(compNames, sv, tv, None) match {
      case Some(html) ⇒
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None ⇒
        NotFound
    }
  }

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
