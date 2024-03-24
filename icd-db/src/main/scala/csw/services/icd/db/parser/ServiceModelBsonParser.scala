package csw.services.icd.db.parser

import csw.services.icd.html.HtmlMarkup
import icd.web.shared.IcdModels.{ServiceModel, ServiceModelClient, ServiceModelProvider, ServicePath}
import icd.web.shared.PdfOptions
import play.api.libs.json.{JsObject, JsValue, Json}
import reactivemongo.api.DB
import reactivemongo.api.bson.collection.BSONCollection
import csw.services.icd.*
import reactivemongo.play.json.compat.*
import bson2json.*
import lax.*
import json2bson.*
import reactivemongo.api.bson.BSONDocument

import scala.concurrent.ExecutionContext.Implicits.global

/*
 * See resources/<version>/service-schema.conf
 */

object ServiceModelBsonParser {

  private object ServicePathBsonParser {
    def apply(doc: BSONDocument): Option[ServicePath] = {
      if (doc.isEmpty) None
      else
        Some(
          ServicePath(
            method = doc.string("method").get,
            path = doc.string("path").get,
            description = "" // Have to get this from the OpenApi file
          )
        )
    }
  }

  private object ServiceModelClientBsonParser {
    def apply(doc: BSONDocument): Option[ServiceModelClient] = {
      if (doc.isEmpty) None
      else
        Some {
          def getItems[A](name: String, f: BSONDocument => A): List[A] = {
            for (subDoc <- doc.children(name)) yield f(subDoc)
          }

          ServiceModelClient(
            subsystem = doc.string(BaseModelBsonParser.subsystemKey).get,
            component = doc.string(BaseModelBsonParser.componentKey).get,
            name = doc.string("name").get,
            paths = getItems("paths", ServicePathBsonParser(_)).flatten
          )
        }
    }
  }

  private object ServiceModelProviderBsonParser {

    // Get a list of routes/paths from the OpenApi description
    private def getOpenApiPaths(json: JsValue): List[ServicePath] = {
      val paths = (json \ "paths").as[JsObject].keys.toList
      paths.flatMap { path =>
        val methods = (json \ "paths" \ path).as[JsObject].keys.toList
        methods.map { method =>
          val summary = (json \ "paths" \ path \ method \ "summary").validate[String].getOrElse("")
          val description =
            if (summary.nonEmpty) summary
            else (json \ "paths" \ path \ method \ "description").validate[String].getOrElse("")
          ServicePath(method, path, summary)
        }
      }
    }

    def apply(db: DB, doc: BSONDocument, subsystem: String, component: String): Option[ServiceModelProvider] = {
      if (doc.isEmpty) None
      else {
        // When reading from the database replace the openApi file name with the contents that were ingested for that file
        val name            = doc.string("name").get
        val collName        = s"$subsystem.$component.service.$name"
        val coll            = db.collection[BSONCollection](collName)
        val maybeOpenApiDoc = coll.find(BSONDocument(), Option.empty[JsObject]).one[BSONDocument].await
        if (maybeOpenApiDoc.isEmpty) {
          println(s"ServiceModelProviderBsonParser: Can't locate MongoDB collection: $collName")
        }
        maybeOpenApiDoc.map { openApiDoc =>
          val openApiDocJson = Json.toJson(openApiDoc)
          ServiceModelProvider(
            name = doc.string("name").get,
            description = doc.string("description").get,
            openApi = openApiDocJson.toString(),
            paths = getOpenApiPaths(openApiDocJson)
          )
        }
      }
    }
  }

  def apply(db: DB, doc: BSONDocument, maybePdfOptions: Option[PdfOptions]): Option[ServiceModel] = {
    if (doc.isEmpty) None
    else {
      val subsystem = doc.string(BaseModelBsonParser.subsystemKey).get
      val component = doc.string(BaseModelBsonParser.componentKey).get

      def getItems[A](name: String, f: BSONDocument => A): List[A] =
        for (subDoc <- doc.children(name)) yield f(subDoc)

      Some(
        ServiceModel(
          subsystem = subsystem,
          component = component,
          description = doc.string("description").map(s => HtmlMarkup.gfmToHtml(s, maybePdfOptions)).getOrElse(""),
          provides = getItems("provides", ServiceModelProviderBsonParser(db, _, subsystem, component)).flatten,
          requires = getItems("requires", ServiceModelClientBsonParser(_)).flatten
        )
      )
    }
  }
}
