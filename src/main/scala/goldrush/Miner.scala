package goldrush

import java.util.concurrent.ConcurrentHashMap

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.{Applicative, Parallel}
import goldrush.Constants._
import goldrush.Licenser.{Issuer, LicenseId}
import goldrush.Miner._
import monix.catnap.ConcurrentQueue
import monix.eval.{TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}

import scala.jdk.CollectionConverters._

class Miner[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    goldStore: GoldStore[F],
    licenser: Observable[LicenseId],
    client: Client[F]
) {
  def mine: F[Unit] = {
    val maxDuration = 60 * 1e9.toLong

    Observable(5, 5, 15, 30, 60)
      .mapEvalF { areaSize =>
        val startTime = System.nanoTime()
        exploratorBatched(areaSize)(client.explore)(Area(0, 0, 1000, 1000), 490_000)
          .takeWhile(_ => (System.nanoTime() - startTime) < maxDuration)
          .map { _ => Counters.foundCellsCount.incrementAndGet() }
          .countL
          .map { size =>
            println(
              s"areaSize: $areaSize, countL: $size, timeSpent: ${(System.nanoTime() - startTime)}"
            )
            Counters.print()
            println(Miner.toStringTreasureForArea())

            Miner.treasuresForBatchArea.clear()
            Counters.clear()
          }
      }
      .completedF
      .flatMap { _ =>
        Observable(5, 15, 30, 60).mapEvalF { areaSize =>
          val startTime = System.nanoTime()
          println("with priority")
          exploratorBatchedPriority(areaSize)(client.explore)(Area(0, 0, 1000, 1000), 490_000)
            .takeWhile(_ => (System.nanoTime() - startTime) < maxDuration)
            .map { _ => Counters.foundCellsCount.incrementAndGet() }
            .countL
            .map { size =>
              println(
                s"areaSize: $areaSize, countL: $size, timeSpent: ${(System.nanoTime() - startTime)}"
              )
              Counters.print()
              println(Miner.toStringTreasureForArea())

              Miner.treasuresForBatchArea.clear()
              Counters.clear()
            }
        }.completedF
      }
  }
}

object Miner {
  type X = Int
  type Y = Int
  type Amount = Int
  type Explorator = Observable[(X, Y, Amount)]

  val treasuresForBatchArea = new ConcurrentHashMap[Int, Int](128, 0.75f, 8)

  def toStringTreasureForArea() = {
    Miner.treasuresForBatchArea
      .entrySet()
      .asScala
      .toSeq
      .sortBy(_.getKey)
      .map { entry => s"(${entry.getKey}, ${entry.getValue})" }
      .mkString(", ")
  }

  def apply[F[_]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
      client: Client[F]
  ): Resource[F, Miner[F]] = {
    for {
      goldStore <- Resource.liftF(GoldStoreImpl[F](goldStoreSize))
      licenser = Licenser.applyObserve(licenceParallelism, Issuer.free(client))
      miner = new Miner[F](goldStore, licenser, client)
    } yield miner
  }

  def exploratorBinary[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    val Area(x, y, sizeX, sizeY) = area
    if (amount == 0 || sizeX * sizeY < 1) Observable.empty
    else if (sizeX * sizeY == 1) {
      Observable((x, y, amount))
    } else {
      val (left, right) =
        if (sizeX > sizeY) {
          val step = sizeX / 2
          val left = Area(x, y, step, sizeY)
          val right = Area(x + step, y, sizeX - step, sizeY)
          (left, right)
        } else {
          val step = sizeY / 2
          val left = Area(x, y, sizeX, step)
          val right = Area(x, y + step, sizeX, sizeY - step)
          (left, right)
        }

      val exploreadAreas = Observable
        .from(explore(left))
        .flatMapIterable { explored =>
          Seq(explored, ExploreResponse(right, amount - explored.amount))
        }

      exploreadAreas
        .filter(_.amount > 0)
        .flatMap { explored =>
          exploratorBinary(explore)(explored.area, explored.amount)
            .asyncBoundary(OverflowStrategy.Unbounded)
        }
    }
  }

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    coords
      .mapParallelUnorderedF(exploreParallelism) { case (x, y) =>
        explore(
          Area(
            x,
            y,
            Math.min(maxStep, posX + sizeX - x),
            Math.min(maxStep, posY + sizeY - y)
          )
        )
      }
      .asyncBoundary(OverflowStrategy.Unbounded)
      .map { x =>
        treasuresForBatchArea.compute(
          x.amount,
          { case (k, v) =>
            if (Option(k).isEmpty || Option(v).isEmpty) 1
            else v + 1
          }
        )
        x
      }
      .filter(_.amount > 0)
      .flatMap { response =>
        exploratorBinary(explore)(response.area, response.amount)
      }

  }

  def exploratorBatchedPriority[F[_]: TaskLike: Applicative](maxStep: Int)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    Observable
      .fromIteratorF(
        coords
          .mapParallelUnorderedF(exploreParallelism) { case (x, y) =>
            explore(
              Area(
                x,
                y,
                Math.min(maxStep, posX + sizeX - x),
                Math.min(maxStep, posY + sizeY - y)
              )
            )
          }
          .asyncBoundary(OverflowStrategy.Unbounded)
          .map { x =>
            treasuresForBatchArea.compute(
              x.amount,
              { case (k, v) =>
                if (Option(k).isEmpty || Option(v).isEmpty) 1
                else v + 1
              }
            )
            x
          }
          .toListL
          .map(_.sortBy(x => -x.amount).iterator)
      )
      .filter(_.amount > 0)
      .flatMap { response =>
        exploratorBinary(explore)(response.area, response.amount)
      }

  }

  def explorator[F[_]: TaskLike: Applicative](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    exploratorBatched(maxExploreArea)(explore)(area, amount)
  }

  def exploratorResource[F[_]: TaskLike: Applicative: Concurrent: TaskLift: ContextShift](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Resource[F, F[(X, Y, Amount)]] = {
    for {
      queue <- Resource.liftF(ConcurrentQueue.unbounded[F, (X, Y, Amount)]())
      _ <- Concurrent[F].background {
        exploratorBatched(maxExploreArea)(explore)(area, amount)
          .mapEvalF { x =>
            queue.offer(x)
          }
          .completedF[F]
      }
    } yield queue.poll
  }

}
