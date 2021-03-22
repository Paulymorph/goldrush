package goldrush

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Resource}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.{Applicative, Monad, NonEmptyParallel}
import monix.catnap.ConcurrentQueue
import monix.eval.TaskLift
import monix.reactive.Observable

object Licenser {
  type LicenseId = Int
  type Licenser[F[_]] = Resource[F, LicenseId]

  def apply[F[_]: ConcurrentEffect: ContextShift: NonEmptyParallel: TaskLift](
      limit: Int,
      issueLicense: F[License]
  ): Resource[F, Licenser[F]] = {
    val licensesStreamF = ConcurrentQueue.unbounded[F, LicenseId]()
    val licensesStoreF = Ref.of(Map.empty[LicenseId, Int])
    val limiterF = Semaphore[F](limit)

    Resource
      .liftF((licensesStreamF, licensesStoreF, limiterF).parTupled)
      .flatMap { case (licensesStream, licensesStore, limiter) =>
        val limitedIssue = limiter.acquire *> issueLicense

        val generateLicensesBackground = Concurrent[F].background {
          Observable
            .repeat(())
            .mapParallelUnorderedF(limit)(_ => limitedIssue)
            .mapEvalF { license =>
              val useTimes = license.digAllowed - license.digUsed
              val licenses = Seq.fill(useTimes)(license.id)
              val stream = licensesStream.offerMany(licenses)

              val updateStore = licensesStore.update { licenses =>
                val newValue = licenses.getOrElse(license.id, 0) + useTimes
                licenses.updated(license.id, newValue)
              }

              (stream, updateStore).parTupled
            }
            .completedF[F]
        }
        generateLicensesBackground.as(
          licenser(licensesStream, licensesStore, limiter.release)
        )
      }
  }

  private def licenser[F[_]: Monad](
      licensesStream: ConcurrentQueue[F, LicenseId],
      licensesStore: Ref[F, Map[LicenseId, Int]],
      release: F[Unit]
  ): Licenser[F] = {
    Resource.makeCase(licensesStream.poll) { (licenseId, _) =>
      licensesStore.modify { licenses =>
        val decreased = licenses.get(licenseId).fold(0)(_ - 1)

        if (decreased <= 0)
          licenses.removed(licenseId) -> release
        else
          licenses.updated(licenseId, decreased) -> Applicative[F].unit
      }.flatten
    }
  }
}
