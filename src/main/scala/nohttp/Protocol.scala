package nohttp

import akka.actor.ActorRef
import akka.routing.ConsistentHashingRouter.ConsistentHashable

case class Session(id:String,score:Int)

case class StartClient()
case class StopClient()

case class SendMsg(id:String,msg: String) extends ConsistentHashable {
  override def consistentHashKey: Any = id
}
case class ReplyMsg(id:String,msg: String) extends ConsistentHashable {
  override def consistentHashKey: Any = id
}

case class SendMsgInner(session:Session,msg: String) extends ConsistentHashable {
  override def consistentHashKey: Any = session.id
}
case class ReplyMsgInner(session:Session,msg: String) extends ConsistentHashable {
  override def consistentHashKey: Any = session.id
}

case class ScoresRequest()
case class ScoresReply(scores:Map[String,Int])