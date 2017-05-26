package http

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import nohttp._
import spray.json._

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.Success

trait JsonSupport   extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val replyFormat: RootJsonFormat[ReplyMsg] = jsonFormat2(ReplyMsg)
  implicit val scoresFormat: RootJsonFormat[ScoresReply] = jsonFormat1(ScoresReply)
}

object WebServer extends App with JsonSupport {

  implicit val system = ActorSystem("akka_bots_http")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout: Timeout = 30.seconds

  val server = system.actorOf(Props[Server], name = "server")

  val routes = path("send") {
    get {
      parameter("uid", "msg") { (id, msg) =>
        val msgFuture = (server ? SendMsg(id, msg)).mapTo[ReplyMsg]
        complete(msgFuture)
      }
    }
  } ~ path("score") {
    get {
      val scoreFuture = (server ? ScoresRequest).mapTo[ScoresReply]
      complete(scoreFuture)
    }
  }

  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)
  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}
