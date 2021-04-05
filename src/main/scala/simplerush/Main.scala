package simplerush

import akka.actor.ActorSystem
import cats.instances.future._
import goldrush.{ClientImpl, Config}
import sttp.client3.akkahttp.AkkaHttpBackend
import test.TestClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Main extends App {

  val actorSystem: ActorSystem = ActorSystem("SingleRequest")
//  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  val blockingContext = actorSystem.dispatchers.lookup("client-blocking-io-dispatcher")
  val exploreContext = actorSystem.dispatchers.lookup("explore-dispatcher")
  val digsContext = actorSystem.dispatchers.lookup("digs-dispatcher")

  val backend = AkkaHttpBackend.usingActorSystem(actorSystem)(Some(blockingContext))
  implicit val futureMonadError = catsStdInstancesForFuture(blockingContext)
  val client = new ClientImpl[Future](Config.getBaseUrl, backend)
//  val client = new TestClient[Future]
  val minerFut = Future(
    new SimpleMiner(client, digsContext = digsContext, exploreContext = exploreContext).mine()
  )(exploreContext).flatten

  val result = Await.ready(
    Future.never,
    Duration.Inf
  )

}
