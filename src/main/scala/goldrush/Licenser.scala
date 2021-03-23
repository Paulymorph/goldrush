package goldrush

import cats.Monad
import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, Resource}
import monix.reactive.Observable
import cats.syntax.flatMap._
import cats.syntax.functor._
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
          .mapEvalF(queue.put)
          .completedF[F]
      }
    } yield queue.take
  }

  object Issuer {
    def free[F[_]](client: Client[F]): Issuer[F] = {
      client.issueLicense()
    }

    def paid[F[_]: Monad](howMany: Int, client: Client[F], store: GoldStore[F]): Issuer[F] = {
      for {
        coins <- store.tryTake(howMany)
        license <- client.issueLicense(coins: _*)
      } yield license
    }
  }
}
