package controllers

import java.io.ByteArrayOutputStream
import javax.inject.Inject

import csw.services.icd.IcdToPdf
import csw.services.icd.db.IcdVersionManager.VersionDiff
import csw.services.icd.db.{ComponentInfoHelper, IcdComponentInfo, IcdDb, IcdDbPrinter}
import csw.services.icd.html.HtmlMarkup
import gnieh.diffson.Operation
import icd.web.shared._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import play.filters.csrf.CSRFAddToken
import spray.json._
import play.api.mvc._
import play.api.Environment

object Application {
  // Used to access the ICD database
  val db = IcdDb()
}

/**
 * Provides the interface between the web client and the server
 */
class Application @Inject() (implicit environment: Environment) extends Controller {
  import Application._

  def index = CSRFAddToken {
    Action { implicit request =>
      import play.filters.csrf.CSRF
      val token = CSRF.getToken(request).map(t => Csrf(t.value)).getOrElse(Csrf(""))
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
    import upickle.default._
    db.versionManager.getSubsystemModel(subsystem, versionOpt) match {
      case Some(model) =>
        val info = SubsystemInfo(model.subsystem, versionOpt, model.title, model.description)
        val json = write(info)
        Ok(json).as(JSON)
      case None =>
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
   *
   * @param subsystem  the subsystem
   * @param versionOpt the subsystem's version (default: current)
   * @param compNames  component names to get info about (separated by ",")
   */
  def componentInfo(subsystem: String, versionOpt: Option[String], compNames: String) = Action {
    import upickle.default._
    val compNameList = compNames.split(",").toList
    val infoList = ComponentInfoHelper.getComponentInfoList(db, subsystem, versionOpt, compNameList)
    val json = write(infoList)
    Ok(json).as(JSON)
  }

  /**
   * Gets information about a component in a given version of an ICD
   *
   * @param subsystem        the source subsystem
   * @param versionOpt       the source subsystem's version (default: current)
   * @param compNames        component names to get info about (separated by ",")
   * @param target           the target subsystem
   * @param targetVersionOpt the target subsystem's version
   */
  def icdComponentInfo(subsystem: String, versionOpt: Option[String], compNames: String,
                       target: String, targetVersionOpt: Option[String]) = Action {
    import upickle.default._
    val compNameList = compNames.split(",").toList
    val infoList = IcdComponentInfo.getComponentInfoList(db, subsystem, versionOpt, compNameList, target, targetVersionOpt)
    val json = write(infoList)
    Ok(json).as(JSON)
  }

  /**
   * Returns the PDF for the given ICD
   *
   * @param subsystem        the source subsystem
   * @param versionOpt       the source subsystem's version (default: current)
   * @param compNamesOpt     an optional comma separated list of component names to include (default: all)
   * @param target           the target subsystem
   * @param targetVersionOpt optional target subsystem's version (default: current)
   * @param icdVersionOpt    optional ICD version (default: current)
   */
  def icdAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String],
               target: String, targetVersionOpt: Option[String],
               icdVersionOpt: Option[String]) = Action {
    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) => s.split(",").toList
      case None    => db.versionManager.getComponentNames(subsystem, versionOpt)
    }

    // If the ICD version is specified, we can determine the subsystem and target versions, otherwise
    // if only the subsystem or target versions were given, use those (default to latest versions)
    val v = icdVersionOpt.getOrElse("*")
    val iv = db.versionManager.getIcdVersions(subsystem, target).find(_.icdVersion.icdVersion == v).map(_.icdVersion)
    val (sv, tv) = if (iv.isDefined) {
      val i = iv.get
      (SubsystemWithVersion(Some(i.subsystem), Some(i.subsystemVersion)), SubsystemWithVersion(Some(i.target), Some(i.targetVersion)))
    } else {
      (SubsystemWithVersion(Some(subsystem), versionOpt), SubsystemWithVersion(Some(target), targetVersionOpt))
    }

