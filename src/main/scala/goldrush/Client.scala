package goldrush

import cats.{Functor, MonadError}
import io.circe
import io.circe.generic.auto._
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.circe._
import sttp.client3.{Response, ResponseException, SttpBackend, UriContext, emptyRequest}

class Client[F[_] : Functor: Sleep](baseUrl: String, backend: SttpBackend[F, Any])(implicit E: MonadError[F, Throwable]) {
  private val infPolicy: RetryPolicy[F] = {
    import scala.concurrent.duration._
    RetryPolicies.limitRetriesByDelay(8.seconds, RetryPolicies.fullJitter(40.millis))
  }

  def getBalance(): F[Balance] = {
    val request =
      emptyRequest.get(uri"$baseUrl/balance")
        .response(asJsonEither[ApiError, Balance])

    unwrapInf {
      backend.send(request)
    }
  }

  def listLicenses(): F[Seq[License]] = {
    val request =
      emptyRequest.get(uri"$baseUrl/licenses")
        .response(asJsonEither[ApiError, Seq[License]])
    unwrapInf {
      backend.send(request)
    }
  }

  def issueLicense(coins: Int*): F[License] = {
    val request =
      emptyRequest.post(uri"$baseUrl/licenses")
        .response(asJsonEither[ApiError, License])
    unwrapInf {
      backend.send(request)
    }
  }

  def explore(area: Area): F[ExploreResponse] = {
    val request =
      emptyRequest.post(uri"$baseUrl/explore")
        .body(area)
        .response(asJsonEither[ApiError, ExploreResponse])
    unwrapInf {
      backend.send(request)
    }
  }

  def dig(licenseId: String, posX: Int, posY: Int, depth: Int): F[Seq[String]] = {
    val r = DigRequest(licenseId, posX, posY, depth)
    val request =
      emptyRequest.post(uri"$baseUrl/dig")
        .body(r)
        .response(asJsonEither[ApiError, Seq[String]])
    unwrapInf {
      backend.send(request)
    }
  }

  def cash(treasure: String): F[Seq[Int]] = {
    val request =
      emptyRequest.post(uri"$baseUrl/cash")
        .body(treasure)
        .response(asJsonEither[ApiError, Seq[Int]])
    unwrapInf {
      backend.send(request)
    }
  }

  private def unwrapInf[T](request: F[Response[Either[ResponseException[ApiError, circe.Error], T]]]): F[T] = {
    import cats.syntax.functor._
    import cats.syntax.monadError._
    import retry.syntax.all._

    request.map(_.body)
      .rethrow
      .retryingOnAllErrors(infPolicy, retry.noop)
  }
}

case class Area(posX: Int, posY: Int, sizeX: Int, sizeY: Int) {
  def locations: Seq[(Int, Int)] = {
    val yds = 1 to sizeY
    for {
      dx <- 1 to sizeX
      dy <- yds
      x = posX + dx - 1
      y = posY + dy - 1
    } yield (x, y)
  }
}

case class ExploreResponse(area: Area, amount: Int)

case class Balance(balance: Int, wallet: Seq[Int])

case class License(id: String, digAllowed: Int, digUsed: Int)

case class DigRequest(licenseId: String, posX: Int, posY: Int, depth: Int)

case class ApiError(code: Int, message: String)