package goldrush

import cats.FlatMap
import cats.effect.Clock
import cats.syntax.flatMap._
import cats.syntax.functor._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class StatisticsClient[F[_]: FlatMap: Clock] private (
    underlying: Client[F],
    statistics: Statistics[F]
) extends Client[F] {
  override def getBalance(): F[Balance] = underlying.getBalance()

  override def listLicenses(): F[Seq[License]] = underlying.listLicenses()

  override def issueLicense(coins: Int*): F[License] =
    measure(underlying.issueLicense(coins: _*)) { case (l, duration) =>
      statistics.issuedLicense(coins, l, duration)
    }

  override def explore(area: Area): F[ExploreResponse] = underlying.explore(area)

  override def dig(licenseId: Int, posX: Int, posY: Int, depth: Int): F[Seq[String]] =
    measure(underlying.dig(licenseId, posX, posY, depth)) { case (_, duration) =>
      statistics.digged(duration)
    }

  override def cash(treasure: String): F[Seq[Int]] =
    measure(underlying.cash(treasure)) { case (coins, duration) =>
      statistics.cashed(coins.length, duration)
    }

  private val time = Clock[F].monotonic(TimeUnit.NANOSECONDS)
  private def measure[T](computation: F[T])(record: (T, FiniteDuration) => F[Unit]): F[T] = {
    import scala.concurrent.duration._
    for {
      start <- time
      result <- computation
      finish <- time
      duration = (finish - start).nanos
      _ <- record(result, duration)
    } yield result
  }
}

object StatisticsClient {
  def wrap[F[_]: FlatMap: Clock](statistics: Statistics[F])(client: Client[F]) =
    new StatisticsClient[F](client, statistics)
}
