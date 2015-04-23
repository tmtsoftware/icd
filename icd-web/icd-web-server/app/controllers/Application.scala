package controllers

import csw.services.icd.db.{IcdDbPrinter, IcdDb}
import play.api._
import play.api.mvc._
import shared.SharedMessages
import play.api.libs.json._
import play.api.libs.functional.syntax._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index(SharedMessages.itWorks))
  }

  /**
   * Gets a list of top level ICD names
   */
  def icdNames = Action {
    val db = IcdDb("test") // XXX TODO configure
    val names = db.query.getIcdNames
    Ok(Json.toJson(names))
  }

  /**
   * Returns the HTML for the ICD with the given name
   * @param name the ICD name
   */
  def icdHtml(name: String) = Action {
    val db = IcdDb("test") // XXX TODO configure
    val html = IcdDbPrinter(db.query).getAsPlainHtml(name)
    Ok(html).as(HTML)
  }
}
