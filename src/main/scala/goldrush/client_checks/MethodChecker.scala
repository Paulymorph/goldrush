package goldrush.client_checks

import cats.effect.Timer
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import goldrush.Types.ThrowableMonadError
import goldrush.{Client, DockerTag}

import scala.concurrent.duration.MILLISECONDS

abstract class MethodChecker[F[_]: ThrowableMonadError: Timer, Inp, Out](
    client: Client[F]
) {

  def timeNow: F[Long] = Timer[F].clock.realTime(MILLISECONDS)

  case class Data[T](begin: Long, ends: Long, result: Either[Throwable, T]) {
    lazy val duration = ends - begin
  }

  val dataName: String

  val input: Seq[Inp]

  def checkMethod(input: Inp): F[Seq[Data[Out]]]

  def timed[T](
      f: Client[F] => F[T]
  ): F[Data[T]] = {
    for {
      begin <- timeNow
      res <- f(client).attempt
      end <- timeNow
    } yield Data(begin, end, res)

  }

  private def printTimings(name: String, data: Seq[Data[_]]): Unit = {
    if (data.isEmpty) ()
    else {
      val rps =
        data.map(_.ends).map(_ / 1e3.toInt).groupBy(identity).values.map(_.size)
      val durations = data.map(_.duration)
      val timeSpentToTest = (data.map(_.ends).max - data.map(_.begin).min) / 1e3
      val srt = durations.sorted
      val n = srt.size
      val qs = Seq(10, 50, 90, 95, 99)
        .map(i => s"q$i: ${srt(i * n / 100)}")
        .mkString(", ")
      println(
        s"$name, count: $n, rps: ${rps.sum / rps.size}, rps2: ${(data.size / timeSpentToTest).toInt} min: ${srt.head}, ${qs}, max: ${srt.last}"
      )
    }
  }

  private def printData[T](
      dataInfo: String,
      data: Seq[Data[Out]]
  ): Unit = {
    val (fails, successes) = data.partition(_.result.isLeft)

    println(dataInfo)
    println(s"total results count: ${data.size}")
    printTimings("fails", fails)
    printTimings("successes", successes)
    println("\n")
  }

  def run: F[Unit] = {
    println(s"Started ${DockerTag.dockerTag}")

    CatsUtil.runSequentially(input) { i =>
      checkMethod(i)
        .map(printData(s"$dataName: $i", _))
    }
  }

}
