package goldrush

import cats.effect.Sync
import cats.{Functor, MonadError}
import io.circe
import io.circe.generic.auto._
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.{RetryPolicies, RetryPolicy, Sleep}
import sttp.client3.circe._
import sttp.client3.{
  Response,
  ResponseException,
  SttpBackend,
  UriContext,
  emptyRequest
}

trait Client[F[_]] {
  def getBalance(): F[Balance]
  def listLicenses(): F[Seq[License]]
  def issueLicense(coins: Int*): F[License]
  def explore(area: Area): F[ExploreResponse]
  def dig(licenseId: String, posX: Int, posY: Int, depth: Int): F[Seq[String]]
  def cash(treasure: String): F[Seq[Int]]
}

class ClientImpl[F[_]: Functor: Sleep: StructuredLogger](
    baseUrl: String,
    backend: SttpBackend[F, Any]
)(implicit E: MonadError[F, Throwable])
    extends Client[F] {
  private val infPolicy: RetryPolicy[F] = {
    import scala.concurrent.duration._
    RetryPolicies.limitRetriesByDelay(
      8.seconds,
      RetryPolicies.fullJitter(40.millis)
    )
  }

  def getBalance(): F[Balance] = {
    val request =
      emptyRequest
        .get(uri"$baseUrl/balance")
        .response(asJsonEither[ApiError, Balance])

    unwrapInf("getBalance") {
      backend.send(request)
    }
  }

  def listLicenses(): F[Seq[License]] = {
    val request =
      emptyRequest
        .get(uri"$baseUrl/licenses")
        .response(asJsonEither[ApiError, Seq[License]])
    unwrapInf("listLicences") {
      backend.send(request)
    }
  }

  def issueLicense(coins: Int*): F[License] = {
    val request =
      emptyRequest
        .post(uri"$baseUrl/licenses")
        .body(coins)
        .response(asJsonEither[ApiError, License])
    unwrapInf("issueLicense") {
      backend.send(request)
    }
  }

  def explore(area: Area): F[ExploreResponse] = {
    val request =
      emptyRequest
        .post(uri"$baseUrl/explore")
        .body(area)
        .response(asJsonEither[ApiError, ExploreResponse])
    unwrapInf("explore") {
      backend.send(request)
    }
  }

  def dig(
      licenseId: String,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = {
    val r = DigRequest(licenseId, posX, posY, depth)
    val request =
      emptyRequest
        .post(uri"$baseUrl/dig")
        .body(r)
        .response(asJsonEither[ApiError, Seq[String]])
    unwrapInf("dig") {
      backend.send(request)
    }
  }

  def cash(treasure: String): F[Seq[Int]] = {
    val request =
      emptyRequest
        .post(uri"$baseUrl/cash")
        .body(treasure)
        .response(asJsonEither[ApiError, Seq[Int]])
    unwrapInf("cash") {
      backend.send(request)
    }
  }

  private def unwrapInf[T](operation: String)(
      request: F[Response[Either[ResponseException[ApiError, circe.Error], T]]]
  ): F[T] = {
    import cats.syntax.functor._
    import cats.syntax.monadError._
    import retry.syntax.all._

    request
      .map(_.body)
      .rethrow
      .retryingOnAllErrors(
        infPolicy,
        (error, retry) => {
          val context = Map(
            "time" -> retry.retriesSoFar.toString,
            "operation" -> operation,
            "cumulativeDelay" -> retry.cumulativeDelay.toString
          )
          StructuredLogger[F].warn(context, error)("Retrying")
        }
      )
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

object ClientImpl {
  def apply[F[_]: Functor: Sleep: Sync](
      baseUrl: String,
      backend: SttpBackend[F, Any]
  )(implicit E: MonadError[F, Throwable]): F[Client[F]] = {
    import cats.syntax.functor._

    Slf4jLogger.create[F].map { implicit logger =>
      new ClientImpl[F](baseUrl, backend)
    }
  }
}

case class ExploreResponse(area: Area, amount: Int)

case class Balance(balance: Int, wallet: Seq[Int])

case class License(id: String, digAllowed: Int, digUsed: Int)

case class DigRequest(licenseId: String, posX: Int, posY: Int, depth: Int)

case class ApiError(code: Int, message: String)
