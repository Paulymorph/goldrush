package goldrush

import java.util.concurrent.atomic.AtomicBoolean

import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import goldrush.Miner._
import monix.eval.{Task, TaskLift, TaskLike}
import monix.reactive.{Observable, OverflowStrategy}
import Miner._
import monix.execution.CancelablePromise

import scala.concurrent.{Future, Promise}
import scala.util.Success
case class Miner[F[
    _
]: Sync: Parallel: Applicative: Concurrent: ContextShift: TaskLike: TaskLift](
    client: Client[F]
) {
  def mine: F[Int] = {
    val digParallelism = 36

    val promise = new AtomicBoolean(false)

    val concurrentStart = Concurrent[F].start()

    val licensesR: Observable[Int] = {
      implicit val licenseOS: OverflowStrategy.BackPressure = OverflowStrategy.BackPressure(10)
      Observable
        .repeat(())
        .dropWhile(_ => promise.get() == false)
        .mapParallelUnorderedF(digParallelism)(_ => client.issueLicense())
        .flatMapIterable { license =>
          Seq.fill(license.digAllowed - license.digUsed)(license.id)
        }
    }

    val exploreResource = {
      val exploreOS: OverflowStrategy.BackPressure = OverflowStrategy.BackPressure(2048)
      for {
        queue <- Resource.liftF(MVar.empty[F, (X, Y, Amount)])
        _ <- Concurrent[F].start {
          explorator(client.explore)(Area(0, 0, 3500, 3500), 490_000)
            .map { x =>
              promise.set(true)
              x
            }
            .asyncBoundary(exploreOS)
            .mapEvalF(queue.put)
            .completedF[F]
        }
      } yield queue.take
    }

    val digger = {
      Observable
        .fromResource(exploreResource)
        .flatMap { nextAreaF =>
          licensesR
            .mapParallelOrderedF(digParallelism) { license =>
              def dig(
                  level: Int,
                  foundTreasures: Seq[String],
                  x: X,
                  y: Y,
                  amount: Amount
              ): F[Seq[String]] = {
                for {
                  newTreasures <- client.dig(license, x, y, level)
                  nextTreasures = foundTreasures ++ newTreasures
                  goDeeper = level < 10 && nextTreasures.size < amount
                  result <-
                    if (goDeeper) dig(level + 1, nextTreasures, x, y, amount)
                    else Applicative[F].pure(nextTreasures)
                } yield result
              }

              for {
                nextArea <- nextAreaF
                (x, y, amount) = nextArea
                res <- dig(1, Seq.empty, x, y, amount)
              } yield res
            }
            .flatMap(Observable.fromIterable)
        }
    }

    val coins = digger
      .mapParallelUnorderedF(digParallelism) { treasure =>
        client.cash(treasure)
      }
      .flatMap(Observable.fromIterable)

    TaskLift[F].apply(coins.sumL)
  }
}

object Miner {
  type X = Int
  type Y = Int
  type Amount = Int
  type Explorator = Observable[(X, Y, Amount)]

  def exploratorBinary[F[_]: TaskLike](
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    val Area(x, y, sizeX, sizeY) = area
    if (amount == 0 || sizeX * sizeY < 1) Observable.empty
    else if (sizeX * sizeY == 1) Observable((x, y, amount))
    else {
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
        }
    }
  }

  def exploratorBatched[F[_]: TaskLike: Applicative](maxStep: Int = 5)(
      explore: Area => F[ExploreResponse]
  )(area: Area, amount: Int): Explorator = {
    import area._
    val xs = Observable.range(posX, posX + sizeX, maxStep).map(_.toInt)
    val ys = Observable.range(posY, posY + sizeY, maxStep).map(_.toInt)
    val coords = xs.flatMap(x => ys.flatMap(y => Observable.pure(x, y)))

    Observable
      .fromIteratorF(
        coords
          .mapParallelUnorderedF(32) { case (x, y) =>
            explore(
              Area(
                x,
                y,
                Math.min(maxStep, posX + sizeX - x),
                Math.min(maxStep, posY + sizeY - y)
              )
            )
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
    exploratorBatched(15)(explore)(area, amount)
  }

}
