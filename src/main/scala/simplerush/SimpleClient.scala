//package simplerush
//
//import akka.actor.typed.ActorSystem
//import akka.actor.typed.scaladsl.Behaviors
//import akka.http.scaladsl.Http
//import akka.http.scaladsl.model._
//import SimpleClient._
//import play.api.libs.json._
//import play.api.libs.json._
//
//import scala.concurrent.{ExecutionContext, Future}
//import scala.util.{Failure, Success}
//
//class SimpleClient(baseUrl: String)(implicit ec: ExecutionContext, actorSystem: ActorSystem[_]) {
//
//  private val licencesUrl = baseUrl + "/licenses"
//
//  private def retriable[T](fut: => Future[T]): Future[T] = {
//    fut.fallbackTo(retriable(fut))
//  }
//
//  def getLicence(coins: Seq[Int]): Future[LicencesResponse] = {
//    retriable(
//      Http().singleRequest(
//        HttpRequest(
//          uri = licencesUrl,
//          method = HttpMethods.POST,
//          entity = HttpEntity(ContentTypes.`application/json`, Json.toBytes(Json.toJson(coins)))
//        )
//      )
//    ).map(response => Json.parse(response.entity.getDataBytes()))
//  }
//  def explore(area: Area): Future[ExploreResponse] = {
//
//  }
//  def dig(request: DigRequest): Future[Seq[String]]
//  def cash(treasureId: String): Future[Seq[Int]]
//
//}
//
//object SimpleClient {
//
//  implicit private val LicencesResponseReader = Json.reads[LicencesResponse]
//
//  case class LicencesResponse(id: Int, digAllowed: Int, digUsed: Int)
//
//  case class ExploreResponse(amount: Int)
//
//  case class DigRequest(licenseID: Int, posX: Int, posY: Int, depth: Int)
//
//  case class Area(posX: Int, posY: Int, sizeX: Int, sizeY: Int)
//
//}
