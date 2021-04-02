package goldrush

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicLong

import cats.data.Chain
import cats.{Applicative, Monad}
import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{Concurrent, Resource, Sync}
import monix.reactive.{Observable, OverflowStrategy}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._
import monix.eval.{TaskLift, TaskLike}

import scala.concurrent.duration.Duration
import scala.util.Random

object Licenser {
  type LicenseId = Int
  type Licenser[F[_]] = F[LicenseId]
  type Issuer[F[_]] = F[License]

  def apply[F[_]: Concurrent: TaskLike: TaskLift](
      parallelism: Int,
      issuer: Issuer[F]
  ): Resource[F, Licenser[F]] = {
    implicit val backPressure: OverflowStrategy[License] = OverflowStrategy.BackPressure(6)
    for {
      queue <- Resource.liftF(MVar.empty[F, Int])
      _ <- Concurrent[F].background {
        Observable
          .repeat(())
//          .timerRepeated(
//            Duration(10, TimeUnit.MILLISECONDS),
//            Duration(1, TimeUnit.MILLISECONDS),
//            ()
//          )
          .mapParallelUnorderedF(parallelism)(_ => issuer)
          .flatMapIterable { license =>
            Seq.fill(license.digAllowed - license.digUsed)(license.id)
          }
          .mapEvalF(queue.put)
          .completedF[F]
      }
    } yield queue.take
  }

  def apply2[F[_]: Concurrent: TaskLike: TaskLift](
      parallelism: Int,
      issuer: Issuer[F]
  ): Resource[F, Licenser[F]] = {
    implicit val backPressure: OverflowStrategy[License] = OverflowStrategy.BackPressure(10)
    val licensesQ = new LinkedBlockingQueue[Int](100)
    for {
      _ <- Concurrent[F].background {
        Observable
          .timerRepeated(
            Duration(10, TimeUnit.MILLISECONDS),
            Duration(1, TimeUnit.MILLISECONDS),
            ()
          )
          .mapParallelUnorderedF[F, Option[License]](parallelism) { _ =>
            if (licensesQ.size() < 25) issuer.map(Option(_))
            else Applicative[F].pure(None)
          }
          .flatMap(Observable.fromIterable(_))
          .map { x =>
            Seq.range(0, x.digAllowed - x.digUsed).foreach(_ => licensesQ.add(x.id))
            x
          }
          .completedF[F]
      }
    } yield Applicative[F].pure(licensesQ.take())
  }

  def noBackground[F[_]: Sync](issuer: Issuer[F]) = {
    def retrieveOrIssue(store: Ref[F, Chain[LicenseId]]): Licenser[F] = {
      store.modify { licenses =>
        licenses.uncons match {
          case Some((license, left)) => left -> Applicative[F].pure(license)
          case None =>
            Chain.empty -> issuer.flatMap { license =>
              val newLicenses =
                Chain.fromSeq(Seq.fill(license.digAllowed - license.digUsed)(license.id))
              store.update(_ ++ newLicenses) *> retrieveOrIssue(store)
            }
        }
      }.flatten
    }
    Ref.of[F, Chain[LicenseId]](Chain.empty[LicenseId]).map { licenseStore =>
      retrieveOrIssue(licenseStore)
    }
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

    def paidRandom[F[_]: Monad: Sync](
        max: Int,
        client: Client[F],
        store: GoldStore[F]
    ): Issuer[F] = {
      for {
        r <- Sync[F].delay(Random.nextInt(max + 1))
        coins <- store.tryTake(r)
        license <- client.issueLicense(coins: _*)
      } yield license
    }
  }
}
