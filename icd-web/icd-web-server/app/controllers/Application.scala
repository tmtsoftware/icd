package controllers

import csw.services.icd.db.{IcdDbPrinter, IcdDb}
import play.api._
import play.api.mvc._
import play.filters.csrf.CSRFAddToken
import shared.Csrf
import play.api.libs.json._
import play.api.libs.functional.syntax._

object Application extends Controller {

  def index = CSRFAddToken {
    Action { implicit request =>
      import play.filters.csrf.CSRF
      val token = CSRF.getToken(request).map(t => Csrf(t.value)).getOrElse(Csrf(""))
      Ok(views.html.index(token))
    }
  }

  /**
   * Gets a list of top level ICD names
   */
  def icdNames = Action {
    val db = IcdDb("test") // XXX TODO configure
    val names = db.query.getIcdNames
    db.close()
    Ok(Json.toJson(names))
  }

  /**
   * Returns the HTML for the ICD with the given name
   * @param name the ICD name
   */
  def icdHtml(name: String) = Action {
    val db = IcdDb("test") // XXX TODO configure
    val html = IcdDbPrinter(db.query).getAsPlainHtml(name)
    db.close()
    Ok(html).as(HTML)
  }
}
