package icd.web.client

import org.scalajs.dom
import org.scalajs.dom.{HttpMethod, RequestInit}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.Future
import scala.scalajs.js

// Wrapper for dom.fetch (replacement for deprecated Ajax class)
object Fetch {

  // Does an HTTP GET on the URL and returns the text
  def get(url: String): Future[String] = {
    dom.fetch(url).toFuture.flatMap(_.text().toFuture)
  }

  // Does an HTTP POST on the URL with the given JSON body and returns a future pair (status, text)
  def post(url: String, data: String): Future[(Int, String)] = {
    dom
      .fetch(
        url,
        new RequestInit {
          method = HttpMethod.POST
          body = data
          headers =  js.Dictionary(
              "Content-Type" -> "application/json"
          )
        }
      )
      .toFuture
      .flatMap(r => r.text().toFuture.map(text => (r.status, text)))
  }

  // Does an HTTP POST on the URL and returns the text
  def post(url: String): Future[String] = {
    post(url, "").map(_._2)
  }
}
