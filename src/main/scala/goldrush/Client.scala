package goldrush

import cats.{Functor, MonadError}
import io.circe
import io.circe.generic.auto._
import sttp.client3.circe._
import sttp.client3.{HttpError, Response, ResponseException, SttpBackend, UriContext, emptyRequest}
import sttp.model.{MediaType, StatusCode}

trait Client[F[_]] {
  def getBalance(): F[Balance]
  def listLicenses(): F[Seq[License]]
  def issueLicense(coins: Int*): F[License]
  def explore(area: Area): F[ExploreResponse]
  def dig(licenseId: Int, posX: Int, posY: Int, depth: Int): F[Seq[String]]
  def cash(treasure: String): F[Seq[Int]]
}

class ClientImpl[F[_]: Functor](
    baseUrl: String,
    backend: SttpBackend[F, Any]
)(implicit E: MonadError[F, Throwable])
    extends Client[F] {

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
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = {
    val r = DigRequest(licenseId, posX, posY, depth)
    import cats.syntax.either._
    val notFoundExpected = asJsonEither[ApiError, Seq[String]].map {
      _.recover { case HttpError(_, StatusCode.NotFound) =>
        Seq.empty
      }
    }
    val request =
      emptyRequest
        .post(uri"$baseUrl/dig")
        .body(r)
        .response(notFoundExpected)
    unwrapInf("dig") {
      backend.send(request)
    }
  }

  def cash(treasure: String): F[Seq[Int]] = {
    val request =
      emptyRequest
        .post(uri"$baseUrl/cash")
        .body(s""""$treasure"""")
        .contentType(MediaType.ApplicationJson)
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

    MonadError[F, Throwable].handleErrorWith(
      request
        .map(_.body)
        .rethrow
    )(_ => unwrapInf(operation)(request))
  }
}

case class Area(posX: Int, posY: Int, sizeX: Int, sizeY: Int)

object Area {

  def locations(area: Area): Seq[(Int, Int)] = {
    import area._
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

case class License(id: Int, digAllowed: Int, digUsed: Int)

case class DigRequest(licenseId: Int, posX: Int, posY: Int, depth: Int)

case class ApiError(code: Int, message: String)
