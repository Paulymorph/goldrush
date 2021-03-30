package goldrush

import cats.Apply
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.effect.{Concurrent, Resource, Timer}
import cats.effect.concurrent.Semaphore

class LimitedClient[F[_]: Apply] private (underlying: Client[F], limiter: Semaphore[F])
    extends Client[F] {
  override def getBalance(): F[Balance] = limiter.acquire *> underlying.getBalance()

  override def listLicenses(): F[Seq[License]] = limiter.acquire *> underlying.listLicenses()

  override def issueLicense(coins: Int*): F[License] =
    limiter.acquire *> underlying.issueLicense(coins: _*)

  override def explore(area: Area): F[ExploreResponse] = limiter.acquire *> underlying.explore(area)

  override def dig(licenseId: Int, posX: Int, posY: Int, depth: Int): F[Seq[String]] =
    limiter.acquire *> underlying.dig(licenseId, posX, posY, depth)

  override def cash(treasure: String): F[Seq[Int]] =
    limiter.acquire *> underlying.cash(treasure)
}

import scala.concurrent.duration._
object LimitedClient {
  def wrap[F[_]: Concurrent: Timer](limit: Int)(underlying: Client[F]) = {
    for {
      limiter <- Resource.liftF(Semaphore(limit))
      _ <- Concurrent[F].background[Unit] {
        (Timer[F].sleep(1.second) *> limiter.releaseN(limit)).foreverM
      }
    } yield new LimitedClient[F](underlying, limiter)
  }
}
