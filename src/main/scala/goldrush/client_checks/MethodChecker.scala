package goldrush.client_checks

import cats.effect.Timer
import goldrush.Client

import scala.concurrent.duration.MILLISECONDS

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.instances.seq._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadError, Parallel, Traverse}
import goldrush.{Area, Client, ExploreResponse}
import monix.eval.{TaskLift, TaskLike}

import scala.concurrent.duration.MILLISECONDS

trait MethodChecker[F[_]: Timer: FlatMap: Applicative] {

  def timeNow: F[Long] = Timer[F].clock.realTime(MILLISECONDS)

  def timed[T](f: Client[F] => F[T]): F[(Long, Either[Throwable, T])] = {
    for {
      begin <- timeNow
      res <- f(client).attempt
      end <- timeNow
    } yield (end - begin, res)

  }

  def printTimes(name: String, times: Seq[Long]): Unit = {
    if (times.isEmpty) ()
    else {
      val srt = times.sorted
      val n = srt.size
      val qs = Seq(10, 50, 90, 95, 99)
        .map(i => s"q$i: ${srt(i * n / 100)}")
        .mkString(", ")
      println(s"$name, count: $n. min: ${srt.head}, ${qs}, max: ${srt.last}")
    }
  }

}
