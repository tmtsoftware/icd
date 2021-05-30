package csw.services.icd.db.parser

import icd.web.shared.IcdModels.{ServiceModel, ServiceModelClient, ServiceModelProvider, ServicePath}
import reactivemongo.api.bson._

/*
 * See resources/<version>/service-schema.conf
 */

object ServiceModelBsonParser {

  object ServicePathBsonParser {
    def apply(doc: BSONDocument): Option[ServicePath] = {
      if (doc.isEmpty) None
      else
        Some {
          ServicePath(
            method = doc.getAsOpt[String]("method").get,
            path = doc.getAsOpt[String]("path").get
          )
        }
    }
  }

  object ServiceModelClientBsonParser {
    def apply(doc: BSONDocument): Option[ServiceModelClient] = {
      if (doc.isEmpty) None
      else
        Some {
          def getItems[A](name: String, f: BSONDocument => A): List[A] =
            for (subDoc <- doc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)

          ServiceModelClient(
            subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
            name = doc.getAsOpt[String]("name").get,
            paths = getItems("paths", ServicePathBsonParser(_)).flatten
          )
        }
    }
  }

  object ServiceModelProviderBsonParser {
    def apply(doc: BSONDocument): Option[ServiceModelProvider] = {
      if (doc.isEmpty) None
      else
        Some {
          ServiceModelProvider(
            name = doc.getAsOpt[String]("name").get,
            openApi = doc.getAsOpt[String]("openApi").get
          )
        }
    }
  }

  def apply(doc: BSONDocument): Option[ServiceModel] = {
    if (doc.isEmpty) None
    else
      Some {
        def getItems[A](name: String, f: BSONDocument => A): List[A] =
          for (subDoc <- doc.getAsOpt[Array[BSONDocument]](name).map(_.toList).getOrElse(Nil)) yield f(subDoc)

        ServiceModel(
          subsystem = doc.getAsOpt[String](BaseModelBsonParser.subsystemKey).get,
          component = doc.getAsOpt[String](BaseModelBsonParser.componentKey).get,
          provides = getItems("provides", ServiceModelProviderBsonParser(_)).flatten,
          requires = getItems("requires", ServiceModelClientBsonParser(_)).flatten
        )
      }
  }
}
