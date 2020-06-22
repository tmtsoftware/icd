package controllers

import java.io.File

import csw.services.icd.PdfCache
import csw.services.icd.db.{IcdDb, IcdDbDefaults}
import javax.inject.Inject
import play.api.Configuration
import play.api.mvc.{ActionBuilderImpl, BodyParsers, Request, Result, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

// Defines the database used
object ApplicationData {
  // Used to access the ICD database
  val tryDb: Try[IcdDb] = Try(IcdDb())

  // Cache of PDF files for published API and ICD versions
  val maybeCache: Option[PdfCache] =
    if (IcdDbDefaults.conf.getBoolean("icd.pdf.cache.enabled"))
      Some(new PdfCache(new File(IcdDbDefaults.conf.getString("icd.pdf.cache.dir"))))
    else None

  // Name of cookie used for login username:password
  val cookieName = "icd.credentials.sha"

  // Action that requires authorization
  class AuthAction @Inject()(parser: BodyParsers.Default, configuration: Configuration)(implicit ec: ExecutionContext)
    extends ActionBuilderImpl(parser) {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
      if (configuration.get[Boolean]("icd.isPublicServer")) {
        request.cookies.get(cookieName) match {
          case Some(cookie) if configuration.get[String](cookieName) == cookie.value => block(request)
          case _                                                                     => Future(Results.Unauthorized("Invalid user token"))
        }
      } else {
        block(request)
      }
    }
  }
}
