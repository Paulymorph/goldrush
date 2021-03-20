package goldrush.client_checks

import cats.effect.Timer
import cats.{Applicative, ApplicativeError, FlatMap}
import goldrush._

import scala.util.Random

class FakeClient[F[_]: Applicative: FlatMap: Timer](implicit
    ae: ApplicativeError[F, Throwable]
) extends Client[F] {
  override def getBalance(): F[Balance] = ???

  override def listLicenses(): F[Seq[License]] = ???

  override def issueLicense(coins: Int*): F[License] = {
    fakeWaitAndResponse(
      License(Random.nextInt(), Math.max(3, coins.size * 5), 0),
      30,
      Math.max(1, 20)
    )
  }

  override def explore(area: Area): F[ExploreResponse] = {
    val areaSize = area.sizeX * area.sizeY

    fakeWaitAndResponse(
      ExploreResponse(area, Random.nextInt(115)),
      areaSize / 5 + 1,
      areaSize / 10 + 1
    )
  }

  private def fakeWaitAndResponse[T](
      res: => T,
      errorLatency: Int,
      successLatency: Int
  ): F[T] = {
    if (Random.nextInt(5) == 0)
      CatsUtil.sleepAndReturn(errorLatency, Left(new RuntimeException))
    else CatsUtil.sleepAndReturn(successLatency, Right(res))
  }

  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = ???

  override def cash(treasure: String): F[Seq[Int]] = ???
}
