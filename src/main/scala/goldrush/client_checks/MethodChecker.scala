package goldrush.client_checks

import cats.effect.Timer
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import goldrush.Client
import goldrush.Types.ThrowableMonadError

import scala.concurrent.duration.MILLISECONDS

abstract class MethodChecker[F[_]: ThrowableMonadError: Timer, Inp, Out](
    client: Client[F]
) {

  def timeNow: F[Long] = Timer[F].clock.realTime(MILLISECONDS)

  val dataName: String

  val input: Seq[Inp]

  def checkMethod(input: Inp): F[Seq[(Long, Either[Throwable, Out])]]

  def timed[T](
      f: Client[F] => F[T]
  ): F[(Long, Either[Throwable, T])] = {
    for {
      begin <- timeNow
      res <- f(client).attempt
      end <- timeNow
    } yield (end - begin, res)

  }

  private def printTimings(name: String, times: Seq[Long]): Unit = {
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

  private def printData[T](
      dataInfo: String,
      data: Seq[(Long, Either[Throwable, T])]
  ): Unit = {
    val fails = data.collect { case (t, Left(l)) => (t, l) }
    val successes = data.collect { case (t, Right(r)) => (t, r) }

    println(dataInfo)
    printTimings("fails", fails.map(_._1))
    printTimings("successes", successes.map(_._1))
    println("\n")
  }

  def run: F[Unit] = {
    println("Started")

    CatsUtil.runSequentially(input) { i =>
      checkMethod(i)
        .map(printData(s"$dataName: $i", _))
    }
  }

}
