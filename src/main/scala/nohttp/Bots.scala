package nohttp

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.ConsistentHashingPool
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Random, Success}


class Client(aServer: ActorRef,sid: String) extends Actor{
  private val server:ActorRef = aServer
  private val sessionId = sid
  private var runnerThread:Thread = _

  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case ReplyMsg(s,r) => println("Session: "+s+" reply:"+r)
    case ScoresReply(scores) => println("Session "+sessionId+" scores:"+scores)

    case StartClient =>
      runnerThread=new Thread {
        println("Client "+sessionId+" started")
        for ( i <- 1 to 10) {
          sendMsg("Msg: " + i)
          //Thread.sleep(1000)
          Thread.sleep((Random.nextFloat() * 200).toLong)

          if(i == 5 || i == 10){
            requestScores()
          }
        }
      }
      runnerThread.setDaemon(true)
      runnerThread.start()

    case StopClient =>
      requestScores()
      runnerThread.interrupt()
      runnerThread = null
  }

  def sendMsg(msg:String): Unit ={
    println(sessionId+" sending: "+msg)
    server ! SendMsg(sessionId,msg)
  }

  def requestScores(): Unit = server ! ScoresRequest
}

class BotActor extends Actor {
  private val wordWeight = 1
  private val answers = List("Hello", "Hi", "Welcome", "Good morning")
  implicit val ec = context.dispatcher
  private var clientFutures:Map[String,Future[Any]] = Map.empty

  def reply(session: Session, msg: String): Future[(Session, String)] = Future {
    val id = session.id
    val msgScore = msg.split(" ").length * wordWeight
    val newSession = Session(id, msgScore)
    Thread.sleep(Random.nextInt(500))
    (newSession, answers(Random.nextInt(answers.size))+" in reply to "+msg)
  }

  def receive: Receive = {
    case SendMsgInner(s, m) =>
      val server = sender()
      val oldF = clientFutures.getOrElse(s.id, Future {
        true
      })

      val newF = oldF.andThen { case _ =>
        reply(s, m) onComplete {
          case scala.util.Success((ss, reply)) =>
            server ! ReplyMsgInner(ss, reply)
          case scala.util.Failure(t) =>
            println("Error: " + t.getMessage)
        }
      }
      clientFutures += (s.id -> newF)
  }
}

class Server extends Actor {
  private val botRouter = context.actorOf(ConsistentHashingPool(10).props(Props[BotActor]), name = "bots")
  private var sessions: Map[String, Session] = Map.empty[String, Session]

  implicit val ec = context.dispatcher

  def receive: Receive = {
    case SendMsg(id, msg) =>
      implicit val timeout:Timeout = 30.seconds

      val oldSession:Session = sessions.getOrElse(id, Session(id, 0))

      val reply = (botRouter ? SendMsgInner(oldSession, msg)).mapTo[ReplyMsgInner]
      val sen = sender()
      reply onComplete {
        case Success(repl @ ReplyMsgInner(_,_) )=>
          Server.this.synchronized {
            val os = sessions.getOrElse(id, Session(id, 0))
            sessions += (repl.session.id -> Session(repl.session.id, os.score + repl.session.score))
          }
          sen ! ReplyMsg(repl.session.id, repl.msg)
        case _ => println("Error "+_)
      }

    case ScoresRequest =>
      sender ! ScoresReply(sessions.values.map({ s => (s.id, s.score) }).toMap)
  }
}

object NoHttpBots extends App {
  val sys = ActorSystem("akka_bots_nohttp")
  val server = sys.actorOf(Props[Server], name = "server")
  val clients = (1 to 3) map { id => sys.actorOf(Props(new Client(server, "session-" + id)), "client-" + id) }
  implicit val ec = sys.dispatcher

  for( c <- clients) {
    c ! StartClient
  }

  Thread.sleep(3000)
  println("Press enter to EXIT")
  StdIn.readLine()
  for( c <- clients) {
    c ! StopClient
  }
  Thread.sleep(1000)

  sys.terminate foreach { _=>println("Terminated") }
}