    IcdDbPrinter(db).getAsHtml(compNames, sv, tv, iv) match {
      case Some(html) =>
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None =>
        NotFound
    }
  }

  /**
   * Returns the PDF for the given subsystem API
   *
   * @param subsystem    the source subsystem
   * @param versionOpt   the source subsystem's version (default: current)
   * @param compNamesOpt an optional comma separated list of component names to include (default: all)
   */
  def apiAsPdf(subsystem: String, versionOpt: Option[String], compNamesOpt: Option[String]) = Action {
    val out = new ByteArrayOutputStream()
    val compNames = compNamesOpt match {
      case Some(s) => s.split(",").toList
      case None    => db.versionManager.getComponentNames(subsystem, versionOpt)
    }
    val sv = SubsystemWithVersion(Some(subsystem), versionOpt)
    val tv = SubsystemWithVersion(None, None)
    IcdDbPrinter(db).getAsHtml(compNames, sv, tv, None) match {
      case Some(html) =>
        IcdToPdf.saveAsPdf(out, html)
        val bytes = out.toByteArray
        Ok(bytes).as("application/pdf")
      case None =>
        NotFound
    }
  }

  /**
   * Returns a detailed list of the versions of the given subsystem
   */
  def getVersions(subsystem: String) = Action {
    import upickle.default._
    val versions = db.versionManager.getVersions(subsystem).map(v =>
      VersionInfo(v.versionOpt, v.user, v.comment, v.date.toString))
    Ok(write(versions)).as(JSON)
  }

  /**
   * Returns a list of version names for the given subsystem
   */
  def getVersionNames(subsystem: String) = Action {
    import upickle.default._
    val versions = db.versionManager.getVersionNames(subsystem)
    Ok(write(versions)).as(JSON)
  }

  /**
   * Publishes the given version of the given subsystem
   */
  def publishApi(subsystem: String, majorVersion: Boolean, comment: String, userName: String) = Action {
    // XXX TODO FIXME: publish should go away
    val now = new DateTime(DateTimeZone.UTC)
    db.versionManager.publishApi(subsystem, None, majorVersion, comment, userName, now)
    Ok.as(JSON)
  }

  /**
   * Publishes an ICD from the given version of the given subsystem to the target subsystem and version
   */
  def publishIcd(subsystem: String, version: String, target: String, targetVersion: String,
                 majorVersion: Boolean, comment: String, userName: String) = Action {
    // XXX error handling?
    db.versionManager.publishIcd(subsystem, version, target, targetVersion, majorVersion, comment, userName)
    Ok.as(JSON)
  }

  /**
   * Gets a list of ICD names as pairs of (subsystem, targetSubsystem)
   */
  def getIcdNames = Action {
    import upickle.default._
    // convert list to use shared IcdName class
    val list = db.versionManager.getIcdNames.map(icd => IcdName(icd.subsystem, icd.target))
    Ok(write(list)).as(JSON)
  }

  /**
   * Gets a list of versions for the ICD from subsystem to target subsystem
   */
  def getIcdVersions(subsystem: String, target: String) = Action {
    import upickle.default._
    // convert list to use shared IcdVersion class
    val list = db.versionManager.getIcdVersions(subsystem, target)
    Ok(write(list)).as(JSON)
  }

  // Packages the diff information for return to browser
  private def getDiffInfo(diff: VersionDiff): DiffInfo = {
    def getValue(a: Any): String = {
      a match {
        case o: JsObject =>
          val header = List("Attribute", "Value")
          val rows = o.fields.toList.map(p => List(getValue(p._1), getValue(p._2)))
          HtmlMarkup.mkTable(header, rows).render
        case ar: JsArray =>
          ar.elements.map(getValue).mkString(", ")
        case s: JsString =>
          s.value.stripMargin
        case n: JsNumber =>
          n.value.toString()
        case _ =>
          a.toString
      }
    }
    def getDiff(p: (String, Any)) = Diff(p._1, getValue(p._2))
    //    def getDiffItem(op: Operation) = DiffItem(op.path, op.toJson.values.map(getDiff).toList)
    def getDiffItem(op: Operation) = DiffItem(op.path, op.toJson.fields.map(getDiff).toList)
    DiffInfo(diff.path, diff.patch.ops.map(getDiffItem))
  }

  /**
   * Gets the difference between two subsystem versions
   */
  def diff(subsystem: String, versionsStr: String) = Action {
    import upickle.default._
    val versions = versionsStr.split(',')
    val v1 = versions.head
    val v2 = versions.tail.head
    val v1Opt = if (v1.nonEmpty) Some(v1) else None
    val v2Opt = if (v2.nonEmpty) Some(v2) else None
    // convert list to use shared IcdVersion class
    val list = db.versionManager.diff(subsystem, v1Opt, v2Opt).map(getDiffInfo)
    Ok(write(list)).as(JSON)
  }

}
