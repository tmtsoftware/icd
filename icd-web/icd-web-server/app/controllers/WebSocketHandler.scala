package controllers

import play.api.mvc._
import play.api.Play.current
import akka.actor._

/**
 * Websocket handler, used to push a message to the browser once the database has been updated,
 * so that the web app can update the list of ICDs.
 */
object WebSocketHandler {
  def props(out: ActorRef) = Props(new WebSocketHandler(out))
}

class WebSocketHandler(out: ActorRef) extends Actor {
  def receive = {
    case msg: String =>
      out ! ("I received your message: " + msg)
  }
}
