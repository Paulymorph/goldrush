package goldrush

import java.util.concurrent.ScheduledExecutorService

import cats.{Applicative, ApplicativeError, FlatMap}
import cats.effect.{Clock, IO, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.Random

class FakeClient[F[_]: Applicative: FlatMap: Timer](implicit
    ae: ApplicativeError[F, Throwable]
) extends Client[F] {
  override def getBalance(): F[Balance] = ???

  override def listLicenses(): F[Seq[License]] = ???

  override def issueLicense(coins: Int*): F[License] = ???

  override def explore(area: Area): F[ExploreResponse] = {
    val areaSize = area.sizeX * area.sizeY

    if (Random.nextInt(5) == 0) {
      Timer[F]
        .sleep(FiniteDuration(Random.nextInt(areaSize / 5 + 1), MILLISECONDS))
        .flatMap(_ =>
          ApplicativeError[F, Throwable].fromEither(Left(new RuntimeException))
        )
    } else {
      Timer[F]
        .sleep(FiniteDuration(Random.nextInt(areaSize / 10 + 1), MILLISECONDS))
        .flatMap(_ =>
          Applicative[F].pure {
            ExploreResponse(area, Random.nextInt(115))
          }
        )
    }

  }

//  def sleep(timespan: FiniteDuration): IO[Unit] =
//    IO.cancelable { cb =>
//      val tick = new Runnable {
//        def run() = ec.execute(new Runnable {
//          def run() = cb(Right(()))
//        })
//      }
//      val f = sc.schedule(tick, timespan.length, timespan.unit)
//      IO(f.cancel(false)).void
//    }

  override def dig(
      licenseId: Int,
      posX: Int,
      posY: Int,
      depth: Int
  ): F[Seq[String]] = ???

  override def cash(treasure: String): F[Seq[Int]] = ???
}
