package goldrush.client_checks

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadError, Parallel}
import goldrush.Client
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.Observable

import scala.concurrent.duration.MILLISECONDS

case class CheckLicence[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift: FlatMap: Timer](
    client: Client[F]
)(implicit me: MonadError[F, Throwable]) {



  def printTimings: F[Unit] = {
    def runOne(threads: Int) = {
      val request = Observable
        .repeat(())
        .mapParallelUnorderedF(threads)(_ => timed(_.issueLicense()))

      TaskLift[F]
        .apply(request.toListL)
        .map { results =>
          val fails = results.collect { case (t, Left(l)) => (t, l) }
          val sucs = results.collect { case (t, Right(r)) => (t, r) }

          println(s"threads: $threads")
          printTimes("fails", fails.map(_._1))
          printTimes("successes", sucs.map(_._1))
          println("\n")
        }
    }

    println("exploring started")
    val threads = Seq(1, 2, 3, 4, 5, 8)

    threads.foldLeft(Applicative[F].pure(())) { case (acc, cur) =>
      acc.flatMap(_ => runOne(cur))
    }

  }

}
