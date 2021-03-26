package goldrush.client_checks

import cats.effect.Timer
import cats.kernel.Monoid
import cats.syntax.flatMap._
import cats.{Applicative, ApplicativeError, FlatMap}
import goldrush.Constants.ThrowableApplicativeError

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.Random

object CatsUtil {

  def runSequentially[F[_]: FlatMap: Applicative, T, Out: Monoid](
      seq: Seq[T]
  )(f: T => F[Out]): F[Out] = {
    seq.foldLeft(Applicative[F].pure(Monoid[Out].empty)) { case (acc, cur) =>
      acc.flatMap(_ => f(cur))
    }
  }

  def sleepAndReturn[T, F[_]: Timer: ThrowableApplicativeError: FlatMap](
      sleepMillis: Int,
      res: Either[Throwable, T]
  ): F[T] = {
    Timer[F]
      .sleep(FiniteDuration(Random.nextInt(sleepMillis), MILLISECONDS))
      .flatMap(_ => ApplicativeError[F, Throwable].fromEither(res))
  }

}
