package goldrush

import cats.effect.{Concurrent, ContextShift, Sync, Timer}
import cats.instances.seq._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, MonadError, Parallel, Traverse}
import monix.eval.{TaskLift, TaskLike}

import scala.concurrent.duration.MILLISECONDS

case class CheckExploreTimings[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift: FlatMap: Timer](
    client: Client[F]
)(implicit me: MonadError[F, Throwable]) {

  def timeNow: F[Long] = Timer[F].clock.realTime(MILLISECONDS)

  def genAreas(size: Int): Seq[Area] = for {
    i <- Range(0, 10)
    j <- Range(0, 10)
  } yield Area(i * size, j * size, size, size)

  def timedExplore(
      area: Area
  ): F[(Long, Either[Throwable, ExploreResponse])] = {
    for {
      begin <- timeNow
      res <- client.explore(area).attempt
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
      println(s"${name}, count: $n. min: ${srt.head}, ${qs}, max: ${srt.last}")
    }
  }

  def exploreTimings: F[Unit] = {
    def runOne(areaSize: Int): F[Unit] = {
      val areas = genAreas(areaSize)

      Traverse[Seq]
        .traverse(areas) { ar =>
          timedExplore(ar)
        }
        .map { results =>
          val fails = results.collect { case (t, Left(l)) => (t, l) }
          val sucs = results.collect { case (t, Right(r)) => (t, r) }

          println(s"areaSize: $areaSize")
          printTimes("fails", fails.map(_._1))
          printTimes("successes", sucs.map(_._1))
          println("\n")
        }
    }

    println("exploring started")
    val sizes = Seq.range(0, 40) ++ Seq.range(50, 150, 10)

    sizes.foldLeft(Applicative[F].pure(())) { case (acc, cur) =>
      acc.flatMap(_ => runOne(cur))
    }

  }

}
