package goldrush

import cats.{Applicative, FlatMap, Monad}
import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, Resource}
import monix.reactive.{Observable, OverflowStrategy}
import cats.syntax.flatMap._
import cats.syntax.functor._
import goldrush.Counters.{freeLicenceDigsCount, freeLicenceTriesCount, paidLicenceDigsCount, paidLicenceTriesCount}
import monix.eval.{TaskLift, TaskLike}

object Licenser {
  type LicenseId = Int
  type Licenser[F[_]] = F[LicenseId]
  type Issuer[F[_]] = F[License]

  def apply[F[_]: Concurrent: TaskLike: TaskLift](
      parallelism: Int,
      issuer: Issuer[F]
  ): Resource[F, Licenser[F]] = {
    for {
      queue <- Resource.liftF(MVar.empty[F, Int])
      _ <- Concurrent[F].background {
        Observable
          .repeat(())
          .mapParallelUnorderedF(parallelism)(_ => issuer)
          .flatMapIterable { license =>
            Seq.fill(license.digAllowed - license.digUsed)(license.id)
          }
          .asyncBoundary(OverflowStrategy.Unbounded)
          .mapEvalF(queue.put)
          .asyncBoundary(OverflowStrategy.Unbounded)
          .completedF[F]
      }
    } yield queue.take
  }

  object Issuer {
    def free[F[_]: Monad](client: Client[F]): Issuer[F] = {
      for {
        _ <- Applicative[F].pure(freeLicenceTriesCount.incrementAndGet())
        licence <- client.issueLicense()
        _ <- Applicative[F].pure(freeLicenceDigsCount.addAndGet(licence.digAllowed))
      } yield licence
    }

    def paid[F[_]: Monad](howMany: Int, client: Client[F], store: GoldStore[F]): Issuer[F] = {
      for {
        coins <- store.tryTake(howMany)
        _ = paidLicenceTriesCount.incrementAndGet()
        license <- client.issueLicense(coins: _*)
        _ = paidLicenceDigsCount.addAndGet(license.digAllowed)
      } yield license
    }
  }
}
