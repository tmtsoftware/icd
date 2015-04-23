package controllers

import csw.services.icd.db.IcdDb
import play.api._
import play.api.mvc._
import shared.SharedMessages
import play.api.libs.json._
import play.api.libs.functional.syntax._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index(SharedMessages.itWorks))
  }

  def icdNames = Action {
    val db = IcdDb("test") // XXX TODO configure
    val names = db.query.getIcdNames
    Ok(Json.toJson(names))
  }
}
