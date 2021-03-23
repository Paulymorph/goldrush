package goldrush.client_checks

import cats.effect.Timer
import cats.{Applicative, ApplicativeError, FlatMap}
import goldrush.Types.ThrowableApplicativeError
import goldrush._
import FakeClient._

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
    if (area.posX + area.sizeX > fieldSize || area.posY + area.sizeY > fieldSize)
      throw new IllegalArgumentException(s"Exploring outside of area: $area")

    val areaSize = area.sizeX * area.sizeY

    fakeWaitAndResponse(
      ExploreResponse(area, Random.nextInt(areaSize)),
      areaSize / 500 + 1,
      areaSize / 1000 + 1
    )
  }



  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = ???

  override def cash(treasure: String): F[Seq[Int]] = ???
}

object FakeClient {

  private def fakeWaitAndResponse[T, F[_]: Timer: ThrowableApplicativeError: FlatMap](
                                      res: => T,
                                      errorLatency: Int,
                                      successLatency: Int
                                    ): F[T] = {
    if (Random.nextInt(5) == 0)
      CatsUtil.sleepAndReturn(errorLatency, Left(new RuntimeException))
    else CatsUtil.sleepAndReturn(successLatency, Right(res))
  }

  private val fieldSize = 3500

}
