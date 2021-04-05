package test

import cats.{Applicative, ApplicativeError}
import goldrush._

import scala.util.Random

class TestClient[F[_]](implicit ae: ApplicativeError[F, Throwable]) extends Client[F] {
  override def getBalance(): F[Balance] = ???
  override def listLicenses(): F[Seq[License]] = ???
  override def issueLicense(coins: Int*): F[License] =
    printAndRes("issueLicense", License(1, 3, 0), 100, errorRate = 60)
  override def explore(area: Area): F[ExploreResponse] =
    printAndRes("explore", ExploreResponse(area, if (Random.nextInt(25) == 0) 1 else 0), 20)
  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = printAndRes(s"dig$depth", if (depth < 4) Seq.empty[String] else Seq("1"), 10)
  override def cash(treasure: String): F[Seq[Int]] = printAndRes("cash", Seq.empty, 100)

  private def printAndRes[T](
      operation: String,
      res: T,
      maxSleepTime: Int = 10,
      errorRate: Int = 0
  ): F[T] = {

    Thread.sleep(Random.nextInt(maxSleepTime * 1))
    if (Random.nextInt(100) < errorRate) {
      println(operation, "error")
      ApplicativeError[F, Throwable].raiseError(new RuntimeException("error"))
    } else {
      println(operation, "res", res)
      Applicative[F].pure(res)
    }
  }
}
